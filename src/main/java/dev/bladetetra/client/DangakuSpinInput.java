package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.combat.DangakuSpinHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Holding attack stays in native input sync, but does not mine blocks or add vanilla hits mid-spin. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class DangakuSpinInput {
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && DangakuSpinHandler.ownsCombo(player, player.getMainHandItem())) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
    private DangakuSpinInput() {}
}
