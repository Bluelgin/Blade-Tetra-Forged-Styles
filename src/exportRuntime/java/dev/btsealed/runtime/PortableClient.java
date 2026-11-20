package dev.btsealed.runtime;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PortableBladeRuntime.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PortableClient {
    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(PortableProfiles.clientListener());
    }

    @Mod.EventBusSubscriber(modid = PortableBladeRuntime.ID, value = Dist.CLIENT)
    public static final class SpinInput {
        @SubscribeEvent
        public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
            var player = Minecraft.getInstance().player;
            if (event.isAttack() && player != null && PortableDangakuSpin.ownsCombo(player)) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    private PortableClient() {}
}
