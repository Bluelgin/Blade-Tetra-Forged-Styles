package dev.bladetetra.client.vfx;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.util.List;

/** Optional owned post-chain; never installs/replaces GameRenderer's or a shader pack's chain. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class DeadThoughtAtmosphere {
    private static final ResourceLocation EFFECT = new ResourceLocation("blade_tetra", "shaders/post/dead_thought.json");
    private static PostChain chain;
    private static int width, height;
    private static boolean failed;

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL
                || !ClientVisualConfig.ENABLE_DEAD_THOUGHT_GRADING.get()
                || ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get() == 0 || failed) return;
        float strength = DeadThoughtVfxClient.atmosphere(event.getPartialTick());
        if (strength < .001F) return;
        Minecraft mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target, EFFECT);
                width = height = -1;
            }
            if (width != target.width || height != target.height) {
                width = target.width; height = target.height; chain.resize(width, height);
            }
            List<PostPass> passes = ObfuscationReflectionHelper.getPrivateValue(PostChain.class, chain, "f_110009_");
            if (passes != null) for (PostPass pass : passes) {
                var uniform = pass.getEffect().getUniform("Intensity");
                if (uniform != null) uniform.set(strength);
            }
            chain.process(event.getPartialTick());
        } catch (IOException | RuntimeException exception) {
            close(); failed = true;
            LogUtils.getLogger().warn("Dead Thought grading disabled; geometry is unaffected", exception);
        } finally {
            target.bindWrite(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().level == null) close();
    }

    static void reload() { close(); failed = false; }
    private static void close() { if (chain != null) { chain.close(); chain = null; } }
    private DeadThoughtAtmosphere() {}
}
