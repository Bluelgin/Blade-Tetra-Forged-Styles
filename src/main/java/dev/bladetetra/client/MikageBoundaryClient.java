package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Shader-light, dependency-free arena VFX. Everything here is immediate client
 * geometry: no particle objects, block entities, world edits, or server spam.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageBoundaryClient {
    private static final ResourceLocation MIRROR_REALM =
            new ResourceLocation(BladeTetra.MOD_ID, "mirror_realm");
    private static final ResourceLocation BORDER_TEXTURE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/effect/mikage_boundary_border.png");
    private static final ResourceLocation SEAL_TEXTURE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/effect/mikage_boundary_seal.png");
    private static final ResourceLocation WALL_TEXTURE = new ResourceLocation(BladeTetra.MOD_ID,
            "textures/effect/mikage_boundary_wall.png");
    private static Boundary active;
    private static int age;
    private static int fadeOutTicks;

    public static void show(long challengeId, double minX, double maxX,
            double minZ, double maxZ, double floorY) {
        active = new Boundary(challengeId, minX, maxX, minZ, maxZ, floorY);
        age = 0;
        fadeOutTicks = -1;
    }

    public static void hide() {
        if (active != null && fadeOutTicks < 0) {
            fadeOutTicks = 24;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || active == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientVisualConfig.ENABLE_MIKAGE_BOUNDARY.get()
                || minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(MIRROR_REALM)) {
            active = null;
            return;
        }
        age++;
        if (fadeOutTicks >= 0 && fadeOutTicks-- <= 0) {
            active = null;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || active == null || !ClientVisualConfig.ENABLE_MIKAGE_BOUNDARY.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        double px = minecraft.player.getX();
        double pz = minecraft.player.getZ();
        double edgeDistance = active.edgeDistance(px, pz);
        float near = 1.0F - Mth.clamp((float) ((edgeDistance - 1.0D) / 9.0D), 0.0F, 1.0F);
        float appear = Mth.clamp((age + event.getPartialTick()) / 34.0F, 0.0F, 1.0F);
        appear = 1.0F - (1.0F - appear) * (1.0F - appear);
        if (fadeOutTicks >= 0) {
            appear *= Mth.clamp((fadeOutTicks + event.getPartialTick()) / 24.0F, 0.0F, 1.0F);
        }
        float brightness = ClientVisualConfig.MIKAGE_BOUNDARY_BRIGHTNESS.get().floatValue();
        float pulse = 0.88F + 0.12F * Mth.sin((age + event.getPartialTick()) * 0.055F);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder geometry = Tesselator.getInstance().getBuilder();
        geometry.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderGroundRing(poseStack, active, appear * brightness * pulse, near);
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** A restrained, floor-only arena mark aligned with the server's circular limit. */
    private static void renderGroundRing(PoseStack poseStack, Boundary b,
            float alpha, float near) {
        double cx = b.centerX();
        double cz = b.centerZ();
        double radius = b.radius();
        double y = b.floorY + 0.045D;
        int glow = rgba(alpha * (0.34F + near * 0.24F), 0.76F);
        int core = rgba(alpha * (0.76F + near * 0.24F), 1.0F);
        int inner = rgba(alpha * 0.32F, 0.62F);
        drawFloorCircle(poseStack, cx, y, cz, radius + 0.45D, 128, 1.25F, glow);
        drawFloorCircle(poseStack, cx, y + 0.006D, cz, radius, 128, 0.42F, core);
        drawFloorCircle(poseStack, cx, y + 0.010D, cz, radius - 1.15D, 128,
                0.20F, inner);
    }

    private static void renderCeremonialTextures(PoseStack poseStack, Boundary b,
            float floorAlpha, float wallAlpha) {
        double y = b.floorY + 0.035D;
        double cx = (b.minX + b.maxX) * 0.5D;
        double cz = (b.minZ + b.maxZ) * 0.5D;
        double sealRadius = Math.min(b.maxX - b.minX, b.maxZ - b.minZ) * 0.31D;

        // The generated seal is a focal emblem rather than a noisy field of particles.
        texturedQuad(poseStack, SEAL_TEXTURE,
                cx - sealRadius, y, cz - sealRadius,
                cx + sealRadius, y, cz - sealRadius,
                cx + sealRadius, y, cz + sealRadius,
                cx - sealRadius, y, cz + sealRadius, floorAlpha * 0.78F);

        double inset = 0.7D;
        double band = 4.0D;
        texturedQuad(poseStack, BORDER_TEXTURE,
                b.minX + inset, y + 0.008D, b.minZ + inset,
                b.maxX - inset, y + 0.008D, b.minZ + inset,
                b.maxX - inset, y + 0.008D, b.minZ + inset + band,
                b.minX + inset, y + 0.008D, b.minZ + inset + band, floorAlpha);
        texturedQuad(poseStack, BORDER_TEXTURE,
                b.maxX - inset, y + 0.008D, b.maxZ - inset,
                b.minX + inset, y + 0.008D, b.maxZ - inset,
                b.minX + inset, y + 0.008D, b.maxZ - inset - band,
                b.maxX - inset, y + 0.008D, b.maxZ - inset - band, floorAlpha);
        texturedQuad(poseStack, BORDER_TEXTURE,
                b.minX + inset, y + 0.009D, b.maxZ - inset,
                b.minX + inset, y + 0.009D, b.minZ + inset,
                b.minX + inset + band, y + 0.009D, b.minZ + inset,
                b.minX + inset + band, y + 0.009D, b.maxZ - inset, floorAlpha);
        texturedQuad(poseStack, BORDER_TEXTURE,
                b.maxX - inset, y + 0.009D, b.minZ + inset,
                b.maxX - inset, y + 0.009D, b.maxZ - inset,
                b.maxX - inset - band, y + 0.009D, b.maxZ - inset,
                b.maxX - inset - band, y + 0.009D, b.minZ + inset, floorAlpha);

        renderTexturedWall(poseStack, b.minX, b.minZ, b.minX, b.maxZ,
                b.floorY + 0.06D, b.floorY + 14.0D, wallAlpha);
        renderTexturedWall(poseStack, b.maxX, b.maxZ, b.maxX, b.minZ,
                b.floorY + 0.06D, b.floorY + 14.0D, wallAlpha);
        renderTexturedWall(poseStack, b.maxX, b.minZ, b.minX, b.minZ,
                b.floorY + 0.06D, b.floorY + 14.0D, wallAlpha);
        renderTexturedWall(poseStack, b.minX, b.maxZ, b.maxX, b.maxZ,
                b.floorY + 0.06D, b.floorY + 14.0D, wallAlpha);
    }

    private static void renderTexturedWall(PoseStack poseStack, double x1, double z1,
            double x2, double z2, double y0, double y1, float alpha) {
        int panels = 5;
        for (int i = 0; i < panels; i++) {
            double t0 = i / (double) panels;
            double t1 = (i + 1) / (double) panels;
            double ax = Mth.lerp(t0, x1, x2), az = Mth.lerp(t0, z1, z2);
            double bx = Mth.lerp(t1, x1, x2), bz = Mth.lerp(t1, z1, z2);
            texturedQuad(poseStack, WALL_TEXTURE,
                    ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, alpha);
        }
    }

    private static void texturedQuad(PoseStack poseStack, ResourceLocation texture,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4,
            float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textureVertex(buffer, matrix, x1, y1, z1, 0.0F, 0.0F, alpha);
        textureVertex(buffer, matrix, x2, y2, z2, 1.0F, 0.0F, alpha);
        textureVertex(buffer, matrix, x3, y3, z3, 1.0F, 1.0F, alpha);
        textureVertex(buffer, matrix, x4, y4, z4, 0.0F, 1.0F, alpha);
        Tesselator.getInstance().end();
    }

    private static void textureVertex(BufferBuilder buffer, Matrix4f matrix,
            double x, double y, double z, float u, float v, float alpha) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .uv(u, v).color(255, 255, 255,
                        Mth.clamp(Math.round(alpha * 255.0F), 0, 255)).endVertex();
    }

    private static void renderFloorSeals(PoseStack poseStack, Boundary b, float alpha) {
        float y = (float) b.floorY + 0.025F;
        // Three concentric rounded-rectangle traces create the ceremonial frame.
        drawRoundedRectLine(poseStack, b.minX, b.maxX, b.minZ, b.maxZ,
                y, 5.5D, 1.2F, rgba(alpha, 1.0F));
        drawRoundedRectLine(poseStack, b.minX + 0.55D, b.maxX - 0.55D,
                b.minZ + 0.55D, b.maxZ - 0.55D,
                y + 0.008F, 5.1D, 0.62F, rgba(alpha * 0.78F, 0.74F));
        drawRoundedRectLine(poseStack, b.minX + 1.35D, b.maxX - 1.35D,
                b.minZ + 1.35D, b.maxZ - 1.35D,
                y + 0.012F, 4.6D, 0.42F, rgba(alpha * 0.55F, 0.62F));

        // Repeated shrine seals along each side; line geometry keeps them crisp
        // and avoids the noisy look of particle glyphs.
        for (int i = 1; i <= 9; i++) {
            double t = i / 10.0D;
            double x = Mth.lerp(t, b.minX + 8.0D, b.maxX - 8.0D);
            drawKnot(poseStack, x, y + 0.018D, b.minZ + 0.48D,
                    0.72D, true, rgba(alpha * 0.72F, 0.85F));
            drawKnot(poseStack, x, y + 0.018D, b.maxZ - 0.48D,
                    0.72D, true, rgba(alpha * 0.72F, 0.85F));
        }
        for (int i = 1; i <= 8; i++) {
            double t = i / 9.0D;
            double z = Mth.lerp(t, b.minZ + 8.0D, b.maxZ - 8.0D);
            drawKnot(poseStack, b.minX + 0.48D, y + 0.018D, z,
                    0.72D, false, rgba(alpha * 0.72F, 0.85F));
            drawKnot(poseStack, b.maxX - 0.48D, y + 0.018D, z,
                    0.72D, false, rgba(alpha * 0.72F, 0.85F));
        }
    }

    private static void renderMirrorWalls(PoseStack poseStack, Boundary b,
            float alpha, double px, double pz) {
        int colorBottom = rgba(alpha, 0.72F);
        int colorTop = rgba(alpha * 0.03F, 0.60F);
        double y0 = b.floorY + 0.08D;
        double y1 = b.floorY + 12.5D;
        drawWall(poseStack, b.minX, b.minZ + 5.5D, b.minX, b.maxZ - 5.5D,
                y0, y1, colorBottom, colorTop);
        drawWall(poseStack, b.maxX, b.maxZ - 5.5D, b.maxX, b.minZ + 5.5D,
                y0, y1, colorBottom, colorTop);
        drawWall(poseStack, b.maxX - 5.5D, b.minZ, b.minX + 5.5D, b.minZ,
                y0, y1, colorBottom, colorTop);
        drawWall(poseStack, b.minX + 5.5D, b.maxZ, b.maxX - 5.5D, b.maxZ,
                y0, y1, colorBottom, colorTop);

        // Sparse flowing streaks give the membrane motion without particle spam.
        double offset = ((age % 120) / 120.0D) * 5.0D;
        int streak = rgba(alpha * 0.55F, 1.0F);
        for (int i = 0; i < 7; i++) {
            double y = b.floorY + 0.8D + (i * 1.75D + offset) % 10.5D;
            drawLine3D(poseStack, b.minX, y, b.minZ + 8.0D,
                    b.minX, y + 0.7D, b.maxZ - 8.0D, 0.20F, streak);
            drawLine3D(poseStack, b.maxX, y, b.maxZ - 8.0D,
                    b.maxX, y + 0.7D, b.minZ + 8.0D, 0.20F, streak);
        }
    }

    private static void renderToriiAnchors(PoseStack poseStack, Boundary b, float alpha) {
        int core = rgba(alpha, 1.0F);
        int aura = rgba(alpha * 0.30F, 0.82F);
        drawTorii(poseStack, b.minX + 3.9D, b.floorY + 0.08D, b.minZ + 3.9D,
                45.0F, core, aura);
        drawTorii(poseStack, b.maxX - 3.9D, b.floorY + 0.08D, b.minZ + 3.9D,
                -45.0F, core, aura);
        drawTorii(poseStack, b.maxX - 3.9D, b.floorY + 0.08D, b.maxZ - 3.9D,
                -135.0F, core, aura);
        drawTorii(poseStack, b.minX + 3.9D, b.floorY + 0.08D, b.maxZ - 3.9D,
                135.0F, core, aura);
    }

    private static void renderContactRipple(PoseStack poseStack, Boundary b,
            double px, double pz, float alpha) {
        double x = Mth.clamp(px, b.minX, b.maxX);
        double z = Mth.clamp(pz, b.minZ, b.maxZ);
        double dl = Math.abs(px - b.minX), dr = Math.abs(b.maxX - px);
        double dn = Math.abs(pz - b.minZ), ds = Math.abs(b.maxZ - pz);
        double min = Math.min(Math.min(dl, dr), Math.min(dn, ds));
        if (min == dl) x = b.minX;
        else if (min == dr) x = b.maxX;
        else if (min == dn) z = b.minZ;
        else z = b.maxZ;
        double phase = (age % 28) / 28.0D;
        int color = rgba(alpha * (1.0F - (float) phase), 1.0F);
        for (int i = 0; i < 3; i++) {
            double radius = 0.8D + ((phase + i / 3.0D) % 1.0D) * 5.0D;
            drawFloorCircle(poseStack, x, b.floorY + 0.065D, z, radius, 32,
                    0.28F, color);
        }
    }

    private static int rgba(float alpha, float intensity) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
        int g = Mth.clamp(Math.round(40.0F * intensity), 0, 255);
        int bl = Mth.clamp(Math.round(22.0F * intensity), 0, 255);
        return a << 24 | r << 16 | g << 8 | bl;
    }

    private static void drawRoundedRectLine(PoseStack poseStack, double minX, double maxX,
            double minZ, double maxZ, double y, double radius, float width, int color) {
        int cornerSteps = 10;
        double[] xs = new double[cornerSteps * 4 + 4];
        double[] zs = new double[xs.length];
        int index = 0;
        double[][] corners = {
                {maxX - radius, minZ + radius, -Math.PI / 2, 0},
                {maxX - radius, maxZ - radius, 0, Math.PI / 2},
                {minX + radius, maxZ - radius, Math.PI / 2, Math.PI},
                {minX + radius, minZ + radius, Math.PI, Math.PI * 1.5}
        };
        for (double[] c : corners) {
            for (int i = 0; i <= cornerSteps; i++) {
                double a = Mth.lerp(i / (double) cornerSteps, c[2], c[3]);
                xs[index] = c[0] + Math.cos(a) * radius;
                zs[index] = c[1] + Math.sin(a) * radius;
                index++;
            }
        }
        drawFloorPolyline(poseStack, xs, zs, index, y, width, color, true);
    }

    private static void drawKnot(PoseStack poseStack, double x, double y, double z,
            double scale, boolean alongX, int color) {
        double[] xs = new double[25];
        double[] zs = new double[25];
        for (int i = 0; i < xs.length; i++) {
            double a = i / 24.0D * Math.PI * 2.0D;
            double u = Math.sin(a * 2.0D) * scale;
            double v = Math.sin(a) * Math.cos(a) * scale * 0.65D;
            xs[i] = x + (alongX ? u : v);
            zs[i] = z + (alongX ? v : u);
        }
        drawFloorPolyline(poseStack, xs, zs, xs.length, y, 0.24F, color, false);
    }

    private static void drawTorii(PoseStack poseStack, double x, double y, double z,
            float yaw, int core, int aura) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        // Broad aura first, then a crisp luminous skeleton.
        drawLocalLine(poseStack, -2.2, 0, 0, -1.55, 5.0, 0, 0.38F, aura);
        drawLocalLine(poseStack, 2.2, 0, 0, 1.55, 5.0, 0, 0.38F, aura);
        drawLocalLine(poseStack, -3.1, 5.0, 0, 3.1, 5.0, 0, 0.55F, aura);
        drawLocalLine(poseStack, -2.65, 4.25, 0, 2.65, 4.25, 0, 0.42F, aura);
        drawLocalLine(poseStack, -2.2, 0, 0, -1.55, 5.0, 0, 0.18F, core);
        drawLocalLine(poseStack, 2.2, 0, 0, 1.55, 5.0, 0, 0.18F, core);
        drawLocalLine(poseStack, -3.1, 5.0, 0, 3.1, 5.0, 0, 0.24F, core);
        drawLocalLine(poseStack, -2.65, 4.25, 0, 2.65, 4.25, 0, 0.18F, core);
        poseStack.popPose();
    }

    private static void drawLocalLine(PoseStack poseStack, double x1, double y1, double z1,
            double x2, double y2, double z2, float width, int color) {
        drawLine3D(poseStack, x1, y1, z1, x2, y2, z2, width, color);
    }

    private static void drawWall(PoseStack poseStack, double x1, double z1,
            double x2, double z2, double y0, double y1, int bottom, int top) {
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        vertex(buffer, matrix, x1, y0, z1, bottom);
        vertex(buffer, matrix, x2, y0, z2, bottom);
        vertex(buffer, matrix, x2, y1, z2, top);
        vertex(buffer, matrix, x1, y1, z1, top);
    }

    private static void drawLine3D(PoseStack poseStack, double x1, double y1, double z1,
            double x2, double y2, double z2, float width, int color) {
        // Camera-facing approximation: two crossed ribbons stay visible at all angles.
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5D) return;
        double nx = -dz / len * width, nz = dx / len * width;
        quad(poseStack, x1 - nx, y1, z1 - nz, x1 + nx, y1, z1 + nz,
                x2 + nx, y2, z2 + nz, x2 - nx, y2, z2 - nz, color);
        quad(poseStack, x1, y1 - width, z1, x1, y1 + width, z1,
                x2, y2 + width, z2, x2, y2 - width, z2, color);
    }

    private static void drawFloorCircle(PoseStack poseStack, double cx, double y, double cz,
            double radius, int steps, float width, int color) {
        double[] xs = new double[steps];
        double[] zs = new double[steps];
        for (int i = 0; i < steps; i++) {
            double a = i / (double) steps * Math.PI * 2.0D;
            xs[i] = cx + Math.cos(a) * radius;
            zs[i] = cz + Math.sin(a) * radius;
        }
        drawFloorPolyline(poseStack, xs, zs, steps, y, width, color, true);
    }

    private static void drawFloorPolyline(PoseStack poseStack, double[] xs, double[] zs,
            int count, double y, float width, int color, boolean closed) {
        int edges = closed ? count : count - 1;
        for (int i = 0; i < edges; i++) {
            int j = (i + 1) % count;
            double dx = xs[j] - xs[i], dz = zs[j] - zs[i];
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-5D) continue;
            double nx = -dz / len * width * 0.5D;
            double nz = dx / len * width * 0.5D;
            quad(poseStack,
                    xs[i] - nx, y, zs[i] - nz,
                    xs[i] + nx, y, zs[i] + nz,
                    xs[j] + nx, y, zs[j] + nz,
                    xs[j] - nx, y, zs[j] - nz, color);
        }
    }

    private static void quad(PoseStack poseStack,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        vertex(buffer, matrix, x1, y1, z1, color);
        vertex(buffer, matrix, x2, y2, z2, color);
        vertex(buffer, matrix, x3, y3, z3, color);
        vertex(buffer, matrix, x4, y4, z4, color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
            double x, double y, double z, int color) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color((color >> 16) & 255, (color >> 8) & 255,
                        color & 255, (color >>> 24) & 255).endVertex();
    }

    private record Boundary(long challengeId, double minX, double maxX,
            double minZ, double maxZ, double floorY) {
        double centerX() {
            return (minX + maxX) * 0.5D;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }

        double radius() {
            return Math.min(maxX - minX, maxZ - minZ) * 0.5D;
        }

        double edgeDistance(double x, double z) {
            double dx = x - centerX();
            double dz = z - centerZ();
            return Math.abs(radius() - Math.sqrt(dx * dx + dz * dz));
        }
    }

    private MikageBoundaryClient() {
    }
}
