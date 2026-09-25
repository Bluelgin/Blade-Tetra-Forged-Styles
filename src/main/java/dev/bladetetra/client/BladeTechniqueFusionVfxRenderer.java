package dev.bladetetra.client;

import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import static dev.bladetetra.client.BladeTechniqueVfxClient.*;
import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;

/** Twin Fox, Twin Phase and Akatsuki technique render families. */
final class BladeTechniqueFusionVfxRenderer {
    static void drawTwinFoxMoonhunt(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        // Short trailing wisps, not full-length luminous tubes.
        int steps = segments(10, quality);
        for (int sign : new int[]{1, -1}) {
            for (int i = 0; i < steps; i++) {
                double a = Math.max(0, t - .16 + .16 * i / steps);
                double b = Math.max(0, t - .16 + .16 * (i + 1) / steps);
                float alpha = (float) (i + 1) / steps * .55F;
                int tint = sign > 0 ? color(1, .94F, .84F, alpha)
                        : color(.85F, .035F, .12F, alpha);
                line(buffer, matrix, camera,
                        TwinFoxModelRenderer.path(effect.start, effect.end, a, sign),
                        TwinFoxModelRenderer.path(effect.start, effect.end, b, sign),
                        .035D * strength * (i + 1) / steps, tint);
            }
        }
    }

