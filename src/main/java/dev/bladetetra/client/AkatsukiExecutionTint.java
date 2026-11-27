package dev.bladetetra.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/** Client-local grading for Akatsuki's execution. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AkatsukiExecutionTint {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation POST_EFFECT =
            new ResourceLocation(BladeTetra.MOD_ID, "shaders/post/blood_moon_domain.json");
    private static final float FADE_IN_PER_TICK = 1.0F / 70.0F;
    private static final float FADE_OUT_PER_TICK = 1.0F / 28.0F;

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    private static boolean loadFailed;
    private static int akatsukiTicks;
    private static float intensity;
    private static float previousIntensity;
    private static float time;

    public static void beginAkatsuki(int duration) {
        akatsukiTicks = Math.max(akatsukiTicks, Math.max(1, duration));
    }

    public static void endAkatsuki(int fadeTicks) {
        akatsukiTicks = Math.min(akatsukiTicks, Math.max(0, fadeTicks));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        previousIntensity = intensity;
        if (akatsukiTicks > 0) akatsukiTicks--;
        boolean validAkatsuki = akatsukiTicks > 0
                && ClientVisualConfig.ENABLE_AKATSUKI_EXECUTION_TINT.get()
                && minecraft.level != null && minecraft.player != null;
        float target = validAkatsuki ? 1.0F : 0.0F;
        intensity = target > intensity
                ? Math.min(target, intensity + FADE_IN_PER_TICK)
                : Math.max(target, intensity - FADE_OUT_PER_TICK);
        if (intensity > 0.001F) time += 0.05F;
        if (minecraft.level == null || minecraft.player == null) {
            akatsukiTicks = 0;
            intensity = previousIntensity = 0.0F;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        float partial = event.getPartialTick();
        float configured = ClientVisualConfig.AKATSUKI_EXECUTION_TINT_INTENSITY.get().floatValue();
        float strength = Mth.lerp(partial, previousIntensity, intensity) * configured;
        if (strength <= 0.001F || !ensureChain()) return;

        try {
            updateUniforms(strength, time + partial * 0.05F);
            chain.process(partial);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } catch (RuntimeException exception) {
            LOGGER.warn("Disabling Akatsuki execution post effect after a render failure", exception);
            closeChain();
            loadFailed = true;
        }
    }

    private static boolean ensureChain() {
        if (loadFailed) return false;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(minecraft.getTextureManager(),
                        minecraft.getResourceManager(), target, POST_EFFECT);
                chainWidth = target.width;
                chainHeight = target.height;
                chain.resize(chainWidth, chainHeight);
            } else if (target.width != chainWidth || target.height != chainHeight) {
                chainWidth = target.width;
                chainHeight = target.height;
                chain.resize(chainWidth, chainHeight);
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load Akatsuki execution post effect; world VFX will remain enabled", exception);
            closeChain();
            loadFailed = true;
            return false;
        }
    }

    private static void updateUniforms(float strength, float animationTime) {
        List<PostPass> passes = ObfuscationReflectionHelper.getPrivateValue(
                PostChain.class, chain, "f_110009_");
        if (passes == null) return;
        for (PostPass pass : passes) {
            EffectInstance effect = pass.getEffect();
            if (effect.getUniform("DomainIntensity") != null) {
                effect.getUniform("DomainIntensity").set(strength);
            }
            if (effect.getUniform("DomainTime") != null) {
                effect.getUniform("DomainTime").set(animationTime);
            }
        }
    }

    private static void reload() {
        closeChain();
        loadFailed = false;
    }

    private static void closeChain() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        chainWidth = chainHeight = -1;
    }

    private AkatsukiExecutionTint() {
    }

    @Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class AkatsukiTintReloadRegistration {
        private AkatsukiTintReloadRegistration() {
        }

        @SubscribeEvent
        public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> reload());
        }
    }
}
