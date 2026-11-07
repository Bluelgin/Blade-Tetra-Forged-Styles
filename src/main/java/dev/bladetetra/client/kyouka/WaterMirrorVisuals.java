package dev.bladetetra.client.kyouka;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Shared water film drawn from geometry, with no model or skin lookup. */
public final class WaterMirrorVisuals {
    public static final ResourceLocation WATER =
            ResourceLocation.fromNamespaceAndPath("blade_tetra", "textures/effect/kyouka/water_film.png");
    public static final RenderType FILM = RenderType.entityTranslucent(WATER);
    private static final float TAU = (float) (2 * Math.PI);

    public static void armor(PoseStack pose, MultiBufferSource buffers, float radius, float height,
            float age, float opacity, float impact, float impactAngle) {
        var out = buffers.getBuffer(FILM);
        float drift = age * .045F;
        for (int i = 0; i < 3; i++) {
            float angle = drift + i * TAU / 3;
            ribbon(out, pose, angle, 1.65F, radius * 1.08F, radius * .9F,
                    height * .12F, height * .86F, age, opacity);
        }
        if (impact > 0) {
            float expanded = radius * (1.05F + .75F * impact);
            float y = height * (.62F - .24F * impact);
            ribbon(out, pose, impactAngle - 2 * impact, 1.35F + 4 * impact,
                    expanded, expanded, y, y + height * .12F, age, opacity * 2.2F * (1 - impact));
        }
    }

    public static void echo(PoseStack pose, MultiBufferSource buffers, float age, float opacity, boolean active) {
        var out = buffers.getBuffer(FILM);
        float drift = Mth.sin(age * .12F) * .2F;
        for (int i = 0; i < 2; i++) {
            float angle = drift + i * Mth.PI;
            ribbon(out, pose, angle, 2.9F, .34F, .41F, .12F, 1.39F, age, opacity);
            ribbon(out, pose, angle, 2.9F, .41F, .16F, 1.39F, 1.56F, age, opacity);
            ribbon(out, pose, angle, 2.9F, .18F, .12F, 1.55F, 1.86F, age, opacity);
        }
        if (active) {
            float radius = .62F + .05F * Mth.sin(age * .19F);
            for (int i = 0; i < 20; i++) {
                float a = TAU * i / 20;
                float b = TAU * (i + 1) / 20;
                vertex(out, pose, Mth.cos(a) * radius, .035F, Mth.sin(a) * radius, 0, 0, 0);
                vertex(out, pose, Mth.cos(b) * radius, .035F, Mth.sin(b) * radius, 1, 0, 0);
                vertex(out, pose, Mth.cos(b) * (radius - .11F), .035F, Mth.sin(b) * (radius - .11F), 1, 1, opacity);
                vertex(out, pose, Mth.cos(a) * (radius - .11F), .035F, Mth.sin(a) * (radius - .11F), 0, 1, opacity);
            }
        }
    }

    private static void ribbon(VertexConsumer out, PoseStack pose, float start, float arc,
            float baseRadius, float topRadius, float bottom, float top, float age, float opacity) {
        for (int i = 0; i < 6; i++) {
            float u = i / 6F;
            float v = (i + 1) / 6F;
            float a = start + arc * u;
            float b = start + arc * v;
            float topA = top + .035F * Mth.sin(a * 3 - age * .2F);
            float topB = top + .035F * Mth.sin(b * 3 - age * .2F);
            float alphaA = opacity * Mth.sin(u * Mth.PI);
            float alphaB = opacity * Mth.sin(v * Mth.PI);
            vertex(out, pose, Mth.cos(a) * baseRadius, bottom, Mth.sin(a) * baseRadius, u, 1, alphaA * .52F);
            vertex(out, pose, Mth.cos(b) * baseRadius, bottom, Mth.sin(b) * baseRadius, v, 1, alphaB * .52F);
            vertex(out, pose, Mth.cos(b + .18F) * topRadius, topB, Mth.sin(b + .18F) * topRadius, v, 0, alphaB);
            vertex(out, pose, Mth.cos(a + .18F) * topRadius, topA, Mth.sin(a + .18F) * topRadius, u, 0, alphaA);
        }
    }

    public static void surface(PoseStack pose, MultiBufferSource buffers, float fade) {
        var out = buffers.getBuffer(FILM);
        float alpha = .35F * fade;
        vertex(out, pose, -.7F, .1F, 0, 0, 1, alpha);
        vertex(out, pose, .7F, .1F, 0, 1, 1, alpha);
        vertex(out, pose, .7F, 2.5F, 0, 1, 0, alpha);
        vertex(out, pose, -.7F, 2.5F, 0, 0, 0, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack pose,
            float x, float y, float z, float u, float v, float alpha) {
        out.vertex(pose.last().pose(), x, y, z).color(.65F, .88F, 1F, Mth.clamp(alpha, 0, 1))
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.last().normal(), 0, 0, 1).endVertex();
    }

    private WaterMirrorVisuals() {}
}
