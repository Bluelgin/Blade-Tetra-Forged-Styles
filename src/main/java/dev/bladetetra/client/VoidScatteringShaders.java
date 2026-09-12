package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;

/** Optional local shaders used by Void Scattering world-space and first-person effects. */
public final class VoidScatteringShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance riftShader;
    private static ShaderInstance domeShader;
    private static ShaderInstance veilShader;

    public static ShaderInstance riftShader() {
        return riftShader;
    }

    public static ShaderInstance domeShader() {
        return domeShader;
    }

    public static ShaderInstance veilShader() {
        return veilShader;
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            riftShader = null;
            domeShader = null;
            veilShader = null;
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                                ResourceLocation.fromNamespaceAndPath(
                                        BladeTetra.MOD_ID, "void_scattering_rift"),
                                DefaultVertexFormat.POSITION_TEX_COLOR),
                        shader -> riftShader = shader);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not load Void Scattering rift shader; "
                        + "falling back to geometry VFX", exception);
            }
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                                ResourceLocation.fromNamespaceAndPath(
                                        BladeTetra.MOD_ID, "void_scattering_dome"),
                                DefaultVertexFormat.POSITION_TEX_COLOR),
                        shader -> domeShader = shader);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not load Void Scattering dome shader; "
                        + "falling back to geometry VFX", exception);
            }
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                                ResourceLocation.fromNamespaceAndPath(
                                        BladeTetra.MOD_ID, "void_scattering_veil"),
                                DefaultVertexFormat.POSITION_TEX_COLOR),
                        shader -> veilShader = shader);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not load Void Scattering first-person veil shader; "
                        + "falling back to HUD edge veil", exception);
            }
        }
    }

    private VoidScatteringShaders() {
    }
}
