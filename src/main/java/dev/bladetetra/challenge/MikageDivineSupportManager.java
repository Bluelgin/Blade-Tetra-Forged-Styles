package dev.bladetetra.challenge;

import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.network.ModularTechniqueVfxPacket;
import dev.bladetetra.network.DivineSupportStatePacket;
import dev.bladetetra.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One controller per Ritual. All targets, protection and cooldowns are scoped to its Session. */
final class MikageDivineSupportManager {
    private static final AtomicInteger VISUAL_IDS = new AtomicInteger();
    final DivineDomainManager.Session session;
    final ServerLevel level;
    private final int visualId = VISUAL_IDS.incrementAndGet();
    private UUID companion, marked;
    private Vec3 array, wall;
    private long arrayEnd, wallEnd, markEnd, arrayReady, wallReady, markReady, attackReady;
    private long techniqueReady;
    private int techniqueCycle;
    private int arrayIntegrity;
    private boolean closed;
    private int missingCompanionTicks;
    private final DivineGuardState guardState = new DivineGuardState();
    private final Map<UUID, Long> meleeWindows = new HashMap<>();
    private final Map<UUID, Long> invaderHits = new HashMap<>();
    private final Map<UUID, Boolean> wallSides = new HashMap<>();
    private final List<SupportStrike> pendingStrikes = new ArrayList<>();
    private final DivineDomainFinisher finisher;

    MikageDivineSupportManager(DivineDomainManager.Session session, ServerLevel level) {
        this.session=session; this.level=level; finisher=new DivineDomainFinisher(this);
        arrayReady=level.getGameTime()+60;
        techniqueReady=level.getGameTime()+30;
        if (session.tier.hasCompanion()) {
            spawnCompanion();
            say("我与你同去。");
        } else say("界门之外，三道祓刀回应了你的刀。御影正在场外护持。");
    }

    private void spawnCompanion() {
            var ally=ModEntities.MIKAGE_DIVINE_COMPANION.get().create(level);
            if (ally != null) {
                BlockPos entry=DivineDomainArenaData.entry(session.originX,session.originZ);
                ally.bind(session.id); ally.moveTo(entry.getX()+.5,entry.getY(),entry.getZ()+.5,180,0);
                ally.setCustomName(Component.literal("御影"));
                if (level.addFreshEntity(ally)) companion=ally.getUUID();
                effect("entrance",ally.position(),-1,40,0);
            }
    }

    UUID companionId() { return companion; }
    void setFinalTarget(Mob boss) { finisher.bind(boss); }
    List<ServerPlayer> players() {
        return session.players.stream().map(id->level.getServer().getPlayerList().getPlayer(id))
                .filter(p->p!=null && p.level()==level && p.isAlive()).toList();
    }
    List<Mob> enemies() {
        return session.enemies.stream().map(level::getEntity).filter(e->e instanceof Mob && e.isAlive())
                .map(e->(Mob)e).toList();
    }
    private MikageDivineCompanionEntity ally() {
        return companion != null && level.getEntity(companion) instanceof MikageDivineCompanionEntity a && a.isAlive() ? a : null;
    }
    boolean available() { var a=ally(); return !session.tier.hasCompanion() || a!=null && !a.recovering(); }

