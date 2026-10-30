package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.network.*;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** One bounded scheduler for the shared shard pool's two mutually exclusive attack modes. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraShardAttackService {
    private static final String MARK="blade_tetra_sakura_concerto";
    private static final int MAX_PROJECTILES=256, PER_PLAYER=4, PROJECTILE_TTL=30;
    private static final class Owner {
        final ItemStack blade; final int slot; final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        long nextAt, blockedUntil; int cursor; boolean right;
        Pending pending; LivingEntity lastTarget; long targetAt;
        Owner(ServerPlayer p,ItemStack b) {blade=b;slot=p.getInventory().selected;dimension=p.level().dimension();}
        boolean matches(ServerPlayer p) {return p.isAlive()&&p.getMainHandItem()==blade&&slot==p.getInventory().selected
                &&dimension.equals(p.level().dimension())&&SakuraEndlessFlow.eligible(blade);}
    }
    private record Pending(LivingEntity target,float damage,long fireAt,boolean right) {}
    private record Flight(ServerPlayer owner,Owner binding,LivingEntity target,float budget,long at) {}
    private static final class DamageBudget {
        final ServerPlayer owner; final LivingEntity target; float remaining;
        DamageBudget(ServerPlayer owner,LivingEntity target,float amount) {this.owner=owner;this.target=target;remaining=amount;}
        boolean matches(net.minecraft.world.damagesource.DamageSource source,LivingEntity victim) {
            return source.getDirectEntity()==owner&&target==victim&&SoulLegacyDamageGuard.isSecondary(source);
        }
    }
    private static final ThreadLocal<DamageBudget> SA_BUDGET=new ThreadLocal<>();
    private static final Map<UUID,Owner> OWNERS=new HashMap<>();
    private static final Map<EntityAbstractSummonedSword,Flight> FLIGHTS=new HashMap<>();
    public static boolean validTarget(ServerPlayer p,LivingEntity t) {
        if(p==t||!t.isAlive()||t.isRemoved()||p.level()!=t.level()||t instanceof Player
                ||p.isAlliedTo(t)||t.isAlliedTo(p)||t instanceof TamableAnimal||t instanceof Animal
                ||t instanceof AbstractVillager||t instanceof AbstractGolem||t instanceof WaterAnimal
                ||t instanceof AmbientCreature||p.distanceToSqr(t)>144||!p.hasLineOfSight(t)) return false;
        return t instanceof Enemy||t instanceof Mob m&&(m.getTarget()==p||m.getLastHurtByMob()==p);
    }
    public static void targetHit(ServerPlayer p,ItemStack blade,LivingEntity target) {
        if(!validTarget(p,target)) return;
        Owner o=OWNERS.get(p.getUUID());
        if(o==null||!o.matches(p)) {o=new Owner(p,blade);OWNERS.put(p.getUUID(),o);}
        o.lastTarget=target;o.targetAt=p.level().getGameTime();
    }
    /** Invoked only after a verified native attack's first positive LivingDamage event. */
    public static void concerto(ServerPlayer p,ItemStack blade,LivingEntity target,float damage) {
        if(!SakuraEndlessFlow.eligible(blade)||SakuraInfiniteDanceSA.active(p,blade)||!validTarget(p,target)) return;
        Owner o=OWNERS.get(p.getUUID());
        if(o==null||!o.matches(p)) {o=new Owner(p,blade);OWNERS.put(p.getUUID(),o);}
        long now=p.level().getGameTime();
        if(now<o.nextAt||now<o.blockedUntil||o.pending!=null||FLIGHTS.size()>=MAX_PROJECTILES) return;
        float budget=SakuraShardAttackRules.damage(damage,GameplayConfig.SAKURA_CONCERTO_FRACTION.get(),GameplayConfig.SAKURA_CONCERTO_CAP.get());
        if(budget<=0) return;
        Vec3 launch=overhead(p,target,o.right);if(launch==null) return;
        int logical=o.cursor++%SakuraShardAttackRules.LOGICAL_SHARDS;
        boolean right=o.right;o.right=!right;
        o.nextAt=now+GameplayConfig.SAKURA_CONCERTO_INTERVAL.get();
        o.pending=new Pending(target,budget,now+SakuraShardAttackRules.FIRE_TICK,right);
        cue(p,blade,1,SakuraShardAttackRules.CONCERTO_VARIANT+logical*2+(right?1:0),
                launch,target.getId(),now);
    }
    public static void dancePulse(ServerPlayer p,ItemStack blade,int stage,int elapsed) {
        var state=blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if(state==null||!SakuraInfiniteDanceSA.active(p,blade)) return;
        Entity entity=state.getTargetEntity(p.level());
        LivingEntity selected=entity instanceof LivingEntity l&&validTarget(p,l)?l:null;
        Owner owner=OWNERS.get(p.getUUID());
        if(selected==null&&owner!=null&&owner.matches(p)&&p.level().getGameTime()-owner.targetAt<=40
                &&owner.lastTarget!=null&&validTarget(p,owner.lastTarget)) selected=owner.lastTarget;
        final LivingEntity target=selected;
        if(elapsed==0) cue(p,blade,stage,0,target==null?p.getEyePosition().add(p.getLookAngle().scale(3.5)):
                target.getBoundingBox().getCenter(),target==null?-1:target.getId(),p.level().getGameTime());
        if(target==null||!SakuraShardAttackRules.pulse(stage,elapsed)) return;
        float damage=(float)(GameplayConfig.SAKURA_SA_STAGE_DAMAGE.get()/SakuraShardAttackRules.pulseCount(stage));
        // Fixed total per stage, never multiplied by the number of native slash entities or targets.
        DamageBudget previous=SA_BUDGET.get();SA_BUDGET.set(new DamageBudget(p,target,damage));
        try {InheritedCombatDamage.apply(()->SoulLegacyDamageGuard.apply(()->target.hurt(p.damageSources().playerAttack(p),damage)));}
        finally {if(previous==null) SA_BUDGET.remove();else SA_BUDGET.set(previous);}
    }
    public static void cue(ServerPlayer p,ItemStack blade,int stage,int variant,Vec3 end,int target,long at) {
        var tag=blade.getTag();if(tag==null||tag.getString("id").isBlank()) return;
        Vec3 origin=p.getEyePosition();
        var packet=new AwakenedBladeVfxPacket(3,AwakenedBladeVfxPacket.SAKURA_SWING,
                origin.x,origin.y,origin.z,end.x,end.y,end.z,variant,
                new AwakenedBladeVfxPacket.SakuraCue(p.getUUID(),tag.getString("id"),p.getInventory().selected,
                        p.level().dimension().location(),stage,SakuraCombatVisuals.nextSequence(),at,target));
        if(packet.valid()) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),packet);
    }
    public static boolean switchBlocked(ServerPlayer p) {
        Owner o = OWNERS.get(p.getUUID());
        return o != null && o.pending != null || FLIGHTS.values().stream().anyMatch(f -> f.owner == p);
    }
    public static void cancel(ServerPlayer p) {
        Owner o=OWNERS.get(p.getUUID());
        if(o!=null) {o.pending=null;o.blockedUntil=p.level().getGameTime()+SakuraShardAttackRules.FLIGHT_TICKS;}
        FLIGHTS.entrySet().removeIf(e->{if(e.getValue().owner==p) {e.getKey().discard();return true;}return false;});
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)) return;
        Owner o=OWNERS.get(p.getUUID());long now=p.level().getGameTime();
        if(o==null) return;
        if(!o.matches(p)) {cancel(p);OWNERS.remove(p.getUUID());return;}
        Pending pending=o.pending;
        if(pending==null||now<pending.fireAt) return;
        o.pending=null;
        if(!SakuraEndlessFlow.eligible(o.blade)||SakuraInfiniteDanceSA.active(p,o.blade)||!validTarget(p,pending.target)||now>pending.fireAt+1
                ||FLIGHTS.size()>=MAX_PROJECTILES||FLIGHTS.values().stream().filter(f->f.owner==p).count()>=PER_PLAYER) return;
        Vec3 origin=overhead(p,pending.target,pending.right);if(origin==null) return;
        Vec3 end=pending.target.getBoundingBox().getCenter();
        if(p.level().clip(new ClipContext(p.getEyePosition(),origin,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS
                ||p.level().clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS) return;
        var sword=new EntityAbstractSummonedSword(SlashBlade.RegistryEvents.SummonedSword,p.level());
        sword.setPos(origin);sword.setOwner(p);sword.setColor(0xffa6ce);sword.setDamage(pending.damage);
        sword.setPierce((byte)0);sword.setNoClip(false);
        sword.getPersistentData().putBoolean(MARK,true);
        SoulLegacyDamageGuard.markSecondary(sword);InheritedCombatDamage.mark(sword);
        Vec3 direction=end.subtract(origin).normalize();sword.shoot(direction.x,direction.y,direction.z,3,0);
        FLIGHTS.put(sword,new Flight(p,o,pending.target,pending.damage,now));
        if(!p.level().addFreshEntity(sword)) FLIGHTS.remove(sword);
    }
    private static Vec3 overhead(ServerPlayer p,LivingEntity target,boolean right) {
        Vec3 center=target.getBoundingBox().getCenter();
        Vec3 side=new Vec3(p.getLookAngle().z,0,-p.getLookAngle().x).normalize().scale(right?1:-1);
        for(Vec3 candidate:List.of(center.add(0,target.getBbHeight()*.5+.6,0).add(side.scale(.25)),
                center.add(side.scale(1.1)).add(0,.35,0))) {
            if(!p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(candidate))) continue;
            var box=new AABB(candidate,candidate).inflate(.12);
            if(!p.level().noCollision(box)) continue;
            if(p.level().clip(new ClipContext(p.getEyePosition(),candidate,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS
                    &&p.level().clip(new ClipContext(candidate,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS) return candidate;
        }
        return null;
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        FLIGHTS.entrySet().removeIf(entry->{
            var sword=entry.getKey();var f=entry.getValue();
            if(sword.isRemoved()||!f.binding.matches(f.owner)||!validTarget(f.owner,f.target)
                    ||f.owner.level().getGameTime()-f.at>PROJECTILE_TTL) {sword.discard();return true;}return false;
        });
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void impact(ProjectileImpactEvent e) {
        if(!(e.getProjectile() instanceof EntityAbstractSummonedSword sword)||!sword.getPersistentData().getBoolean(MARK)) return;
        Flight f=FLIGHTS.get(sword);
        if(e.getRayTraceResult() instanceof EntityHitResult hit&&(f==null||hit.getEntity()!=f.target||!validTarget(f.owner,f.target))) {
            e.setCanceled(true);sword.discard();
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent e) {
        var direct=e.getSource().getDirectEntity();
        if(!(direct instanceof EntityAbstractSummonedSword sword)||!sword.getPersistentData().getBoolean(MARK)) return;
        Flight f=FLIGHTS.get(sword);
        if(f==null||e.getEntity()!=f.target||!f.binding.matches(f.owner)||!validTarget(f.owner,f.target)) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent e) {
        DamageBudget budget=SA_BUDGET.get();
        if(budget!=null&&budget.matches(e.getSource(),e.getEntity())) e.setAmount(Math.min(e.getAmount(),budget.remaining));
        if(e.getSource().getDirectEntity() instanceof EntityAbstractSummonedSword sword&&sword.getPersistentData().getBoolean(MARK)) {
            Flight f=FLIGHTS.get(sword);e.setAmount(f==null?0:Math.min(e.getAmount(),f.budget));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e) {
        DamageBudget budget=SA_BUDGET.get();
        if(budget!=null&&budget.matches(e.getSource(),e.getEntity())) {
            float accepted=Math.max(0,Math.min(e.getAmount(),budget.remaining));e.setAmount(accepted);budget.remaining-=accepted;
        }
        if(e.getSource().getDirectEntity() instanceof EntityAbstractSummonedSword sword&&sword.getPersistentData().getBoolean(MARK)) {
            Flight f=FLIGHTS.get(sword);e.setAmount(f==null?0:Math.min(e.getAmount(),f.budget));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {cancel(p);OWNERS.remove(p.getUUID());}
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {if(e.getEntity() instanceof ServerPlayer p) {cancel(p);OWNERS.remove(p.getUUID());}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {if(e.getEntity() instanceof ServerPlayer p) {cancel(p);OWNERS.remove(p.getUUID());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {OWNERS.clear();FLIGHTS.clear();}
    private SakuraShardAttackService() {}
}
