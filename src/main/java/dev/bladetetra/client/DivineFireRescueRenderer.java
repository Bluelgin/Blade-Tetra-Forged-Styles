package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.challenge.DivineFireRescueState;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;

/** Reuses Mikage's actual volumetric flame tongues, rather than a new flat sprite. */
public final class DivineFireRescueRenderer {
    public static void draw(PoseStack poses, Vec3 at, float time, float alpha, int quality, Vec3 camera) {
        alpha *= ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();
        var matrix = poses.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int outer = BladeTechniqueVfxGeometry.color(.65F, .08F, .18F, alpha * .85F);
            int middle = BladeTechniqueVfxGeometry.color(1, .3F, .4F, alpha);
            int inner = BladeTechniqueVfxGeometry.color(1, .92F, .83F, alpha);
            // Keep the same protected footprint at every visual-quality setting.
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + time * .012;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
                Vec3 base = at.add(radial.scale(DivineFireRescueState.RADIUS)).add(0, .12, 0);
                BladeTechniqueMikageVfxRenderer.drawBoundaryFlameTongue(buffer, matrix, camera, base,
                        radial, tangent, .8 + Math.sin(time * .12 + i) * .12, time * .14 + i,
                        .6F, outer, middle, inner, quality <= 0, i);
            }
            BladeTechniqueVfxGeometry.ringHorizontal(buffer, matrix, at.add(0, .06, 0),
                    DivineFireRescueState.RADIUS, .035, middle, 48);
            tessellator.end();
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }
    private DivineFireRescueRenderer() {}
}
