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
import dev.bladetetra.network.VoidScatteringVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;

/**
 * Extra release/counter staging for Void Scattering.
 * The main domain renderer stays focused on the dome itself; this layer makes the
 * stored return volley and Residual Bloom read like actual attacks.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidScatteringCounterVfxClient {
    private static final ArrayList<ReleaseVisual> RELEASES = new ArrayList<>();
    private static final ArrayList<ResidualGuardVisual> RESIDUAL_GUARDS = new ArrayList<>();
    private static final ArrayList<ResidualCounterVisual> RESIDUAL_COUNTERS = new ArrayList<>();
    private static final int MAX_SWORDS = 6;
    private static ClientLevel activeLevel;

    public static void accept(VoidScatteringVfxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        resetFor(minecraft.level);
        switch (packet.type()) {
            case VoidScatteringVfxPacket.COUNTER_RELEASE -> RELEASES.add(
                    new ReleaseVisual(packet.playerEntityId(),
                            Mth.clamp(packet.storedSlots(), 0, MAX_SWORDS),
                            Math.max(1, packet.duration()), packet.seed()));
            case VoidScatteringVfxPacket.RESIDUAL -> {
                RESIDUAL_GUARDS.removeIf(v -> v.playerEntityId == packet.playerEntityId());
                RESIDUAL_GUARDS.add(new ResidualGuardVisual(packet.playerEntityId(),
                        Math.max(1, packet.duration()), packet.seed()));
            }
            case VoidScatteringVfxPacket.RESIDUAL_COUNTER -> {
                RESIDUAL_GUARDS.removeIf(v -> v.playerEntityId == packet.playerEntityId());
                Vec3 direction = new Vec3(packet.impactX(), packet.impactY(), packet.impactZ());
                RESIDUAL_COUNTERS.add(new ResidualCounterVisual(packet.playerEntityId(),
                        Math.max(1, packet.duration()), packet.seed(),
                        direction.lengthSqr() > 1.0E-5D
                                ? direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D)));
            }
            case VoidScatteringVfxPacket.CANCEL -> {
                RESIDUAL_GUARDS.removeIf(v -> v.playerEntityId == packet.playerEntityId());
                RESIDUAL_COUNTERS.removeIf(v -> v.playerEntityId == packet.playerEntityId());
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        resetFor(minecraft.level);
        if (minecraft.isPaused()) return;
        RELEASES.removeIf(v -> ++v.age >= v.duration
                || minecraft.level == null
                || minecraft.level.getEntity(v.playerEntityId) == null);
        RESIDUAL_GUARDS.removeIf(v -> ++v.age >= v.duration
                || minecraft.level == null
                || minecraft.level.getEntity(v.playerEntityId) == null);
        RESIDUAL_COUNTERS.removeIf(v -> ++v.age >= v.duration
                || minecraft.level == null
                || minecraft.level.getEntity(v.playerEntityId) == null);
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || (RELEASES.isEmpty() && RESIDUAL_GUARDS.isEmpty()
                && RESIDUAL_COUNTERS.isEmpty())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;

        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);
        if (!renderShader(poses, camera, level, event.getPartialTick())) {
            renderFallback(poses, camera, level, event.getPartialTick());
        }
        poses.popPose();
    }

    private static boolean renderShader(PoseStack poses, Camera camera,
            ClientLevel level, float partialTick) {
        if (!ClientVisualConfig.ENABLE_VOID_SCATTERING_SHADER.get()) return false;
        ShaderInstance shader = VoidScatteringShaders.riftShader();
        if (shader == null) return false;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> shader);
        if (shader.getUniform("RiftTime") != null) {
            shader.getUniform("RiftTime").set(
                    (level.getGameTime() + partialTick) * 0.05F);
        }
        if (shader.getUniform("RiftIntensity") != null) {
            shader.getUniform("RiftIntensity").set(
                    ClientVisualConfig.VOID_SCATTERING_SHADER_INTENSITY.get().floatValue());
        }

        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        drawShaderRelease(buffer, matrix, camera.getPosition(), level, partialTick);
        drawShaderResiduals(buffer, matrix, camera.getPosition(), level, partialTick);
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private static void drawShaderRelease(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, ClientLevel level, float partialTick) {
        for (ReleaseVisual visual : RELEASES) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null || visual.swords <= 0) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            float gather = 1.0F - Mth.clamp(t / 0.46F, 0.0F, 1.0F);
            float pulse = Mth.clamp(1.0F - Math.abs(t - 0.34F) * 2.2F, 0.0F, 1.0F);
            Vec3 center = player.position().add(0.0D, 1.15D, 0.0D);
            for (int i = 0; i < visual.swords; i++) {
                double base = Math.floorMod(visual.seed, 360) * Math.PI / 180.0D;
                double angle = base + i * Math.PI * 2.0D / Math.max(1, visual.swords)
                        + visual.age * 0.018D;
                double radius = 1.88D - gather * 0.16D;
                double y = (i % 3 - 1) * 0.26D + Math.sin(visual.age * 0.15D + i) * 0.06D;
                Vec3 swordCenter = center.add(Math.cos(angle) * radius, y,
                        Math.sin(angle) * radius);
                drawShaderCrack(buffer, matrix, camera, swordCenter,
                        visual.seed + i * 83, 0.95F, true,
                        0.62F + pulse * 0.38F, 1.16F + pulse * 0.12F);
            }
        }
    }

    private static void drawShaderResiduals(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, ClientLevel level, float partialTick) {
        for (ResidualGuardVisual visual : RESIDUAL_GUARDS) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            float alpha = Mth.sin(t * Mth.PI);
            Vec3 center = player.getEyePosition(partialTick)
                    .add(player.getLookAngle().normalize().scale(1.72D));
            drawShaderCrack(buffer, matrix, camera, center, visual.seed,
                    alpha, false, 0.62F + alpha * 0.24F, 1.72F);
        }

        for (ResidualCounterVisual visual : RESIDUAL_COUNTERS) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            float alpha = 1.0F - t;
            Vec3 look = player.getLookAngle().normalize();
            Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-5D) right = new Vec3(1.0D, 0.0D, 0.0D);
            else right = right.normalize();
            Vec3 center = player.getEyePosition(partialTick).add(look.scale(1.78D));
            drawShaderCrack(buffer, matrix, camera, center.add(right.scale(0.34D)),
                    visual.seed + 19, alpha, true, 1.0F, 1.68F);
            drawShaderCrack(buffer, matrix, camera, center.subtract(right.scale(0.34D))
                            .add(0.0D, 0.10D, 0.0D),
                    visual.seed + 97, alpha, true, 0.96F, 1.58F);
        }
    }

    private static void renderFallback(PoseStack poses, Camera camera,
            ClientLevel level, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        Vec3 cameraPos = camera.getPosition();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (ReleaseVisual visual : RELEASES) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            Vec3 center = player.position().add(0.0D, 1.15D, 0.0D);
            for (int i = 0; i < visual.swords; i++) {
                double angle = Math.floorMod(visual.seed, 360) * Math.PI / 180.0D
                        + i * Math.PI * 2.0D / Math.max(1, visual.swords);
                Vec3 point = center.add(Math.cos(angle) * 1.82D,
                        (i % 3 - 1) * 0.24D, Math.sin(angle) * 1.82D);
                drawFallbackBlade(buffer, matrix, cameraPos, point,
                        0.88F * (1.0F - t * 0.35F), 1.0F);
            }
        }

        for (ResidualGuardVisual visual : RESIDUAL_GUARDS) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            Vec3 center = player.getEyePosition(partialTick)
                    .add(player.getLookAngle().normalize().scale(1.72D));
            drawFallbackBlade(buffer, matrix, cameraPos, center,
                    Mth.sin(t * Mth.PI) * 0.72F, 1.5F);
        }

        for (ResidualCounterVisual visual : RESIDUAL_COUNTERS) {
            Entity player = level.getEntity(visual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((visual.age + partialTick) / visual.duration, 0.0F, 1.0F);
            Vec3 look = player.getLookAngle().normalize();
            Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-5D) right = new Vec3(1.0D, 0.0D, 0.0D);
            else right = right.normalize();
            Vec3 center = player.getEyePosition(partialTick).add(look.scale(1.78D));
            float alpha = 1.0F - t;
            drawFallbackBlade(buffer, matrix, cameraPos, center.add(right.scale(0.32D)),
                    alpha, 1.55F);
            drawFallbackBlade(buffer, matrix, cameraPos, center.subtract(right.scale(0.32D)),
                    alpha, 1.45F);
        }

        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawShaderCrack(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 center, int seed, float alpha, boolean filled,
            float flash, float scale) {
        if (alpha <= 0.001F) return;
        Vec3 right = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        right = right.lengthSqr() < 0.0001D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(right.z, 0.0D, -right.x).normalize();
        double halfWidth = 0.34D * scale;
        double halfHeight = 0.72D * scale;
        Vec3 horizontal = right.scale(halfWidth);
        Vec3 vertical = new Vec3(0.0D, halfHeight, 0.0D);
        int fillByte = filled ? 255 : 0;
        int flashByte = Mth.clamp(Math.round(Mth.clamp(flash, 0.0F, 1.0F) * 255.0F), 0, 255);
        int seedByte = Math.floorMod(seed, 251);
        int alphaByte = Mth.clamp(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F), 0, 255);
        shaderVertex(buffer, matrix, center.subtract(horizontal).subtract(vertical),
                0.0F, 1.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.add(horizontal).subtract(vertical),
                1.0F, 1.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.add(horizontal).add(vertical),
                1.0F, 0.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.subtract(horizontal).add(vertical),
                0.0F, 0.0F, fillByte, flashByte, seedByte, alphaByte);
    }

    private static void shaderVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 point, float u, float v, int filled, int flash, int seed, int alpha) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .uv(u, v).color(filled, flash, seed, alpha).endVertex();
    }

    private static void drawFallbackBlade(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 center, float alpha, float scale) {
        if (alpha <= 0.001F) return;
        Vec3 view = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        Vec3 right = view.lengthSqr() < 1.0E-5D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(view.z, 0.0D, -view.x).normalize();
        Vec3 vertical = new Vec3(0.0D, 0.72D * scale, 0.0D);
        Vec3 horizontal = right.scale(0.085D * scale);
        int a = Mth.clamp(Math.round(alpha * 220.0F), 0, 220);
        int r = 226, g = 207, b = 255;
        fallbackVertex(buffer, matrix, center.subtract(horizontal).subtract(vertical), r, g, b, a);
        fallbackVertex(buffer, matrix, center.add(horizontal).subtract(vertical), r, g, b, a);
        fallbackVertex(buffer, matrix, center.add(horizontal).add(vertical), r, g, b, a);
        fallbackVertex(buffer, matrix, center.subtract(horizontal).add(vertical), r, g, b, a);
    }

    private static void fallbackVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 point, int r, int g, int b, int a) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(r, g, b, a).endVertex();
    }

    private static void resetFor(ClientLevel level) {
        if (level == activeLevel) return;
        activeLevel = level;
        RELEASES.clear();
        RESIDUAL_GUARDS.clear();
        RESIDUAL_COUNTERS.clear();
    }

    private static final class ReleaseVisual {
        private final int playerEntityId;
        private final int swords;
        private final int duration;
        private final int seed;
        private int age;

        private ReleaseVisual(int playerEntityId, int swords, int duration, int seed) {
            this.playerEntityId = playerEntityId;
            this.swords = swords;
            this.duration = duration;
            this.seed = seed;
        }
    }

    private static final class ResidualGuardVisual {
        private final int playerEntityId;
        private final int duration;
        private final int seed;
        private int age;

        private ResidualGuardVisual(int playerEntityId, int duration, int seed) {
            this.playerEntityId = playerEntityId;
            this.duration = duration;
            this.seed = seed;
        }
    }

    private static final class ResidualCounterVisual {
        private final int playerEntityId;
        private final int duration;
        private final int seed;
        @SuppressWarnings("unused")
        private final Vec3 impactDirection;
        private int age;

        private ResidualCounterVisual(int playerEntityId, int duration, int seed,
                Vec3 impactDirection) {
            this.playerEntityId = playerEntityId;
            this.duration = duration;
            this.seed = seed;
            this.impactDirection = impactDirection;
        }
    }

    private VoidScatteringCounterVfxClient() {
    }
}
