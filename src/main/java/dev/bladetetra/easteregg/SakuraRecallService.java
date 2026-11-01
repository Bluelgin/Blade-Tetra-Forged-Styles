package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Owns the charged finisher's transient payment, release and two-hit lifecycle. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraRecallService {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final class Session {
        final ServerPlayer owner;final ItemStack blade;final int slot,cost,maxTier,releaseTicks;final long started,sequence;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final LivingEntity target;final Vec3 origin;final float attack,height;final boolean paidRank;
        Vec3 anchor;int tier;long spent,burnBudget;long release=-1;boolean struck,hit,bloomed;
        Session(ServerPlayer p,ItemStack blade,LivingEntity target,float attack,float height,int cost,long points) {
            owner=p;this.blade=blade;slot=p.getInventory().selected;dimension=p.level().dimension();
            this.target=target;origin=anchor=target.getBoundingBox().getCenter();this.attack=attack;this.height=height;
            paidRank=GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get();this.cost=cost;burnBudget=points;maxTier=(int)Math.min(3,points/Math.max(1,cost));releaseTicks=SakuraRecallSequence.burnRelease(points,cost);started=p.level().getGameTime();sequence=SakuraCombatVisuals.nextSequence();
        }
        boolean held() {return owner.isAlive()&&!owner.isSpectator()&&owner.getMainHandItem()==blade
                &&owner.getInventory().selected==slot&&owner.level().dimension().equals(dimension)
                &&SakuraStanceController.supported(blade)
                &&blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .map(b->!b.isBroken()&&!b.isSealed()).orElse(false);}
    }
    public static boolean charging(ServerPlayer p) {var s=SESSIONS.get(p.getUUID());return s!=null&&s.release<0;}
    public static boolean active(ServerPlayer p) {return SESSIONS.containsKey(p.getUUID());}
    public static int duration(ServerPlayer p) {
        var s=SESSIONS.get(p.getUUID());return s==null?14:s.releaseTicks+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM+SakuraRecallSequence.RETURN;
    }
    static boolean valid(ServerPlayer p,LivingEntity target) {
        return SakuraShardAttackService.validTarget(p,target)&&target.isAttackable()
                &&!target.isInvulnerableTo(p.damageSources().playerAttack(p))
                &&p.distanceToSqr(target)<=SakuraRecallSequence.SEARCH_RANGE*SakuraRecallSequence.SEARCH_RANGE
                &&p.serverLevel().hasChunkAt(target.blockPosition());
    }
    static boolean start(ServerPlayer p,ItemStack blade,LivingEntity target) {
        if(active(p)||target==null||!valid(p,target)||SakuraInfiniteDanceSA.active(p,blade))return false;
        float attack=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if(!Float.isFinite(attack)||attack<=0||!blade.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                .map(b->!b.isBroken()&&!b.isSealed()).orElse(false))return false;
        Vec3 center=target.getBoundingBox().getCenter();
        Vec3 top=center.add(0,Math.min(15,target.getBbHeight()*.5+2.5),0);
        var roof=p.level().clip(new ClipContext(center,top,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        float height=(float)(roof.getType()==HitResult.Type.MISS?top.y-center.y:roof.getLocation().y-center.y-.55);
        if(height<.4)return false;
        long[] plan=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r->{
            int grade=r.getRank(p.level().getGameTime()).level;
            int cost=2*SakuraFlowRules.halfSecondCost(grade,GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get());
            long points=GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()?r.getRankPoint(p.level().getGameTime()):Long.MAX_VALUE;
            return new long[]{cost,GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()?Math.max(0,points):3L*cost};
        }).orElse(new long[]{120,0});
        var s=new Session(p,blade,target,attack,height,(int)plan[0],plan[1]);SESSIONS.put(p.getUUID(),s);
        send(s,SakuraRecallPacket.Kind.START,s.maxTier);return true;
    }
    private static void send(Session s,SakuraRecallPacket.Kind kind,int tier) {
        var packet=new SakuraRecallPacket(s.owner.getUUID(),s.blade.getOrCreateTag().getString("id"),s.slot,
                s.dimension.location(),s.sequence,s.started,s.owner.level().getGameTime(),kind,tier,s.target.getId(),s.anchor,s.height,s.releaseTicks);
        if(packet.valid())ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->s.owner),packet);
    }
    public static void cancel(ServerPlayer p) {
        Session s=SESSIONS.remove(p.getUUID());if(s==null)return;
        if(s.paidRank&&s.release<0&&s.spent>0&&p.isAlive())p.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(r->r.addRankPoint(p,s.spent));
        if(p.level().dimension().equals(s.dimension))send(s,SakuraRecallPacket.Kind.CANCEL,s.tier);
        SakuraStanceController.returnRecall(p,SakuraRecallSequence.RETURN);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        Session s=SESSIONS.get(p.getUUID());if(s==null)return;
        long now=p.level().getGameTime();
        if(s.release>=0&&now>=s.release+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM+SakuraRecallSequence.RETURN) {
            SESSIONS.remove(p.getUUID());return;
        }
        if(now<s.started||now-s.started>180||!s.held()||!SakuraStanceController.reassembling(p)) {cancel(p);return;}
        if(s.release<0) {
            if(!valid(p,s.target)||s.target.getBoundingBox().getCenter().distanceToSqr(s.origin)>SakuraRecallSequence.LEASH*SakuraRecallSequence.LEASH) {cancel(p);return;}
            long age=now-s.started;
            long due=SakuraRecallSequence.burnDue(s.burnBudget,age,s.releaseTicks);
            if(due>s.spent) {
                long payment=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r->{
                    long amount=Math.min(due-s.spent,Math.max(0,r.getRankPoint(now)));
                    if(s.paidRank)r.addRankPoint(p,-amount);
                    return s.paidRank?amount:due-s.spent;
                }).orElse(0L);
                s.spent+=payment;
                if(s.spent<due)s.burnBudget=s.spent;
            }
            int tier=s.burnBudget==0?0:(int)Math.min(3,s.spent*s.maxTier/s.burnBudget);
            if(tier>s.tier) {
                s.tier=tier;send(s,SakuraRecallPacket.Kind.STAGE,s.tier);
                p.serverLevel().playSound(null,s.target.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.PLAYERS,.6F,.8F+s.tier*.25F);
            }
            if(s.release<0&&age>=s.releaseTicks)s.release=now;
            long lockAt=s.started+s.releaseTicks-SakuraRecallSequence.LOCK;
            if(now<lockAt) {
                Vec3 delta=s.target.getBoundingBox().getCenter().subtract(s.anchor);
                s.anchor=s.anchor.add(delta.length()<=SakuraRecallSequence.TRACK_STEP?delta:delta.normalize().scale(SakuraRecallSequence.TRACK_STEP));
            }
            if(s.release<0&&age>=SakuraRecallSequence.APPROACH+4&&age<s.releaseTicks-SakuraRecallSequence.LOCK
                    &&age%16==12)SakuraShardAttackService.locator(p,s.target,s.anchor.add(0,s.height,0),s.attack);
            if(s.release>=0) {
                send(s,SakuraRecallPacket.Kind.RELEASE,s.tier);
                p.serverLevel().playSound(null,s.target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                        net.minecraft.sounds.SoundSource.PLAYERS,.8F,.6F);
                SakuraStanceController.returnRecall(p,SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM+SakuraRecallSequence.RETURN);
            }
        }
        if(s.release<0)return;
        float total=(float)(s.attack*SakuraRecallSequence.multiplier(s.tier)*GameplayConfig.SAKURA_RECALL_DAMAGE_SCALE.get());
        if(!s.struck&&now>=s.release+SakuraRecallSequence.RUSH) {
            s.struck=true;
            if(now<=s.release+SakuraRecallSequence.RUSH+1&&valid(p,s.target)
                    &&s.target.getBoundingBox().getCenter().distanceToSqr(s.anchor)<=2.25
                    &&p.level().clip(new ClipContext(s.anchor.add(0,s.height,0),s.anchor,
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS) {
                s.hit=damage(p,s.target,total*.3F,true);
                if(s.hit) {send(s,SakuraRecallPacket.Kind.HIT,s.tier);sound(s);}
            }
        }
        if(s.struck&&!s.bloomed&&now>=s.release+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM) {
            s.bloomed=true;
            if(s.hit&&now<=s.release+SakuraRecallSequence.RUSH+SakuraRecallSequence.BLOOM+1) {
                if(s.target.isAlive()&&valid(p,s.target)&&s.target.getBoundingBox().getCenter().distanceToSqr(s.anchor)<=6.25)
                    damage(p,s.target,total*.7F,true);
                var others=p.serverLevel().getEntitiesOfClass(Mob.class,new AABB(s.anchor,s.anchor).inflate(2.5),
                        t->t!=s.target&&valid(p,t)&&t.getBoundingBox().getCenter().distanceToSqr(s.anchor)<=6.25);
                others.sort(Comparator.comparingDouble(t->t.distanceToSqr(s.anchor)));
                for(int i=0;i<Math.min(4,others.size());i++)damage(p,others.get(i),total*.7F,false);
                { // A confirmed piercing impact always blooms, even when it killed the only enemy.
                    send(s,SakuraRecallPacket.Kind.BLOOM,s.tier);
                    com.mojang.logging.LogUtils.getLogger().debug("SAKURA_BLOOM owner={} sequence={} targetAlive={} tier={}",
                            p.getGameProfile().getName(),s.sequence,s.target.isAlive(),s.tier);
                    p.serverLevel().playSound(null,net.minecraft.core.BlockPos.containing(s.anchor),
                            net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.PLAYERS,.8F+s.tier*.15F,1.1F-s.tier*.1F);
                    p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES,
                            s.anchor.x,s.anchor.y,s.anchor.z,18+s.tier*6,.8,.8,.8,.12);
                }
            }

        }
    }
    private static void sound(Session s) {
        s.owner.serverLevel().playSound(null,s.target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS,1F,.7F);
        s.owner.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.CHERRY_LEAVES,
                s.anchor.x,s.anchor.y,s.anchor.z,16,.65,.6,.65,.05);
    }
    private static boolean damage(ServerPlayer p,LivingEntity target,float amount,boolean followup) {
        if(!Float.isFinite(amount)||amount<=0||MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p,target)))return false;
        int oldInvulnerability=target.invulnerableTime;
        if(followup)target.invulnerableTime=0;
        float before=target.getHealth();
        try {SakuraShardAttackService.secondary(p,target,amount);}
        finally {
            if(followup)target.invulnerableTime=Math.max(oldInvulnerability,target.invulnerableTime);
        }
        return target.getHealth()<before;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {for(var s:List.copyOf(SESSIONS.values()))cancel(s.owner);}
    private SakuraRecallService() {}
}
