package dev.bladetetra.client;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Short, client-only impact hold used by prepared Iaido hits. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IaidoImpactClient {
    private static int remainingTicks;
    private static int totalTicks;
    private static float strength;

    public static void trigger(int tier) {
        if (!ClientVisualConfig.ENABLE_IAIDO_IMPACT_FEEDBACK.get() || tier < 2) {
            return;
        }
        int duration = tier >= 3 ? 3 : 2;
        if (duration >= remainingTicks) {
            remainingTicks = duration;
            totalTicks = duration;
            strength = tier >= 3 ? 1.0F : 0.72F;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && remainingTicks > 0) {
            remainingTicks--;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (remainingTicks <= 0) {
            return;
        }
        float progress = (remainingTicks + (float) event.getPartialTick())
                / Math.max(1.0F, totalTicks);
        float amplitude = strength
                * ClientVisualConfig.IAIDO_CAMERA_IMPACT_INTENSITY.get().floatValue()
                * progress;
        float phase = (totalTicks - remainingTicks + (float) event.getPartialTick()) * 4.7F;
        event.setYaw(event.getYaw() + (float) Math.sin(phase) * amplitude * 0.55F);
        event.setPitch(event.getPitch() - amplitude * 0.42F);
        event.setRoll(event.getRoll() + (float) Math.cos(phase * 0.8F) * amplitude * 0.85F);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (remainingTicks <= 0) {
            return;
        }
        float progress = (remainingTicks + event.getPartialTick())
                / Math.max(1.0F, totalTicks);
        int alpha = Math.min(38, Math.round(
                38.0F
                        * strength
                        * progress
                        * ClientVisualConfig.IAIDO_FLASH_INTENSITY.get().floatValue()));
        if (alpha <= 0) {
            return;
        }
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        event.getGuiGraphics().fill(0, 0, width, height, alpha << 24 | 0xEAF4FF);
    }

    private IaidoImpactClient() {
    }
}
