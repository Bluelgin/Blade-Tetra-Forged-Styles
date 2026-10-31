package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One server scheduler for bounded, entity-free trails and grounded swords. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class RaikiriFieldService {
    private static final List<Trail> TRAILS=new ArrayList<>();
    private static final List<Sword> SWORDS=new ArrayList<>();
    private static int nextVisualId=1;
    enum DamageKind { TRAIL_STRIKE, SWORD_DISCHARGE, ARMOR_DISCHARGE }
    private record Impact(LivingEntity target,float cap,DamageKind kind) {}
    private static final ThreadLocal<Impact> IMPACT=new ThreadLocal<>();
    private record Strike(Vec3 position,long due) {}
    private static final class Trail {
        final RaikiriActionObserver.Session session;
        final UUID action;
        long expires;
        final List<Strike> strikes=new ArrayList<>();
        final RaikiriRules.Budget budget;
        int index;
        boolean sealed;
        Vec3 last;
        Trail(RaikiriActionObserver.Session s,UUID action,long now) {
            session=s;this.action=action;expires=RaikiriRules.trailExpiry(now,RaikiriConfig.MAX_OBSERVE_TICKS.get(),
                    RaikiriConfig.DELAY_START.get(),RaikiriConfig.DELAY_END.get());
            budget=new RaikiriRules.Budget(RaikiriConfig.TRAIL_BUDGET.get().floatValue(),
                    RaikiriConfig.TRAIL_TARGET_CAP.get().floatValue());
        }
        void sample(Vec3 position,long tick) {
            if(strikes.size()>=RaikiriConfig.TRAIL_SAMPLES.get()) return;
            if(last!=null && last.distanceTo(position)<RaikiriConfig.SAMPLE_SPACING.get()) return;
            int start=Math.min(RaikiriConfig.DELAY_START.get(),RaikiriConfig.DELAY_END.get());
            int end=Math.max(RaikiriConfig.DELAY_START.get(),RaikiriConfig.DELAY_END.get());
            long due=tick+RaikiriRules.delay(strikes.size(),RaikiriConfig.TRAIL_SAMPLES.get(),start,end);
            strikes.add(new Strike(position,due));
            expires=Math.max(expires,due+2); // Also safe if config changes during an action.
            last=position;
        }
    }
    private static final class Sword {
        final RaikiriActionObserver.Session session;
        final Vec3 pos;
        final int visual=nextVisualId++;
        final long born,expires;
        final RaikiriRules.Budget budget;
        final RaikiriRules.Charge charge=new RaikiriRules.Charge();
        final Set<UUID> viewers=new HashSet<>();
        long next;
        Sword(RaikiriActionObserver.Session s,Vec3 pos,long now) {
            session=s;this.pos=pos;born=now;expires=now+RaikiriConfig.LIFETIME.get();
            next=now+RaikiriConfig.PERIOD.get();
            float total=RaikiriConfig.LIFE_BUDGET.get().floatValue();
            budget=new RaikiriRules.Budget(total,total);
        }
    }
    static boolean hasTrailCapacity(UUID owner) {
        return TRAILS.size()<RaikiriConfig.TRAILS.get()
                && TRAILS.stream().filter(t->t.session.owner.equals(owner)).count()<3;
    }
    /** Only observed endpoints are scheduled; never interpolate a predicted dash. */
    static void segment(ServerPlayer p,RaikiriActionObserver.Session s,UUID action,
            Vec3 from,Vec3 to,long fromTick,long toTick) {
        Trail trail=TRAILS.stream().filter(t->t.action.equals(action)).findFirst().orElse(null);
        if(trail==null && hasTrailCapacity(s.owner)) {
            trail=new Trail(s,action,fromTick);TRAILS.add(trail);trail.sample(from,fromTick);
        }
        if(trail!=null) trail.sample(to,toTick);
        AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.STEP,from.add(0,0.1,0),to.add(0,0.1,0),1);
        double radius=RaikiriConfig.RESONANCE_RADIUS.get();
        for(Sword sword:SWORDS) {
            if(sword.session!=s || !validSword(p,sword)) continue;
            RaikiriRules.Point close=RaikiriRules.closest(point(sword.pos),point(from),point(to));
            Vec3 near=new Vec3(close.x(),close.y(),close.z());
            if(RaikiriRules.distanceSquared(point(sword.pos),point(from),point(to))<=radius*radius
                    && clearPath(p.serverLevel(),near.add(0,0.4,0),sword.pos.add(0,0.4,0),p)
                    && sword.charge.resonate(action))
                AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                        AwakenedBladeVfxPacket.RESONANCE,sword.pos,sword.pos,sword.visual);
        }
    }
    static void finish(UUID action) { for(Trail trail:TRAILS) if(trail.action.equals(action)) trail.sealed=true; }
    private static RaikiriRules.Point point(Vec3 p) { return new RaikiriRules.Point(p.x,p.y,p.z); }
    /** Real collision-shape surface, including slabs; no unsupported or void anchors. */
    static Vec3 support(ServerPlayer p) {
        if(!p.onGround()) return null;
        Vec3 from=p.position().add(0,0.15,0),to=p.position().add(0,-0.7,0);
        if(!RaikiriActionObserver.loaded(p.serverLevel(),new AABB(from,to))) return null;
        HitResult hit=p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK || Math.abs(hit.getLocation().y-p.getY())>0.3) return null;
        Vec3 pos=new Vec3(p.getX(),hit.getLocation().y,p.getZ());
        return RaikiriActionObserver.withinBorder(p.serverLevel(),new AABB(pos,pos))?pos:null;
    }
    static void plant(ServerPlayer p,RaikiriActionObserver.Session s) {
        Vec3 pos=support(p);if(pos==null || !s.valid(p)) return;
        List<Sword> owned=SWORDS.stream().filter(a->a.session.owner.equals(s.owner)).toList();
        for(int i=0;i<owned.size()-RaikiriConfig.SWORDS.get()+1;i++) remove(owned.get(i),p.serverLevel());
        if(SWORDS.size()>=RaikiriConfig.ANCHORS.get()) return;
        Sword sword=new Sword(s,pos,p.level().getGameTime());SWORDS.add(sword);
        syncNewViewers(p.serverLevel(),sword);
    }
    private static boolean validSword(ServerPlayer p,Sword sword) {
        ServerLevel level=p.serverLevel();
        if(!sword.session.valid(p) || level.getGameTime()>=sword.expires
                || !RaikiriActionObserver.loaded(level,new AABB(sword.pos,sword.pos.add(0,1.5,0)))) return false;
        HitResult hit=level.clip(new ClipContext(sword.pos.add(0,0.15,0),sword.pos.add(0,-0.2,0),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(sword.pos)<0.01;
    }
    static void tick(MinecraftServer server) {
        Iterator<Trail> trails=TRAILS.iterator();
        while(trails.hasNext()) {
            Trail t=trails.next();ServerPlayer p=RaikiriActionObserver.owner(server,t.session);
            if(!t.session.valid(p)) { trails.remove();continue; }
            long now=p.level().getGameTime();
            if(t.index<t.strikes.size() && now>=t.strikes.get(t.index).due) {
                Vec3 at=t.strikes.get(t.index).position.add(0,0.4,0);
                t.index++;
                if(RaikiriActionObserver.loaded(p.serverLevel(),new AABB(at,at).inflate(0.2))) {
                    AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                            AwakenedBladeVfxPacket.BOLT,at.add(0,2.4,0),at,0);
                    double radius=RaikiriConfig.STRIKE_RADIUS.get();
                    for(LivingEntity target:targets(p,at,radius,2)) {
                        float amount=t.budget.reserve(target.getUUID(),RaikiriConfig.TRAIL_TARGET_CAP.get().floatValue());
                        damage(p,target,amount,DamageKind.TRAIL_STRIKE);
                    }
                }
            }
            if(t.sealed && t.index>=t.strikes.size() || now>t.expires) trails.remove();
        }
        Iterator<Sword> swords=SWORDS.iterator();
        while(swords.hasNext()) {
            Sword s=swords.next();ServerPlayer p=RaikiriActionObserver.owner(server,s.session);
            if(p==null || !validSword(p,s)) {
                ServerLevel level=server.getLevel(s.session.dimension);
                if(level!=null) vanish(level,s);
                swords.remove();continue;
            }
            long now=p.level().getGameTime();
            if(now%20==0) syncNewViewers(p.serverLevel(),s);
            if(now<s.next) continue;
            s.next=now+RaikiriConfig.PERIOD.get();
            boolean charged=s.charge.consume();
            float round=RaikiriConfig.ROUND_BUDGET.get().floatValue()
                    *(charged?RaikiriConfig.RESONANCE_MULTIPLIER.get().floatValue():1F);
            List<LivingEntity> targets=targets(p,s.pos.add(0,0.5,0),RaikiriConfig.SWORD_RANGE.get(),RaikiriConfig.TARGETS.get());
            if(targets.isEmpty()) continue;
            for(LivingEntity target:targets) {
                float amount=s.budget.reserve(target.getUUID(),round/targets.size());
                if(amount<=0) break;
                if(damage(p,target,amount,DamageKind.SWORD_DISCHARGE))
                    AwakenedBladeCombat.visual(p.serverLevel(),SoulLegacyState.Legacy.RAIKIRI,
                            AwakenedBladeVfxPacket.BOLT,s.pos.add(0,0.8,0),target.getBoundingBox().getCenter(),charged?1:0);
            }
        }
    }
    static List<LivingEntity> targets(ServerPlayer p,Vec3 origin,double radius,int limit) {
        ServerLevel level=p.serverLevel(); AABB box=new AABB(origin,origin).inflate(radius);
        // Never load adjacent chunks to search or ray trace.
        if(!RaikiriActionObserver.loaded(level,box)) return List.of();
        List<LivingEntity> candidates=new ArrayList<>();
        // Cap collection before filtering/raycasting: dense crowds cannot grow
        // a temporary list or run unbounded legal-path tests per discharge.
        level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                box,t->true,candidates,32);
        return candidates.stream().filter(t->AwakenedBladeCombat.canAffect(p,t)
                && t.getBoundingBox().getCenter().distanceToSqr(origin)<=radius*radius)
                .sorted(Comparator.comparingDouble(t->t.distanceToSqr(origin)))
                .filter(t->clearPath(level,origin,t.getBoundingBox().getCenter(),p))
                .limit(limit).toList();
    }
    static boolean clearPath(ServerLevel level,Vec3 from,Vec3 to,ServerPlayer p) {
        return RaikiriActionObserver.loaded(level,new AABB(from,to))
                && level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p))
                        .getType()==HitResult.Type.MISS;
    }
    static boolean damage(ServerPlayer p,LivingEntity target,float amount,DamageKind kind) {
        if(amount<=0 || IMPACT.get()!=null || !AwakenedBladeCombat.canAffect(p,target)) return false;
        var bladeState=p.getMainHandItem().getCapability(dev.bladetetra.item.ModularSlashBladeItem.BLADESTATE).orElse(null);
        // Native RankPointHandler resolves its owner's combo during hurt. If
        // inventory ticking has not yet retired a stale node, skip this extra
        // impact instead of letting lightning advance a native timeout.
        if(bladeState!=null && !mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId().equals(bladeState.getComboSeq())
                && !bladeState.getComboSeq().equals(RaikiriActionObserver.activeCombo(p,bladeState))) return false;
        int previous=target.invulnerableTime;float before=target.getHealth();target.invulnerableTime=0;
        var rank=p.getCapability(mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank.RANK_POINT).orElse(null);
        long raw=rank==null?0:rank.getRawRankPoint(), updated=rank==null?0:rank.getLastUpdate(), rise=rank==null?0:rank.getLastRankRise();
        IMPACT.set(new Impact(target,amount,kind)); boolean landed=false;
        try {
            landed=InheritedCombatDamage.apply(()->SoulLegacyDamageGuard.apply(()->
                    target.hurt(p.level().damageSources().playerAttack(p),amount)));
            landed=landed && target.getHealth()<before;
            return landed;
        } finally {
            IMPACT.remove();if(!landed) target.invulnerableTime=previous;
            // Native 1.9.63 RankPointHandler awards on LivingHurtEvent even
            // for player-attributed extra damage. Restore ONLY this scoped
            // impact's native rank side effects, preserving natural decay.
            if(rank!=null && (rank.getRawRankPoint()!=raw || rank.getLastUpdate()!=updated || rank.getLastRankRise()!=rise)) {
                rank.setRawRankPoint(raw);rank.setLastUpdte(updated);rank.setLastRankRise(rise);
                rank.addRankPoint(p,0);
            }
        }
    }
    // Forge fires this synchronously inside LivingEntity.hurt -> knockback,
    // before velocity is changed. Only this discharge's target is suppressed.
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void dischargeKnockback(LivingKnockBackEvent e) {
        Impact impact=IMPACT.get();
        if(impact!=null && impact.kind!=DamageKind.TRAIL_STRIKE && e.getEntity()==impact.target)
            e.setCanceled(true);
    }
    // Third-party amplification cannot turn an explicitly budgeted secondary
    // impact into unbounded damage. Cancelled damage remains cancelled.
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void capHurt(LivingHurtEvent e) {
        Impact i=IMPACT.get();if(i!=null && e.getEntity()==i.target)
            e.setAmount(Float.isFinite(e.getAmount())?Math.max(0,Math.min(i.cap,e.getAmount())):0);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void capDamage(LivingDamageEvent e) {
        Impact i=IMPACT.get();if(i!=null && e.getEntity()==i.target)
            e.setAmount(Float.isFinite(e.getAmount())?Math.max(0,Math.min(i.cap,e.getAmount())):0);
    }
    private static void syncNewViewers(ServerLevel level,Sword s) {
        for(ServerPlayer viewer:level.players()) {
            if(viewer.distanceToSqr(s.pos)<=64D*64D && s.viewers.add(viewer.getUUID()))
                AwakenedBladeCombat.visualTo(viewer,AwakenedBladeVfxPacket.GROUND_SWORD,s.pos,
                        new Vec3(s.expires-level.getGameTime(),0,0),s.visual);
        }
    }
    private static void vanish(ServerLevel level,Sword s) {
        AwakenedBladeCombat.visual(level,SoulLegacyState.Legacy.RAIKIRI,
                AwakenedBladeVfxPacket.REMOVE_SWORD,s.pos,s.pos,s.visual);
    }
    private static void remove(Sword s,ServerLevel level) { vanish(level,s);SWORDS.remove(s); }
    static void clear(UUID owner,MinecraftServer server) {
        TRAILS.removeIf(t->t.session.owner.equals(owner));
        Iterator<Sword> it=SWORDS.iterator();while(it.hasNext()) {
            Sword s=it.next();if(!s.session.owner.equals(owner)) continue;
            ServerLevel l=server==null?null:server.getLevel(s.session.dimension);if(l!=null) vanish(l,s);
            it.remove();
        }
    }
    static int swordCount(UUID owner) { return (int)SWORDS.stream().filter(s->s.session.owner.equals(owner)).count(); }
    static int trailCount(UUID owner) { return (int)TRAILS.stream().filter(t->t.session.owner.equals(owner)).count(); }
    static List<Vec3> samples(UUID owner) {
        return TRAILS.stream().filter(t->t.session.owner.equals(owner))
                .flatMap(t->t.strikes.stream()).map(Strike::position).toList();
    }
    static boolean charged(UUID owner) { return SWORDS.stream().anyMatch(s->s.session.owner.equals(owner) && s.charge.charged()); }
    static void forgetViewer(UUID viewer) { for(Sword sword:SWORDS) sword.viewers.remove(viewer); }
    static void reset() { TRAILS.clear();SWORDS.clear(); }
    private RaikiriFieldService() {}
}
