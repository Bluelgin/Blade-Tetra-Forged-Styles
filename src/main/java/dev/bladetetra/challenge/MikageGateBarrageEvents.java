package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Vanilla target clicks arm one sword input; native melee motion enters through MikageDuelEvents. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class MikageGateBarrageEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() instanceof MikageGateCoreEntity core) {
            var release = core.release(); if (release != null) release.swing(player, false);
        }
    }
    private MikageGateBarrageEvents() {}
}
