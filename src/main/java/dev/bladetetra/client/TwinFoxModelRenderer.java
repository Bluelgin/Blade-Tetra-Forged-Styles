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

/** Native Blockbench meshes. Cosmetic only; never creates damaging entities. */
final class TwinFoxModelRenderer {
    private static final ResourceLocation WHITE = texture("white");
    private static final ResourceLocation BLACK = texture("black");
    private static final ResourceLocation SPIRIT = model("spirit");
    private static final ResourceLocation CRESCENT = model("crescent");
    private static final ResourceLocation SEAL = model("seal");

    private TwinFoxModelRenderer() {}

    static Vec3 path(Vec3 start, Vec3 end, double t, int sign) {
        Vec3 delta = end.subtract(start);
        Vec3 side = new Vec3(-delta.z, 0, delta.x);
        side = side.lengthSqr() < .0001 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 control = start.add(end).scale(.5).add(0, 1.15, 0)
                .add(side.scale(Mth.clamp(delta.length() * .34, 2.2, 6) * sign));
        double inverse = 1 - t;
        return start.scale(inverse * inverse).add(control.scale(2 * inverse * t))
                .add(end.scale(t * t)).add(side.scale(sign * .58 * t))
                .add(0, (sign > 0 ? .22 : -.08) * t, 0);
    }

    static void draw(PoseStack poses, MultiBufferSource buffers, Vec3 start, Vec3 end,
                     float t, float size, boolean impact, boolean complete) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        float yaw = (float) Math.atan2(-direction.x, -direction.z);
        if (impact) {
            float scale = (1 - t) * (1 + t * 2.5F) * size;
            if (scale < .01F) return;
            for (int sign : new int[]{1, -1}) {
                if (!complete && sign < 0) continue;
                part(poses, buffers, end, yaw, sign * (50 + t * 160), scale * 1.6F,
                        CRESCENT, "crescent", sign < 0, false);
            }
            return;
        }
        part(poses, buffers, end.add(0, -.7, 0), 0, t * 90,
                size * (.7F + .2F * Mth.sin(t * Mth.PI)), SEAL, "seal", false, true);
        for (int sign : new int[]{1, -1}) {
            if (t < .28F) {
                float appear = Mth.sin(t / .28F * Mth.PI);
                part(poses, buffers, start.add(side.scale(sign * .7)), yaw, sign * 12,
                        size * .8F * appear, SPIRIT, "spirit", sign < 0, false);
            }
            Vec3 position = path(start, end, t, sign);
            Vec3 tangent = path(start, end, Math.min(1, t + .025), sign)
                    .subtract(path(start, end, Math.max(0, t - .025), sign));
            float heading = (float) Math.atan2(-tangent.x, -tangent.z);
            float scale = size * (.45F + .35F * Mth.sin(t * Mth.PI));
            part(poses, buffers, position, heading, sign * (25 + t * 290), scale,
                    CRESCENT, "crescent", sign < 0, false);
        }
    }

    static void drawPursuit(PoseStack poses, MultiBufferSource buffers, Vec3 originalAnchor,
            Vec3 originalCenter, Vec3 center, float t, float size, boolean crossing) {
        Vec3 direction = originalAnchor.subtract(originalCenter).multiply(1, 0, 1);
        direction = direction.lengthSqr() < .0001 ? new Vec3(0, 0, 1) : direction.normalize();
        double radius = Math.max(.85, originalAnchor.distanceTo(originalCenter));
        Vec3 whiteAnchor = center.add(direction.scale(radius));
        float yaw = (float) Math.atan2(direction.x, direction.z);
        if (!crossing) {
            float edge = Math.min(Mth.clamp(t / .10F, 0, 1),
                    Mth.clamp((1 - t) / .16F, 0, 1));
            float pulse = .82F + .08F * Mth.sin(t * 20);
            part(poses, buffers, whiteAnchor.add(0, .28 + .08 * Mth.sin(t * 14), 0),
                    yaw + Mth.PI, 0, size * edge * pulse, SPIRIT, "spirit", false, false);
            return;
        }
        Vec3 blackAnchor = center.subtract(direction.scale(radius));
        float approach = Mth.clamp(t / .72F, 0, 1);
        float eased = 1 - (1 - approach) * (1 - approach);
        Vec3 white = whiteAnchor.lerp(center.add(0, .1, 0), eased);
        Vec3 black = blackAnchor.lerp(center.add(0, -.05, 0), eased);
        float spiritFade = Mth.clamp((.72F - t) / .18F, 0, 1);
        part(poses, buffers, white, yaw + Mth.PI, t * -45, size * .78F * spiritFade,
                SPIRIT, "spirit", false, false);
        part(poses, buffers, black, yaw, t * 45, size * .82F * spiritFade,
                SPIRIT, "spirit", true, false);
        if (t > .48F) {
            float close = Mth.clamp((t - .48F) / .42F, 0, 1);
            float bladeScale = size * (.45F + .65F * Mth.sin(close * Mth.PI));
            part(poses, buffers, white, yaw, -65 + close * 175, bladeScale,
                    CRESCENT, "crescent", false, false);
            part(poses, buffers, black, yaw + Mth.PI, 65 - close * 175, bladeScale,
                    CRESCENT, "crescent", true, false);
        }
    }

    private static void part(PoseStack poses, MultiBufferSource buffers, Vec3 position,
                             float yaw, float roll, float scale, ResourceLocation model,
                             String group, boolean dark, boolean horizontal) {
        if (scale < .01F) return;
        poses.pushPose();
        try {
            poses.translate(position.x, position.y, position.z);
            poses.mulPose(Axis.YP.rotation(yaw));
            if (horizontal) poses.mulPose(Axis.XP.rotationDegrees(90));
            poses.mulPose(Axis.ZP.rotationDegrees(roll));
            // Blockbench's OBJ codec already converts its 16-unit grid to blocks.
            poses.scale(scale, scale, scale);
            BladeRenderState.resetCol();
            // Standard textured rendering preserves the dark fox's opaque silhouette.
            BladeRenderState.renderOverrided(ItemStack.EMPTY,
                    BladeModelManager.getInstance().getModel(model), group,
                    dark ? BLACK : WHITE, poses, buffers, LightTexture.FULL_BRIGHT);
        } finally {
            poses.popPose();
            BladeRenderState.resetCol();
        }
    }

    private static ResourceLocation model(String name) {
        return new ResourceLocation("blade_tetra", "models/effect/twin_fox/" + name + ".obj");
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("blade_tetra", "textures/effect/twin_fox/" + name + ".png");
    }
}
