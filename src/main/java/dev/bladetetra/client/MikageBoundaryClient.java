package dev.bladetetra.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

import static dev.bladetetra.client.vfx.render.VfxPrimitives.ringHorizontal;

/**
 * Shader-light, dependency-free arena VFX. Everything here is immediate client
 * geometry: no particle objects, block entities, world edits, or server spam.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MikageBoundaryClient {
    private static final ResourceLocation MIRROR_REALM =
            new ResourceLocation(BladeTetra.MOD_ID, "mirror_realm");
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
        renderGroundRing(geometry, poseStack.last().pose(), active,
                appear * brightness * pulse, near);
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** A restrained, floor-only arena mark aligned with the server's circular limit. */
    private static void renderGroundRing(BufferBuilder buffer, Matrix4f matrix, Boundary boundary,
            float alpha, float near) {
        double radius = boundary.radius();
        double y = boundary.floorY + 0.045D;
        int glow = rgba(alpha * (0.34F + near * 0.24F), 0.76F);
        int core = rgba(alpha * (0.76F + near * 0.24F), 1.0F);
        int inner = rgba(alpha * 0.32F, 0.62F);
        Vec3 center = new Vec3(boundary.centerX(), y, boundary.centerZ());

        // VfxPrimitives uses half-width while the retired local helper used full width.
        ringHorizontal(buffer, matrix, center, radius + 0.45D, 1.25D * 0.5D, glow, 128);
        ringHorizontal(buffer, matrix, center.add(0.0D, 0.006D, 0.0D),
                radius, 0.42D * 0.5D, core, 128);
        ringHorizontal(buffer, matrix, center.add(0.0D, 0.010D, 0.0D),
                radius - 1.15D, 0.20D * 0.5D, inner, 128);
    }

    private static int rgba(float alpha, float intensity) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(255.0F * intensity), 0, 255);
        int g = Mth.clamp(Math.round(40.0F * intensity), 0, 255);
        int blue = Mth.clamp(Math.round(22.0F * intensity), 0, 255);
        return a << 24 | r << 16 | g << 8 | blue;
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
