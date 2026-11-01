package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.InheritedCombatDamage;
import dev.bladetetra.network.*;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.event.*;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static dev.bladetetra.network.AkatsukiThreadPacket.Phase.*;

/** Online, weapon-bound SA channel; escrow stays entirely with AkatsukiBloodBlade. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class AkatsukiBloodTendrils {
    static final int WINDUP=12,BURN_PER_SECOND=120,BIND_TICKS=10,RETURN_TICKS=10;
    private static final UUID ROOT=UUID.fromString("bf60cd9d-b6f7-40d4-a15b-15454b661239");
    private record Request(ItemStack blade,long at) {}
    private static final Map<UUID,Request> REQUESTS=new HashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final ThreadLocal<Budget> DAMAGE=new ThreadLocal<>();
    private record Budget(ServerPlayer owner,LivingEntity victim,float amount) {
        boolean matches(net.minecraft.world.damagesource.DamageSource source,LivingEntity entity) {
            return entity==victim&&source.getDirectEntity()==owner&&SoulLegacyDamageGuard.isSecondary(source);
        }
    }
    private static final class Session {
        final ServerPlayer owner;final ItemStack blade;final UUID binding;final int slot;final long started,sequence,budget;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final float attack;
        long spent,lastTick=-1,bindAt=-1,returnAt=-1;Vec3 previous;List<LivingEntity> targets=List.of();
        Session(ServerPlayer p,long points) {
            owner=p;blade=p.getMainHandItem();slot=p.getInventory().selected;dimension=p.level().dimension();
            binding=blade.getTag().getCompound(AkatsukiBloodBlade.TAG).getUUID("binding");
            started=p.level().getGameTime();sequence=SakuraCombatVisuals.nextSequence();budget=points;
            attack=(float)p.getAttributeValue(Attributes.ATTACK_DAMAGE);previous=p.position();
        }
        boolean held() {
            return owner.isAlive()&&!owner.isSpectator()&&!owner.isPassenger()&&owner.getMainHandItem()==blade
                    &&owner.getInventory().selected==slot&&owner.level().dimension().equals(dimension)
                    &&AkatsukiBloodBlade.full(owner)&&blade.getTag().getCompound(AkatsukiBloodBlade.TAG).hasUUID("binding")
                    &&binding.equals(blade.getTag().getCompound(AkatsukiBloodBlade.TAG).getUUID("binding"));
        }
    }
    public static boolean active(ServerPlayer p) {return SESSIONS.containsKey(p.getUUID());}
    /** Same physical reach as native ordinary melee (ENTITY_REACH - 1), not SA's reduced custom length. */
    static double range(ServerPlayer p) {
        var reach=p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        double melee=(reach==null?4:reach.getValue()-1)+AkatsukiBloodBlade.meleeExtension(p);
        return Double.isFinite(melee)?Math.max(0,melee)*1.5:0;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void art(SlashBladeEvent.PerformSlashArtEvent e) {
        if(!(e.getEntityLiving() instanceof ServerPlayer p))return;
        if(active(p)){e.setComboState(AkatsukiThreadCombos.THRUST_ID);return;}
        if(e.getType()==null||e.getType()==SlashArts.ArtsType.Fail||!AkatsukiBloodBlade.full(p)
                ||!AkatsukiBloodBlade.orbiting(p)||AkatsukiExecution.switchBlocked(p)||p.isPassenger())return;
        long points=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r->r.getRankPoint(p.level().getGameTime())).orElse(0L);
        if(points<=0)return;
        REQUESTS.put(p.getUUID(),new Request(p.getMainHandItem(),p.level().getGameTime()));
        e.setComboState(AkatsukiThreadCombos.THRUST_ID);
    }
    public static void committed(LivingEntity user) {
        if(!(user instanceof ServerPlayer p)||active(p))return;
        var request=REQUESTS.remove(p.getUUID());
        if(request==null||request.blade!=p.getMainHandItem()||request.at!=p.level().getGameTime()
                ||!AkatsukiBloodBlade.full(p)||!AkatsukiBloodBlade.orbiting(p))return;
        var state=request.blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if(state==null||!AkatsukiThreadCombos.THRUST_ID.equals(state.getComboSeq())||state.getLastActionTime()!=request.at)return;
        long points=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r->r.getRankPoint(request.at)).orElse(0L);
        if(points<=0)return;
        var s=new Session(p,points);SESSIONS.put(p.getUUID(),s);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null){speed.removeModifier(ROOT);speed.addTransientModifier(new AttributeModifier(ROOT,"Blood thread stance",-1,AttributeModifier.Operation.MULTIPLY_TOTAL));}
        SoulActivityService.triggered(p,s.blade,SoulLegacyState.Legacy.AKATSUKI);send(s,BURN,List.of());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void motion(BladeMotionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&active(p))e.setCanceled(true);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&active(p)&&DAMAGE.get()==null)e.setCanceled(true);
    }
    private static boolean valid(Session s,LivingEntity target) {
        Vec3 delta=target.getBoundingBox().getCenter().subtract(s.owner.getEyePosition());
        return AkatsukiExecution.canAffect(s.owner,target)&&target.isAttackable()
                &&!target.isInvulnerableTo(s.owner.damageSources().playerAttack(s.owner))
                &&delta.lengthSqr()<=range(s.owner)*range(s.owner)&&delta.normalize().dot(s.owner.getLookAngle())>.35&&s.owner.hasLineOfSight(target);
    }
    private static List<LivingEntity> acquire(Session s) {
        return s.owner.level().getEntitiesOfClass(LivingEntity.class,s.owner.getBoundingBox().inflate(range(s.owner)),t->valid(s,t)).stream()
                .sorted(Comparator.comparingDouble(t->s.owner.distanceToSqr(t))).limit(4).toList();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        long now=p.level().getGameTime();var r=REQUESTS.get(p.getUUID());if(r!=null&&now>r.at)REQUESTS.remove(p.getUUID());
        var s=SESSIONS.get(p.getUUID());if(s==null||s.lastTick==now)return;s.lastTick=now;
        var state=s.blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if(!s.held()||state==null||!AkatsukiThreadCombos.THRUST_ID.equals(state.getComboSeq())
                ||p.position().distanceToSqr(s.previous)>Math.max(4,p.getDeltaMovement().lengthSqr()*4+.25)
                ||now-s.started>2300){cancel(p);return;}
        s.previous=p.position();
        if(now-s.started>=WINDUP) {
            var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(ROOT);
        }
        if(s.returnAt>=0){if(now-s.returnAt>=RETURN_TICKS)cancel(p);return;}
        if(s.bindAt>=0) {
            var visible=s.targets.stream().filter(target->valid(s,target)).toList();
            if(visible.size()!=s.targets.size()){s.targets=visible;send(s,BIND,s.targets);}
            if(now-s.bindAt>=BIND_TICKS) {
                boolean anyExecuted=false;
                for(var target:s.targets)if(valid(s,target)) {
                    boolean executed=AkatsukiExecution.threadFinish(p,target,s.attack);
                    anyExecuted|=executed;
                    if(!executed&&target.isAlive())hit(s,target,s.attack*.8F);
                    send(s,executed?MIST:SNAP,List.of(target));
                }
                if(anyExecuted)AkatsukiExecution.threadFinished(p);
                s.returnAt=now;send(s,RETURN,s.targets);
            }
            return;
        }
        if(now-s.started<WINDUP)return;
        long due=Math.min(s.budget,Math.max(0,now-s.started-WINDUP+1)*BURN_PER_SECOND/20);
        long remaining=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(rank->{
            long available=Math.max(0,rank.getRankPoint(now)),pay=Math.min(available,Math.max(0,due-s.spent));
            if(pay>0)rank.addRankPoint(p,-pay);s.spent+=pay;return Math.max(0,rank.getRankPoint(now));
        }).orElse(0L);
        boolean lostTarget=s.targets.stream().anyMatch(target->!valid(s,target));
        if(lostTarget)s.targets=s.targets.stream().filter(target->valid(s,target)).toList();
        if(now%5==0){s.targets=acquire(s);send(s,BURN,s.targets);}
        else if(lostTarget)send(s,BURN,s.targets);
        if(now%8==0)for(var target:s.targets)if(valid(s,target))hit(s,target,s.attack*.24F);
        if(s.spent>=s.budget||remaining==0) {
            s.targets=acquire(s);s.bindAt=now;send(s,BIND,s.targets);
        }
    }
    private static void hit(Session s,LivingEntity target,float amount) {
        damage(s.owner,target,amount);
    }
    static boolean damage(ServerPlayer p,LivingEntity target,float amount) {
        return damage(p,target,amount,p.damageSources().playerAttack(p));
    }
    static boolean damage(ServerPlayer p,LivingEntity target,float amount,net.minecraft.world.damagesource.DamageSource source) {
        if(!Float.isFinite(amount)||amount<=0)return false;
        var old=DAMAGE.get();DAMAGE.set(new Budget(p,target,amount));int invulnerability=target.invulnerableTime;
        try {
            if(MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(p,target)))return false;
            target.invulnerableTime=0;
            return InheritedCombatDamage.apply(()->SoulLegacyDamageGuard.apply(()->target.hurt(source,amount)));
        } finally {target.invulnerableTime=Math.max(invulnerability,target.invulnerableTime);if(old==null)DAMAGE.remove();else DAMAGE.set(old);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent e) {var b=DAMAGE.get();if(b!=null&&b.matches(e.getSource(),e.getEntity()))e.setAmount(Math.min(e.getAmount(),b.amount));}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e) {var b=DAMAGE.get();if(b!=null&&b.matches(e.getSource(),e.getEntity()))e.setAmount(Math.min(e.getAmount(),b.amount));}
    private static void send(Session s,AkatsukiThreadPacket.Phase phase,List<LivingEntity> targets) {
        var list=targets.stream().limit(4).map(t->new AkatsukiThreadPacket.Target(t.getId(),t.getBoundingBox().getCenter(),Math.min(16,t.getBbWidth()),Math.min(32,t.getBbHeight()))).toList();
        var packet=new AkatsukiThreadPacket(s.owner.getUUID(),s.binding,s.dimension.location(),s.sequence,
                s.owner.level().getGameTime()-s.started,phase,Math.max(0,1-s.spent/(float)Math.max(1,s.budget)),list);
        if(packet.valid())ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->s.owner),packet);
    }
    public static void cancel(ServerPlayer p) {
        REQUESTS.remove(p.getUUID());var s=SESSIONS.remove(p.getUUID());
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(ROOT);
        if(s==null)return;if(p.level().dimension().equals(s.dimension))send(s,END,List.of());
        s.blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state->{
            if(AkatsukiThreadCombos.THRUST_ID.equals(state.getComboSeq()))state.updateComboSeq(p,ComboStateRegistry.PIERCING_END.getId());
        });
    }
    @SubscribeEvent public static void hurtOwner(LivingDamageEvent e) {if(e.getAmount()>0&&e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {if(e.getEntity() instanceof ServerPlayer p)cancel(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {for(var s:List.copyOf(SESSIONS.values()))cancel(s.owner);REQUESTS.clear();}
    private AkatsukiBloodTendrils() {}
}
