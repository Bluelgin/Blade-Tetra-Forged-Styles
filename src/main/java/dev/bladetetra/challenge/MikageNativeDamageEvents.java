package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Adapt the amount of an actual native hurt; never schedule a second hit or replace native geometry. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageNativeDamageEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        var source = event.getSource().getDirectEntity();
        var traced = MikageBladeAttackTrace.projectile(); if (traced != null && event.getSource().getEntity() == MikageNativeCombat.shooter(traced)) source = traced;
        if (!MikageNativeCombat.owns(source) || !(MikageNativeCombat.shooter(source) instanceof MikageEntity boss)) return;
        if (!boss.encounter().eligible(player)) { event.setCanceled(true); return; }
        event.setAmount(boss.damage().nativeDamage(player, event.getAmount()));
        if (event.getAmount() > 0) MikageNativeCombat.accepted(source, player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        var source = event.getSource().getDirectEntity(); var traced = MikageBladeAttackTrace.projectile();
        if (traced != null && event.getSource().getEntity() == MikageNativeCombat.shooter(traced)) source = traced;
        if (MikageNativeCombat.owns(source) && MikageNativeCombat.shooter(source) instanceof MikageEntity boss
                && boss.encounter().eligible(player))
            boss.damage().nativeDamageAccepted(player, event.getAmount());
    }
    private MikageNativeDamageEvents() { }
}
