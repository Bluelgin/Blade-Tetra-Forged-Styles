package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Blockbench-authored volumetric blade-light components for Twin Phase: Kikouku. */
final class TwinPhaseModelRenderer {
    private static final ResourceLocation MODEL = new ResourceLocation(
            "blade_tetra", "models/effect/twin_phase/twin_phase.obj");
    private static final ResourceLocation LIGHT = new ResourceLocation(
            "blade_tetra", "textures/effect/twin_phase/light.png");
    private static final ResourceLocation SHADOW = new ResourceLocation(
            "blade_tetra", "textures/effect/twin_phase/shadow.png");

    private TwinPhaseModelRenderer() {}

    static void drawYasha(PoseStack poses, MultiBufferSource buffers,
            Vec3 source, Vec3 target, float t, float strength, int seed) {
        Vec3 facing = target.subtract(source).multiply(1.0D, 0.0D, 1.0D);
        if (facing.lengthSqr() < 0.0001D) facing = new Vec3(0.0D, 0.0D, 1.0D);
        float yaw = (float) Math.atan2(-facing.x, -facing.z);
        for (int index = 0; index < 5; index++) {
            float delay = index * 0.075F;
            float local = Mth.clamp((t - delay) / 0.58F, 0.0F, 1.0F);
            float envelope = Mth.sin(local * Mth.PI);
            if (envelope <= 0.001F) continue;
            float roll = -58.0F + index * 29.0F + (seed & 3) * 3.0F;
            double y = (index - 2) * 0.12D;
            part(poses, buffers, target.add(0.0D, y, 0.0D), yaw,
                    roll, 2.45F * strength * envelope,
                    "yasha_slash_arc", LIGHT, 0xDFFFFFFF, false);
        }
        if (t > 0.48F) {
            float local = Mth.clamp((t - 0.48F) / 0.48F, 0.0F, 1.0F);
            float envelope = Mth.sin(local * Mth.PI);
            part(poses, buffers, target.add(0.0D, 0.08D, 0.0D), yaw,
                    88.0F, 3.15F * strength * envelope,
                    "twin_phase_finisher", LIGHT, 0xE8FFFFFF, false);
        }
    }

    static void drawKikouku(PoseStack poses, MultiBufferSource buffers,
            Vec3 center, float t, float strength, int seed) {
        float appear = Mth.clamp(t / 0.18F, 0.0F, 1.0F);
        float fade = Mth.clamp((1.0F - t) / 0.22F, 0.0F, 1.0F);
        float envelope = appear * fade;
        for (int layer = 0; layer < 2; layer++) {
            float spin = (layer == 0 ? 1.0F : -1.0F)
                    * (t * 245.0F + (seed & 7) * 5.0F);
            float scale = (2.1F + t * (layer == 0 ? 4.2F : 3.5F))
                    * strength * envelope;
            part(poses, buffers, center.add(0.0D, 0.16D + layer * 0.24D, 0.0D),
                    0.0F, spin, scale, "kikoku_broken_ring", SHADOW,
                    layer == 0 ? 0xDCFFFFFF : 0xC8FFF0F7, true);
        }
        if (t > 0.58F) {
            float local = Mth.clamp((t - 0.58F) / 0.38F, 0.0F, 1.0F);
            float slash = Mth.sin(local * Mth.PI);
            for (int index = 0; index < 3; index++) {
                part(poses, buffers, center.add(0.0D, 0.48D + index * 0.28D, 0.0D),
                        (float) (index * Math.PI / 3.0D), 18.0F + index * 61.0F,
                        (2.5F + index * 0.25F) * strength * slash,
                        "twin_phase_finisher", index == 1 ? LIGHT : SHADOW,
                        index == 1 ? 0xE8FFFFFF : 0xD8FFFFFF, false);
            }
        }
    }

    private static void part(PoseStack poses, MultiBufferSource buffers,
            Vec3 position, float yaw, float roll, float scale, String group,
            ResourceLocation texture, int color, boolean horizontal) {
        if (scale <= 0.001F) return;
        poses.pushPose();
        try {
            poses.translate(position.x, position.y, position.z);
            poses.mulPose(Axis.YP.rotation(yaw));
            if (horizontal) poses.mulPose(Axis.XP.rotationDegrees(90.0F));
            poses.mulPose(Axis.ZP.rotationDegrees(roll));
            poses.scale(scale, scale, scale);
            BladeRenderState.setCol(color, false);
            BladeRenderState.renderOverrided(ItemStack.EMPTY,
                    BladeModelManager.getInstance().getModel(MODEL), group,
                    texture, poses, buffers, LightTexture.FULL_BRIGHT);
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }
}
