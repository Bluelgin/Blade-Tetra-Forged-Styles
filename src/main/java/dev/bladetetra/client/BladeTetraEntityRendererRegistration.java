package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * One stable owner for all Blade Tetra entity renderers.
 *
 * <p>Forge EventBus 6.0.5's ModLauncherFactory derives generated listener class
 * names from the declaring class's simple name, method name and event type's
 * simple name. Separate nested classes named {@code Registration} can therefore
 * alias one another. Keep entity renderer registration in this uniquely named
 * top-level subscriber.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BladeTetraEntityRendererRegistration {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MIKAGE.get(), MikageRenderer::new);
        event.registerEntityRenderer(ModEntities.MIKAGE_PHANTOM_SWORD.get(),
                MikagePhantomSwordRenderer::new);
        event.registerEntityRenderer(ModEntities.MIKAGE_ECHO.get(), MikageEchoRenderer::new);
        event.registerEntityRenderer(ModEntities.MIKAGE_GATE_CORE.get(), MikageGateCoreRenderer::new);
        event.registerEntityRenderer(ModEntities.MIKAGE_GATE_SWORD.get(),
                context -> new mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer<>(context));
        event.registerEntityRenderer(ModEntities.MIKAGE_DIVINE_COMPANION.get(),
                MikageDivineCompanionRenderer::new);
        event.registerEntityRenderer(ModEntities.KYOUKA_AFTERIMAGE.get(),
                KyoukaAfterimageRenderer::new);
    }

    private BladeTetraEntityRendererRegistration() {
    }
}
