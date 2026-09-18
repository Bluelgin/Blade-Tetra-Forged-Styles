package dev.bladetetra.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.RaikiriVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static dev.bladetetra.client.vfx.render.VfxPrimitives.bandFacing;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.billboard;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.color;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.texturedRibbon;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.withAlpha;

/** Textured ribbons with a quality-gated procedural core for Raikiri's chain circuit. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RaikiriVfxClient {
    private static final ResourceLocation ARC_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/effect/raikiri/chain_arc.png");
    private static final ResourceLocation RING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/effect/raikiri/overload_ring.png");
    private static final ResourceLocation SPARK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/effect/raikiri/branch_spark.png");
    private static final List<ArcEffect> EFFECTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    public static void spawn(RaikiriVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            activeLevel = minecraft.level;
        }
        double maximum = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
        if (minecraft.player != null
                && minecraft.player.distanceToSqr(packet.startX(), packet.startY(),
                        packet.startZ()) > maximum * maximum) return;
        EFFECTS.add(new ArcEffect(packet));
        int cap = Math.max(12, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        while (EFFECTS.size() > cap) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            activeLevel = minecraft.level;
        }
        Iterator<ArcEffect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            ArcEffect effect = iterator.next();
            if (++effect.age >= effect.duration) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || EFFECTS.isEmpty()
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);
        Matrix4f matrix = poses.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        float global = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        renderArcs(matrix, camera.getPosition(), event.getPartialTick(), global);
        if (ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get() >= 1) {
            renderProceduralCores(matrix, camera.getPosition(), event.getPartialTick(), global,
                    ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get());
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        renderImpactSprites(matrix, camera.getPosition(), event.getPartialTick(), global);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void renderArcs(Matrix4f matrix, Vec3 camera,
            float partialTick, float global) {
        RenderSystem.setShaderTexture(0, ARC_TEXTURE);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (ArcEffect effect : EFFECTS) {
            float age = effect.age + partialTick;
            float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
            float fade = Mth.clamp(1.0F - t, 0.0F, 1.0F);
            float pulse = 0.78F + 0.22F * Mth.sin((age + effect.seed * 0.07F) * 3.7F);
            float alpha = fade * pulse * global;
            Vec3 direction = effect.end.subtract(effect.start);
            if (direction.lengthSqr() < 0.0001D) continue;
            Vec3 center = effect.start.add(effect.end).scale(0.5D);
            Vec3 forward = direction.normalize();
            Vec3 view = camera.subtract(center).normalize();
            Vec3 faceWidth = forward.cross(view);
            if (faceWidth.lengthSqr() < 0.001D) faceWidth = forward.cross(new Vec3(0, 1, 0));
            if (faceWidth.lengthSqr() < 0.001D) faceWidth = new Vec3(1, 0, 0);
            faceWidth = faceWidth.normalize();
            Vec3 crossWidth = forward.cross(faceWidth).normalize();
            double width = (effect.overload ? 0.48D : 0.30D)
                    * (0.82D + 0.18D * pulse) * global;
            texturedRibbon(buffer, matrix, effect.start, effect.end,
                    faceWidth.scale(width), alpha, effect.color);
            texturedRibbon(buffer, matrix, effect.start, effect.end,
                    crossWidth.scale(width * 0.82D), alpha * 0.58F, effect.color);
            if (effect.overload) {
                Vec3 diagonal = faceWidth.add(crossWidth).normalize();
                texturedRibbon(buffer, matrix, effect.start, effect.end,
                        diagonal.scale(width * 0.68D), alpha * 0.36F, effect.color);
            }
        }
        Tesselator.getInstance().end();
    }

    private static void renderProceduralCores(Matrix4f matrix, Vec3 camera,
            float partialTick, float global, int quality) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (ArcEffect effect : EFFECTS) {
            float age = effect.age + partialTick;
            float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
            float fade = Mth.clamp(1.0F - t, 0.0F, 1.0F);
            float pulse = 0.82F + 0.18F * Mth.sin((age + effect.seed * 0.11F) * 4.6F);
            Vec3 direction = effect.end.subtract(effect.start);
            if (direction.lengthSqr() < 0.0001D) continue;

            Vec3 forward = direction.normalize();
            Vec3 side = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side.lengthSqr() < 0.001D) side = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
            side = side.normalize();
            Vec3 up = side.cross(forward).normalize();
            int segments = quality >= 2 ? (effect.overload ? 12 : 9) : (effect.overload ? 8 : 6);
            double jitter = (effect.overload ? 0.22D : 0.13D) * Math.min(1.25D, global);
            double outerWidth = (effect.overload ? 0.055D : 0.040D) * global;
            double coreWidth = (effect.overload ? 0.019D : 0.014D) * global;
            int outer = withAlpha(effect.color, fade * pulse * global * 0.64F);
            int core = color(0.92F, 0.98F, 1.0F, fade * pulse * global * 0.96F);

            Vec3 previous = effect.start;
            for (int index = 1; index <= segments; index++) {
                double progress = index / (double) segments;
                Vec3 point = effect.start.lerp(effect.end, progress);
                if (index < segments) {
                    double envelope = Math.sin(progress * Math.PI);
                    double phase = effect.seed * 0.173D + index * 2.399D + age * 0.47D;
                    point = point.add(side.scale(Math.sin(phase) * jitter * envelope))
                            .add(up.scale(Math.cos(phase * 1.37D) * jitter * 0.68D * envelope));
                }
                bandFacing(buffer, matrix, previous, point, camera, outerWidth, outer);
                bandFacing(buffer, matrix, previous, point, camera, coreWidth, core);

                if (quality >= 2 && effect.overload && index < segments && index % 3 == 0) {
                    double branchPhase = effect.seed * 0.29D + index * 1.73D;
                    Vec3 branchDirection = side.scale(Math.sin(branchPhase))
                            .add(up.scale(0.45D + 0.35D * Math.cos(branchPhase)))
                            .normalize();
                    Vec3 branchEnd = point.add(branchDirection.scale(0.32D + 0.06D * (index % 2)));
                    bandFacing(buffer, matrix, point, branchEnd, camera,
                            outerWidth * 0.58D, withAlpha(effect.color, fade * global * 0.42F));
                }
                previous = point;
            }
        }
        Tesselator.getInstance().end();
    }

    private static void renderImpactSprites(Matrix4f matrix, Vec3 camera,
            float partialTick, float global) {
        for (ArcEffect effect : EFFECTS) {
            float age = effect.age + partialTick;
            float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
            float fade = (1.0F - t) * global;
            RenderSystem.setShaderTexture(0, SPARK_TEXTURE);
            int sparks = effect.overload ? 3 : 1;
            for (int index = 0; index < sparks; index++) {
                double progress = (index + 1.0D) / (sparks + 1.0D);
                Vec3 center = effect.start.lerp(effect.end, progress);
                double wobble = Math.sin(effect.seed * 0.17D + index * 2.1D) * 0.18D;
                center = center.add(0.0D, wobble, 0.0D);
                billboard(matrix, camera, center,
                        (effect.overload ? 0.52D : 0.34D) * (1.0D + t * 0.18D),
                        fade * (0.82F - index * 0.10F), effect.color);
            }
            if (effect.overload) {
                RenderSystem.setShaderTexture(0, RING_TEXTURE);
                billboard(matrix, camera, effect.end,
                        (0.82D + t * 0.72D) * global, fade * 0.92F,
                        effect.color);
            }
        }
    }

    private static final class ArcEffect {
        final Vec3 start;
        final Vec3 end;
        final boolean overload;
        final int color;
        final int seed;
        final int duration;
        int age;

        ArcEffect(RaikiriVfxPacket packet) {
            start = new Vec3(packet.startX(), packet.startY(), packet.startZ());
            end = new Vec3(packet.endX(), packet.endY(), packet.endZ());
            overload = packet.overload();
            color = packet.color();
            seed = packet.seed();
            duration = overload ? 11 : 7;
        }
    }

    private RaikiriVfxClient() {
    }
}
