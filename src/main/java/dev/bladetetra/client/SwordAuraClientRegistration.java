package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SwordAuraClientRegistration {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SWORD_AURA_DRIVE.get(), SwordAuraRenderer::new);
    }

    private SwordAuraClientRegistration() {}
}
