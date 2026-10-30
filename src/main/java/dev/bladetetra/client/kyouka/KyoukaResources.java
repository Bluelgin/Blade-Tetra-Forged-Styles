package dev.bladetetra.client.kyouka;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KyoukaResources {
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> KyoukaMirrorRenderer.reload());
    }
    private KyoukaResources() {}
}