    static void renderTwinFoxModels(PoseStack poses, float partialTick, float global) {
        if (EFFECTS.stream().noneMatch(e -> e.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS)) return;
        MultiBufferSource.BufferSource buffers = MODEL_BUFFERS;
        try {
            for (Effect effect : EFFECTS) {
                boolean impact = effect.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT;
                float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0, 1);
                if (effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                        || effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS) {
                    TwinFoxModelRenderer.drawPursuit(poses, buffers, effect.start,
                            effect.initialEnd, effect.end, t, global,
                            effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS);
                    continue;
                }
                if (!impact && effect.type != BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT) continue;
                TwinFoxModelRenderer.draw(poses, buffers, effect.start, effect.end, t,
                        global, impact, effect.intensity > 1);
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    static void renderTwinPhaseModels(PoseStack poses, float partialTick,
            float global) {
        if (EFFECTS.stream().noneMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_YASHA
                        || effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU)) return;
        MultiBufferSource.BufferSource buffers = MODEL_BUFFERS;
        try {
            for (Effect effect : EFFECTS) {
                float t = Mth.clamp((effect.age + partialTick) / effect.duration,
                        0.0F, 1.0F);
                if (effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_YASHA) {
                    TwinPhaseModelRenderer.drawYasha(poses, buffers, effect.start,
                            effect.end, t, global * effect.intensity, effect.seed);
                } else if (effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU) {
                    TwinPhaseModelRenderer.drawKikouku(poses, buffers, effect.end,
                            t, global * effect.intensity, effect.seed);
                }
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    static void renderTwinPhaseNativeWinds(PoseStack poses,
            float partialTick, float global, int quality) {
        if (EFFECTS.stream().noneMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU)) return;
        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_DIM_MODEL);
        MultiBufferSource.BufferSource buffers = MODEL_BUFFERS;
        try {
            for (Effect effect : EFFECTS) {
                if (effect.type != BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU) continue;
                float age = effect.age + partialTick;
                float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
                float appear = Mth.clamp(t / 0.16F, 0.0F, 1.0F);
                float fade = Mth.clamp((1.0F - t) / 0.20F, 0.0F, 1.0F);
                float visibility = appear * fade;
                int copies = quality == 0 ? 2 : quality == 1 ? 3 : 4;
                float scale = 0.0105F * global * effect.intensity
                        * (0.78F + t * 0.72F);
                poses.pushPose();
                poses.translate(effect.end.x, effect.end.y + 0.72D, effect.end.z);
                poses.mulPose(Axis.YP.rotationDegrees(
                        Math.floorMod(effect.seed, 360) + age * 4.8F));
                poses.scale(scale, scale, scale);
                for (int index = 0; index < copies; index++) {
                    poses.pushPose();
                    poses.mulPose(Axis.YP.rotationDegrees(360.0F / copies * index));
                    poses.mulPose(Axis.XP.rotationDegrees(58.0F + index * 17.0F));
                    poses.mulPose(Axis.ZP.rotationDegrees(
                            (index % 2 == 0 ? 1.0F : -1.0F) * age * 13.0F));
                    float pulse = 0.72F + 0.28F * Mth.sin(
                            t * Mth.PI + index * 0.7F);
                    poses.scale(pulse, pulse, pulse);
                    int alpha = Mth.clamp((int) (visibility * 205.0F), 0, 255);
                    int rgb = index % 3 == 1 ? 0xF2D6EA : 0xA51E5B;
                    BladeRenderState.setCol((alpha << 24) | rgb, false);
                    BladeRenderState.renderOverridedColorWrite(ItemStack.EMPTY,
                            model, "wind", SLASH_DIM_TEXTURE, poses, buffers,
                            LightTexture.FULL_BRIGHT);
                    poses.popPose();
                }
                poses.popPose();
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    static void drawTwinFoxMoonhuntImpact(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        Vec3 forward = flatDirection(effect.start, center, effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        float fade = (1.0F - t) * (1.0F - t);
        boolean complete = effect.intensity > 1.0F;
        double radius = (0.55D + t * (complete ? 3.8D : 2.2D)) * strength;
        int white = color(1.0F, 0.96F, 0.90F, 0.92F * fade);
        int crimson = color(0.98F, 0.025F, 0.14F, 0.88F * fade);
        ringVertical(buffer, matrix, center, side, radius,
                0.075D + 0.06D * fade, complete ? crimson : white,
                segments(44, quality));
        line(buffer, matrix, camera,
                center.subtract(side.scale(radius)).add(0.0D, -radius * 0.42D, 0.0D),
                center.add(side.scale(radius)).add(0.0D, radius * 0.42D, 0.0D),
                (complete ? 0.15D : 0.09D) * fade, white);
        if (complete) {
            line(buffer, matrix, camera,
                    center.subtract(side.scale(radius)).add(0.0D, radius * 0.42D, 0.0D),
                    center.add(side.scale(radius)).add(0.0D, -radius * 0.42D, 0.0D),
                    0.17D * fade, crimson);
            arcHorizontal(buffer, matrix, camera, center.add(0.0D, -0.62D, 0.0D),
                    radius * 0.78D, 0.0D, Math.PI * 2.0D,
                    0.055D, crimson, segments(40, quality));
        }
    }

    static void drawAkatsukiFinalMoon(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        Vec3 facing = flatDirection(effect.start, center, effect.yaw);
        Vec3 side = new Vec3(-facing.z, 0.0D, facing.x).normalize();
        double scale = Mth.clamp(effect.intensity, 0.75F, 2.25F);
        Vec3 moonCenter = center.subtract(facing.scale(0.48D * scale))
                .add(0.0D, 0.30D * scale, 0.0D);
        int moon = withAlpha(0xFFBD1838, 0.34F + 0.22F * Mth.sin(t * 16.0F));
        int core = withAlpha(0xFFFFE4D0, 0.82F);
        arcVertical(buffer, matrix, camera, moonCenter, facing, side,
                1.45D * scale, -Math.PI * 0.82D, Math.PI * 0.82D,
                0.10D * scale, moon, segments(44, quality));
        arcVertical(buffer, matrix, camera, moonCenter, facing, side,
                1.18D * scale, -Math.PI * 0.72D, Math.PI * 0.72D,
                0.035D * scale, core, segments(36, quality));
    }

    static void renderAkatsukiJudgementWinds(PoseStack poses,
            float partialTick, float global, int quality) {
        boolean present = EFFECTS.stream().anyMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                        || effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END);
        if (!present) return;

        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_DIM_MODEL);
        MultiBufferSource.BufferSource buffers = MODEL_BUFFERS;
        for (Effect effect : EFFECTS) {
            if (effect.type != BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                    && effect.type != BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END) continue;
            renderAkatsukiJudgementWind(poses, buffers, model, effect,
                    partialTick, global, quality);
        }
        buffers.endBatch();
        BladeRenderState.resetCol();
    }

    static void renderAkatsukiJudgementWind(PoseStack poses,
            MultiBufferSource buffers, WavefrontObject model, Effect effect,
            float partialTick, float global, int quality) {
        float age = effect.age + partialTick;
        boolean ending = effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END;
        float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
        float visibility = ending
                ? (1.0F - t) * (1.0F - t)
                : Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        if (visibility <= 0.002F) return;

        int copies = quality == 0 ? 3 : quality == 1 ? 5 : 7;
        float targetScale = Mth.clamp(effect.intensity, 0.85F, 2.10F);
        float endExpansion = ending ? 1.0F + t * 0.62F : 1.0F;
        float baseScale = 0.0135F * targetScale * global * endExpansion;
        int seedAngle = Math.floorMod(effect.seed, 360);

        poses.pushPose();
        poses.translate(effect.end.x, effect.end.y, effect.end.z);
        poses.mulPose(Axis.YP.rotationDegrees(-effect.yaw + 90.0F));
        poses.mulPose(Axis.YP.rotationDegrees(seedAngle));
        poses.scale(baseScale, baseScale, baseScale);
        for (int index = 0; index < copies; index++) {
            float cycleOffset = 15.0F / copies * index;
            float cycle = Mth.positiveModulo(age + cycleOffset, 15.0F) / 15.0F;
            float sweepScale = 0.48F + cycle * 0.98F;
            float pulse = Mth.sin(cycle * Mth.PI);
            float alpha = Mth.clamp(pulse * visibility * 0.88F, 0.0F, 1.0F);
            if (alpha <= 0.01F) continue;

            poses.pushPose();
            poses.mulPose(Axis.XP.rotationDegrees(360.0F / copies * index));
            poses.mulPose(Axis.YP.rotationDegrees(24.0F + index * 3.5F));
            poses.scale(sweepScale, sweepScale, sweepScale);
            poses.mulPose(Axis.ZP.rotationDegrees(
                    18.0F * (age + seedAngle * 0.13F + cycleOffset)));
            int red = ending && index % 4 == 0 ? 0xFFF0DF : 0xFF2038;
            BladeRenderState.setCol((Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24)
                    | red);
            BladeRenderState.renderOverridedColorWrite(ItemStack.EMPTY, model, "wind",
                    SLASH_DIM_TEXTURE, poses, buffers, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
        poses.popPose();
    }

    static void drawAkatsukiFinalMoonEnd(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        float fade = (1.0F - t) * (1.0F - t);
        int red = withAlpha(0xFFFF2038, fade * 0.94F);
        int white = withAlpha(0xFFFFF0DF, fade);
        int cuts = quality == 0 ? 10 : quality == 1 ? 16 : 22;
        for (int index = 0; index < cuts; index++) {
            Vec3 direction = deterministicDirection(effect.seed + 73, index);
            double length = (1.4D + t * 2.8D + index % 4 * 0.16D) * effect.intensity;
            Vec3 offset = direction.scale(t * 0.55D);
            line(buffer, matrix, camera,
                    center.add(offset).subtract(direction.scale(length * 0.5D)),
                    center.add(offset).add(direction.scale(length * 0.5D)),
                    0.025D + fade * 0.025D, index % 5 == 0 ? white : red);
        }
        arcHorizontal(buffer, matrix, camera, center.add(0.0D, -0.45D, 0.0D),
                (0.35D + t * 3.2D) * effect.intensity, 0.0D, Math.PI * 2.0D,
                0.045D, red, segments(42, quality));
    }

    private BladeTechniqueFusionVfxRenderer() {
    }
}