    void tick() {
        long now=level.getGameTime();
        List<ServerPlayer> participants=players();
        if(closed || participants.isEmpty()) return;
        if(session.tier.hasCompanion() && ally()==null) {
            // A replaced/unloaded host never refunds the shared rescue or skill cooldowns.
            if(++missingCompanionTicks>=20) { spawnCompanion(); missingCompanionTicks=0; }
        } else missingCompanionTicks=0;
        List<Mob> mobs=enemies();
        tickPendingStrikes(now);
        meleeWindows.entrySet().removeIf(e->e.getValue()<=now || !session.enemies.contains(e.getKey()));
        invaderHits.keySet().retainAll(session.enemies);
        wallSides.keySet().retainAll(session.enemies);
        ServerPlayer player=participants.stream().min(Comparator.comparingDouble(p->p.getHealth()/p.getMaxHealth())).orElseThrow();
        finisher.tick(mobs,now);
        var a=ally();
        if(a!=null && !a.recovering()) moveAndAttack(a,player,mobs,now);
        if(array!=null && now>=arrayEnd) endArray();
        if(wall!=null && now>=wallEnd) { effect("wall_end",wall,-1,16,0); wall=null; wallSides.clear(); }
        if(marked!=null && (now>=markEnd || !session.enemies.contains(marked))) marked=null;
        for(Mob mob:mobs) confine(mob);
        if(now%5!=0) return;
        if(available() && !finisher.binding(now)) {
            if(now>=techniqueReady && !mobs.isEmpty()) castActiveTechnique(a,player,mobs,now);
            long nearby=mobs.stream().filter(m->m.distanceToSqr(player)<81).count();
            if(now>=arrayReady && nearby>=3) {
                array=safePoint(player.position()); arrayEnd=now+160; arrayReady=now+400; arrayIntegrity=6;
                effect("array",array,-1,160,0); pose(1);
            }
            if(session.tier.hasCompanion() && now>=wallReady && (mobs.size()>12 || nearby>=8)) {
                wall=new Vec3(session.originX+.5,DivineDomainArenaData.FLOOR_Y+1,session.originZ+.5);
                wallEnd=now+100; wallReady=now+700; wallSides.clear();
                for(Mob mob:mobs) wallSides.put(mob.getUUID(),mob.getX()>=wall.x);
                effect("wall",wall,-1,100,0); pose(2);
            }
            if(now>=markReady) {
                Mob target=mobs.stream().filter(m->DivineDomainEnemyRole.of(m)!=DivineDomainEnemyRole.PURSUER)
                        .filter(m->DivineDomainEnemyRole.of(m)!=DivineDomainEnemyRole.ORDINARY)
                        .max(Comparator.comparingInt(m->priority(DivineDomainEnemyRole.of(m)))).orElse(null);
                if(target!=null) {
                    marked=target.getUUID(); markEnd=now+120; markReady=now+200;
                    effect("mark",target.position(),target.getId(),120,0); pose(1);
                }
            }
        }
        for(Mob mob:mobs) {
            // Session enemies never chase spectators or players in another Ritual.
            if(bound(mob)) { mob.setTarget(null); mob.getNavigation().stop(); continue; }
            if(mob.getTarget()==null || (!session.players.contains(mob.getTarget().getUUID())
                    && !mob.getTarget().getUUID().equals(companion))
                    || !mob.getTarget().isAlive() || mob.getTarget().level()!=level) mob.setTarget(player);
            if(inArray(mob)) mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,1,false,false));
            if(DivineDomainEnemyRole.of(mob)==DivineDomainEnemyRole.INVADER && array!=null) {
                mob.setTarget(null); mob.getNavigation().moveTo(array.x,array.y,array.z,1.15);
                if(mob.position().distanceToSqr(array)<4 && now>=invaderHits.getOrDefault(mob.getUUID(),0L)) {
                    invaderHits.put(mob.getUUID(),now+20); arrayIntegrity--;
                    effect("anchor_hit",array,-1,12,0);
                    if(arrayIntegrity<=0) { say("阵眼被破开了。下次护住它。"); endArray(); }
                }
            }
        }
        if(now%20==0) status(true);
    }

    private void confine(Mob mob) {
        if(mob.getY()<DivineDomainArenaData.FLOOR_Y-4 || !DivineDomainArenaData.contains(mob.getX(),mob.getZ(),session.originX,session.originZ)) {
            Vec3 safe=safePoint(new Vec3(session.originX+6,64,session.originZ+6));
            mob.teleportTo(safe.x,safe.y,safe.z);
        }
        if(wall!=null) {
            boolean side=wallSides.computeIfAbsent(mob.getUUID(),id->mob.getX()>=wall.x);
            if((mob.getX()>=wall.x)!=side || Math.abs(mob.getX()-wall.x)<.4) {
                mob.teleportTo(wall.x+(side?.65:-.65),mob.getY(),mob.getZ());
                mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);
            }
        }
    }

    private static int priority(DivineDomainEnemyRole role) {
        return switch(role) { case FINAL -> 5; case GUARDIAN -> 4; case INVADER -> 3; case TAINTED -> 2; default -> 0; };
    }
    private void moveAndAttack(MikageDivineCompanionEntity a, ServerPlayer player,List<Mob> mobs,long now) {
        if(finisher.binding(now)) { a.getNavigation().stop(); a.pose(3); return; }
        if(now%10==0) {
            Vec3 destination=safePoint(player.position().add(2,0,2));
            double distance=a.distanceTo(player);
            if(distance>18 || a.getY()<DivineDomainArenaData.FLOOR_Y-2) {
                effect("dash",a.position(),-1,10,0); a.teleportTo(destination.x,destination.y,destination.z);
                effect("dash",destination,-1,10,0);
            } else if(distance>4) a.getNavigation().moveTo(destination.x,destination.y,destination.z,distance>8?1.9:1);
            else a.getNavigation().stop();
            a.getLookControl().setLookAt(player,30,30);
        }
        if(now%20==0) a.pose(0);
        if(now<attackReady) return;
        Mob target=mobs.stream().filter(m->m.distanceToSqr(a)<9 && DivineDomainEnemyRole.of(m)!=DivineDomainEnemyRole.PURSUER)
                .max(Comparator.comparingInt(m->priority(DivineDomainEnemyRole.of(m)))).orElse(null);
        if(target!=null && a.hasLineOfSight(target)) {
            attackReady=now+35; a.pose(2);
            float damage=DivineSupportRules.companionDamage(target.getHealth());
            if(damage>0) target.hurt(level.damageSources().mobAttack(a),damage);
            Vec3 direction=target.position().subtract(a.position()); target.knockback(.65,-direction.x,-direction.z);
        }
    }

    private void castActiveTechnique(MikageDivineCompanionEntity ally,
            ServerPlayer player, List<Mob> mobs, long now) {
        Vec3 origin=ally==null?player.position():ally.position();
        List<Mob> targets=mobs.stream()
                .filter(m->m.isAlive() && m.position().distanceToSqr(origin)<18*18)
                .sorted(Comparator.comparingInt((Mob m)->priority(DivineDomainEnemyRole.of(m))).reversed()
                        .thenComparingDouble(m->m.distanceToSqr(origin)))
                .toList();
        if(targets.isEmpty()) { techniqueReady=now+20; return; }

        techniqueCycle++;
        if(ally==null || targets.size()>=3 && techniqueCycle%3==0) {
            castPurificationVolley(targets,now);
            techniqueReady=now+(ally==null?100:80);
            return;
        }

        Mob target=targets.get(0);
        if(ally.distanceToSqr(target)>20.25D) {
            Vec3 from=ally.position();
            Vec3 approach=from.subtract(target.position()).multiply(1,0,1);
            if(approach.lengthSqr()<.001D) approach=new Vec3(0,0,1);
            Vec3 destination=safePoint(target.position().add(approach.normalize().scale(2.0D)));
            effect("dash",from,-1,10,yaw(from,target.position()));
            ally.teleportTo(destination.x,destination.y,destination.z);
            effect("dash",destination,-1,10,yaw(destination,target.position()));
            ally.pose(2);
            queueStrike(target,now+4,2.5F);
        } else {
            ally.pose(2);
            queueStrike(target,now,1.5F);
            queueStrike(target,now+5,1.5F);
            queueStrike(target,now+10,2.0F);
        }
        attackReady=Math.max(attackReady,now+18);
        techniqueReady=now+70;
    }

    private void castPurificationVolley(List<Mob> targets,long now) {
        int count=Math.min(3,targets.size());
        for(int i=0;i<count;i++) {
            Mob target=targets.get(i);
            effect("volley",target.position(),target.getId(),22,i*120);
            queueStrike(target,now+8+i*3,1.5F);
        }
        pose(1);
        attackReady=Math.max(attackReady,now+18);
    }

    private void queueStrike(Mob target,long at,float damage) {
        pendingStrikes.add(new SupportStrike(target.getUUID(),at,damage));
    }

    private void tickPendingStrikes(long now) {
        Iterator<SupportStrike> iterator=pendingStrikes.iterator();
        while(iterator.hasNext()) {
            SupportStrike strike=iterator.next();
            if(now<strike.at) continue;
            iterator.remove();
            Entity found=level.getEntity(strike.target);
            if(!(found instanceof Mob target) || !target.isAlive()
                    || !session.enemies.contains(target.getUUID())) continue;
            float damage=Math.min(strike.damage,DivineSupportRules.companionDamage(target.getHealth()));
            if(damage<=0) continue;
            var ally=ally();
            target.hurt(ally==null?level.damageSources().magic():level.damageSources().mobAttack(ally),damage);
            effect("strike",target.position(),target.getId(),12,0);
        }
    }

    private static float yaw(Vec3 from,Vec3 to) {
        Vec3 direction=to.subtract(from);
        return (float)Math.toDegrees(Math.atan2(-direction.x,direction.z));
    }
    Vec3 safePoint(Vec3 preferred) {
        for(int i=0;i<16;i++) {
            BlockPos pos=i==0?BlockPos.containing(preferred.x,DivineDomainArenaData.FLOOR_Y+1,preferred.z)
                    :DivineDomainArenaData.spawnPoint(session.originX,session.originZ,i,8);
            if(!DivineDomainArenaData.isGuaranteedCombatFloor(pos.getX()-session.originX,pos.getZ()-session.originZ)) continue;
            if(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                    && !level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())
                return Vec3.atBottomCenterOf(pos);
        }
        return Vec3.atBottomCenterOf(DivineDomainArenaData.spawnPoint(session.originX,session.originZ,0,8));
    }
    boolean inArray(Entity entity) {
        return array!=null && level.getGameTime()<arrayEnd && Math.abs(entity.getY()-array.y)<5
                && DivineSupportRules.insideArray(entity.getX()-array.x,entity.getZ()-array.z);
    }
    boolean marked(Entity entity) { return entity.getUUID().equals(marked) && level.getGameTime()<markEnd; }
    boolean bound(Entity entity) { return finisher.isBound(entity,level.getGameTime()); }
    boolean protectedPlayer(ServerPlayer player) { return guardState.protects(player.getUUID(),level.getGameTime()); }
    boolean attackingArray(Entity entity) { return array!=null && DivineDomainEnemyRole.of(entity)==DivineDomainEnemyRole.INVADER; }
    boolean wallSeparates(Entity enemy,Entity target) {
        return wall!=null && (target.getX()>=wall.x)!=wallSides.getOrDefault(enemy.getUUID(),enemy.getX()>=wall.x);
    }
    boolean allowMelee(Entity enemy) {
        long now=level.getGameTime();
        if(now<meleeWindows.getOrDefault(enemy.getUUID(),0L)) return false;
        meleeWindows.put(enemy.getUUID(),now+30); return true;
    }
    boolean guard(ServerPlayer player) {
        if(closed || !session.tier.hasCompanion() || !available() || !session.players.contains(player.getUUID())
                || !guardState.tryUse(player.getUUID(),level.getGameTime())) return false;
        player.setHealth(Math.min(player.getMaxHealth(),4));
        player.clearFire();
        for(var effect:List.copyOf(player.getActiveEffects())) if(!effect.getEffect().isBeneficial()) player.removeEffect(effect.getEffect());
        var a=ally(); Vec3 at=safePoint(player.position());
        if(a!=null) { a.teleportTo(at.x,at.y,at.z); a.pose(1); }
        for(Mob mob:enemies()) if(mob.distanceToSqr(player)<36) {
            Vec3 delta=mob.position().subtract(player.position()); mob.knockback(1.4,-delta.x,-delta.z);
        }
        effect("guard",player.position(),player.getId(),60,0); say("站稳。这一次，我替你挡下了。"); status(true); return true;
    }
    private void endArray() { if(array!=null) effect("array_end",array,-1,16,0); array=null; }
    void pose(int value) { var a=ally(); if(a!=null) a.pose(value); }
    void say(String text) { session.broadcast(level.getServer(),Component.literal("御影："+text)); }
    void effect(String kind,Vec3 position,int target,int duration,float yaw) {
        var sound=switch(kind) {
            case "array", "binding", "entrance" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case "wall", "dash" -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case "guard" -> SoundEvents.TOTEM_USE;
            default -> null;
        };
        if(sound!=null) level.playSound(null,position.x,position.y,position.z,sound,SoundSource.PLAYERS,.65F,1.15F);
        var packet=new ModularTechniqueVfxPacket(new ResourceLocation("blade_tetra","divine/"+kind),
                session.originX,DivineDomainArenaData.FLOOR_Y+1,session.originZ,
                position.x,position.y,position.z,yaw,1,ally()==null?-1:ally().getId(),target,duration,visualId);
        // World VFX are visible to nearby observers, but the HUD is participant-only.
        for(ServerPlayer observer:level.players()) if(observer.distanceToSqr(position)<128*128)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->observer),packet);
    }
    private void status(boolean active) {
        long now=level.getGameTime();
        var packet=new DivineSupportStatePacket(session.id,active,session.tier.ordinal(),
                (int)Math.max(0,arrayReady-now),(int)Math.max(0,wallReady-now),!guardState.used(),available());
        for(ServerPlayer player:players()) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);
    }
    void close() {
        if(closed) return;
        status(false); effect("end",new Vec3(session.originX,64,session.originZ),-1,1,0);
        var a=ally(); if(a!=null) a.discard();
        guardState.revokeProtection(); meleeWindows.clear(); invaderHits.clear(); wallSides.clear();
        pendingStrikes.clear(); closed=true;
    }
    private record SupportStrike(UUID target,long at,float damage) {}
}
