package dev.bladetetra.easteregg;

import dev.bladetetra.BladeTetra;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Associates native damage with a committed action. No miss-triggered ordinary volleys. */
@Mod.EventBusSubscriber(modid=BladeTetra.MOD_ID)
public final class SakuraCombatVisuals {
    private static final Map<UUID,Attack> CURRENT=new HashMap<>();
    private static final Map<EntitySlashEffect,Attack> SLASHES=new WeakHashMap<>();
    private static long sequence;
    static long nextSequence() {return ++sequence;}
    private static final class Attack {
        final ServerPlayer player;final ItemStack blade;final int slot;final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final ResourceLocation combo;final long at;
        long slashAt=Long.MIN_VALUE;boolean confirmed, fracture;
        Attack(ServerPlayer p,ItemStack b,ResourceLocation c,long at) {
            player=p;blade=b;slot=p.getInventory().selected;dimension=p.level().dimension();combo=c;this.at=at;
        }
        boolean matches(ServerPlayer p,ItemStack b) {
            return p==player&&blade==b&&slot==p.getInventory().selected&&dimension.equals(p.level().dimension())
                    &&p.isAlive()&&SakuraStanceController.supported(b)&&p.level().getGameTime()-at>=0
                    &&p.level().getGameTime()-at<=40;
        }
    }
    private static Attack action(ServerPlayer p,ItemStack b) {
        var state=b.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if(state==null||SakuraDanceCombos.owns(state.getComboSeq())
                ||state.getComboSeq().getPath().equals("none")) return null;
        Attack a=CURRENT.get(p.getUUID());
        if(a==null||!a.matches(p,b)||!a.combo.equals(state.getComboSeq())||a.at!=state.getLastActionTime()) {
            a=new Attack(p,b,state.getComboSeq(),state.getLastActionTime());CURRENT.put(p.getUUID(),a);
        }
        return a;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void slash(SlashBladeEvent.DoSlashEvent e) {
        if(!(e.getUser() instanceof ServerPlayer p)||!SakuraStanceController.supported(e.getBlade())
                ||SakuraInfiniteDanceSA.active(p,e.getBlade())) return;
        Attack a=action(p,e.getBlade());if(a!=null) a.slashAt=p.level().getGameTime();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void spawned(EntityJoinLevelEvent e) {
        if(e.getLevel().isClientSide()||!(e.getEntity() instanceof EntitySlashEffect slash)
                ||!(slash.getOwner() instanceof ServerPlayer p)) return;
        Attack a=CURRENT.get(p.getUUID());
        if(a!=null&&a.matches(p,p.getMainHandItem())&&a.slashAt==p.level().getGameTime()
                &&p.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                    .map(s->s.getComboSeq().equals(a.combo)&&s.getLastActionTime()==a.at).orElse(false)) {
            if(SLASHES.size()>=4096) SLASHES.clear();SLASHES.put(slash,a);
        }
    }
    public static void confirm(ServerPlayer p,ItemStack b,Entity direct,LivingEntity target,float damage) {
        if(!SakuraStanceController.supported(b)) return;
        if(SakuraInfiniteDanceSA.active(p,b)) {
            if(direct==p||direct instanceof EntitySlashEffect) SakuraShardAttackService.targetHit(p,b,target);
            return;
        }
        boolean nativeMelee=direct==p&&b.getCapability(ItemSlashBlade.BLADESTATE).map(s->s.onClick()).orElse(false);
        Attack a=direct instanceof EntitySlashEffect slash?SLASHES.get(slash):nativeMelee?action(p,b):null;
        if(a==null||a.confirmed||!a.matches(p,b)) return;
        a.confirmed=true;
        if(!SakuraStanceController.dispersed(b)) a.fracture=p.getRandom().nextFloat()<.25F;
        else SakuraShardAttackService.concerto(p,b,target,damage);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)) return;
        Attack a=CURRENT.get(p.getUUID());
        if(a!=null&&a.fracture) {a.fracture=false;if(a.matches(p,p.getMainHandItem())) SakuraStanceController.fractureHit(p,a.blade);}
        if(a!=null&&!a.matches(p,p.getMainHandItem())) CURRENT.remove(p.getUUID());
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void scaleOrdinary(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if(!(e.getSource().getEntity() instanceof ServerPlayer p)
                ||!SakuraEndlessFlow.eligible(p.getMainHandItem())
                ||SakuraInfiniteDanceSA.active(p,p.getMainHandItem())
                ||SoulLegacyDamageGuard.isSecondary(e.getSource())) return;
        var direct=e.getSource().getDirectEntity();
        if(direct==p||direct instanceof EntitySlashEffect) e.setAmount(e.getAmount()*.7F);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        CURRENT.remove(e.getEntity().getUUID());SLASHES.values().removeIf(a->a.player.getUUID().equals(e.getEntity().getUUID()));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {CURRENT.clear();SLASHES.clear();sequence=0;}
    private SakuraCombatVisuals() {}
}
