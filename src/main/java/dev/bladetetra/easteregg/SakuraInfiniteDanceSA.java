package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-authoritative SA. No held input, permanent blade writes or custom resource. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraInfiniteDanceSA {
    public enum Phase { IDLE, STARTING, DANCING, FINISHING }
    private record Request(ItemStack blade, int slot, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, long at, int rank) {}
    private static final Map<UUID,Request> REQUESTS=new HashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final UUID SLOW=UUID.fromString("7bfc53b0-5f14-4b17-b7b1-33c26abe3c30");
    private static final class Session {
        final Request request;
        Phase phase=Phase.STARTING;
        int stage=1;
        long actionAt, lastNativeTick=Long.MIN_VALUE, nextCharge;
        boolean advancing;
        Session(Request request) {this.request=request;actionAt=request.at;nextCharge=request.at+10;}
    }
    public static boolean active(ServerPlayer p,ItemStack blade) {
        var s=SESSIONS.get(p.getUUID());return s!=null&&matches(p,s.request)&&s.request.blade==blade;
    }
    public static int initialRank(ServerPlayer p,ItemStack blade) {
        var s=SESSIONS.get(p.getUUID());return active(p,blade)?s.request.rank:0;
    }
    public static Phase phase(ServerPlayer p) {var s=SESSIONS.get(p.getUUID());return s==null?Phase.IDLE:s.phase;}
    private static boolean matches(ServerPlayer p,Request r) {
        return p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&p.getMainHandItem()==r.blade
                &&p.getInventory().selected==r.slot&&p.level().dimension().equals(r.dimension)
                &&SakuraEndlessFlow.eligible(r.blade)
                &&r.blade.getCapability(ItemSlashBlade.BLADESTATE).map(b->!b.isBroken()&&!b.isSealed()).orElse(false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void art(SlashBladeEvent.PerformSlashArtEvent e) {
        if(!(e.getEntityLiving() instanceof ServerPlayer p)||!SakuraEndlessFlow.eligible(p.getMainHandItem())) return;
        if(SESSIONS.containsKey(p.getUUID())) {e.setComboState(ComboStateRegistry.NONE.getId());return;}
        if(e.getType()==null||e.getType()==SlashArts.ArtsType.Fail) return;
        SakuraEndlessFlow.reconcile(p);
        int grade=p.getCapability(CapabilityConcentrationRank.RANK_POINT)
                .map(r->r.getRank(p.level().getGameTime()).level).orElse(0);
        if(grade<4||!SakuraDanceCombos.compatible()||!p.isAlive()||p.isSpectator()||p.isPassenger()) {
            e.setComboState(ComboStateRegistry.NONE.getId());return;
        }
        REQUESTS.put(p.getUUID(),new Request(p.getMainHandItem(),p.getInventory().selected,
                p.level().dimension(),p.level().getGameTime(),grade));
        e.setComboState(SakuraDanceCombos.id(1));
    }
    /** Node clickAction runs after upstream commits combo and action time. */
    public static void committed(LivingEntity user,int stage) {
        if(!(user instanceof ServerPlayer p)) return;
        Session s=SESSIONS.get(p.getUUID());
        if(s==null) {
            Request r=REQUESTS.remove(p.getUUID());
            if(stage!=1||r==null||!matches(p,r)||r.at!=p.level().getGameTime()) return;
            s=new Session(r);SESSIONS.put(p.getUUID(),s);
            SakuraShardAttackService.cancel(p);
            var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null) {speed.removeModifier(SLOW);speed.addTransientModifier(new AttributeModifier(SLOW,
                    "Sakura infinite dance",-.75,AttributeModifier.Operation.MULTIPLY_TOTAL));}
        } else if(!s.advancing||stage!=(s.stage==6?1:s.stage+1)) return;
        s.stage=stage;s.actionAt=p.level().getGameTime();s.lastNativeTick=Long.MIN_VALUE;
        if(s.phase!=Phase.FINISHING) s.phase=Phase.DANCING;
        SakuraDanceCombos.nativeNode(stage).clickAction(user);
    }
    public static void nativeTick(LivingEntity user,int stage) {
        if(!(user instanceof ServerPlayer p)) return;
        Session s=SESSIONS.get(p.getUUID());
        if(s==null||!matches(p,s.request)||s.stage!=stage) return;
        long now=p.level().getGameTime(),elapsed=now-s.actionAt;
        if(s.lastNativeTick==now||elapsed<0||elapsed>=SakuraFlowRules.minimumAutoTick(stage)) return;
        s.lastNativeTick=now;
        SakuraDanceCombos.nativeNode(stage).tickAction(user);
        SakuraEndlessFlow.reconcile(p);
        boolean funded=p.getCapability(CapabilityConcentrationRank.RANK_POINT).map(r->{
            long points=r.getRankPoint(now);
            return points>0&&(now<s.nextCharge||!GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()
                    ||points>=SakuraFlowRules.halfSecondCost(s.request.rank,GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get()));
        }).orElse(false);
        if(s.phase!=Phase.FINISHING&&funded) SakuraShardAttackService.dancePulse(p,s.request.blade,stage,(int)elapsed);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void next(SlashBladeEvent.NextComboEvent e) {
        if(SakuraDanceCombos.owns(e.getSlashBladeState().getComboSeq())
                ||e.getUser() instanceof ServerPlayer p&&active(p,e.getBlade())) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void charge(SlashBladeEvent.ChargeActionEvent e) {
        if(e.getEntityLiving() instanceof ServerPlayer p&&active(p,p.getMainHandItem())) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&active(p,p.getMainHandItem())
                && !p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).map(b->b.onClick()).orElse(false))
            e.setCanceled(true); // native AttackManager sets onClick around its authored melee callbacks
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void use(PlayerInteractEvent.RightClickItem e) {
        if(e.getEntity() instanceof ServerPlayer p&&active(p,p.getMainHandItem())) e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)) return;
        Request request=REQUESTS.get(p.getUUID());
        if(request!=null&&request.at<p.level().getGameTime()) REQUESTS.remove(p.getUUID());
        Session s=SESSIONS.get(p.getUUID());
        if(s==null) {
            removeSlow(p);
            p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(b->{
                if(SakuraDanceCombos.owns(b.getComboSeq())) {b.setComboSeq(ComboStateRegistry.NONE.getId());b.setLastActionTime(p.level().getGameTime());}
            });
            return;
        }
        var state=s.request.blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        long now=p.level().getGameTime();
        boolean controlled=p.isSleeping()||p.isFullyFrozen()||p.getCapability(CapabilityMobEffect.MOB_EFFECT)
                .map(m->m.isStun(now)).orElse(false);
        if(!matches(p,s.request)||state==null||!SakuraDanceCombos.id(s.stage).equals(state.getComboSeq())
                ||state.getLastActionTime()!=s.actionAt||now<s.actionAt||controlled) {end(p);return;}
        if(p.isUsingItem()) p.stopUsingItem();
        SakuraEndlessFlow.reconcile(p);
        if(now-s.request.at>=GameplayConfig.SAKURA_SA_MAX_TICKS.get()) s.phase=Phase.FINISHING;
        p.getCapability(CapabilityConcentrationRank.RANK_POINT).ifPresent(rank->{
            long points=Math.max(0,rank.getRankPoint(now));
            if(points==0) s.phase=Phase.FINISHING;
            if(s.phase!=Phase.FINISHING&&now>=s.nextCharge) {
                int cost=SakuraFlowRules.halfSecondCost(s.request.rank,GameplayConfig.AWAKENED_SAKURA_BURN_POINTS_PER_SECOND.get());
                s.nextCharge=now+10;
                if(GameplayConfig.ENABLE_AWAKENED_RANK_COSTS.get()) {
                    if(points<cost) s.phase=Phase.FINISHING;
                    else {rank.addRankPoint(p,-cost);if(points==cost) s.phase=Phase.FINISHING;}
                }
            }
        });
        SakuraEndlessFlow.reconcile(p);
        nativeTick(p,s.stage);
        if(now-s.actionAt>=SakuraFlowRules.minimumAutoTick(s.stage)) {
            if(s.phase==Phase.FINISHING) {end(p);return;}
            int next=s.stage==6?1:s.stage+1;
            s.advancing=true;
            try {state.updateComboSeq(p,SakuraDanceCombos.id(next));} finally {s.advancing=false;}
            if(!SakuraDanceCombos.id(next).equals(state.getComboSeq())||state.getLastActionTime()!=now) {end(p);return;}
            nativeTick(p,next);
        }
    }
    public static void end(ServerPlayer p) {
        Session s=SESSIONS.remove(p.getUUID());REQUESTS.remove(p.getUUID());removeSlow(p);
        if(s==null) return;
        SakuraShardAttackService.cancel(p);
        SakuraShardAttackService.cue(p,s.request.blade,1,-1,p.getEyePosition(),-1,p.level().getGameTime());
        s.request.blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state->{
            if(SakuraDanceCombos.owns(state.getComboSeq())) {
                state.setComboSeq(ComboStateRegistry.NONE.getId());state.setLastActionTime(p.level().getGameTime());
            }
        });
    }
    private static void removeSlow(ServerPlayer p) {var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null) speed.removeModifier(SLOW);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingDamageEvent e) {
        if(e.getAmount()>0&&e.getEntity() instanceof ServerPlayer p) end(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {if(e.getEntity() instanceof ServerPlayer p) end(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {if(e.getEntity() instanceof ServerPlayer p) end(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e) {if(e.getEntity() instanceof ServerPlayer p) end(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {if(e.getOriginal() instanceof ServerPlayer p) end(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {SESSIONS.clear();REQUESTS.clear();}
    private SakuraInfiniteDanceSA() {}
}
