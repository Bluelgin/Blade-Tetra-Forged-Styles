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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client reconstruction of Void Scattering's translucent dome and stored swords. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidScatteringVfxClient {
    private static final ResourceLocation READY_ICON = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/gui/void_scattering_ready.png");
    private static final Map<Integer, DomainVisual> DOMAINS = new HashMap<>();
    private static final ArrayList<ResidualVisual> RESIDUALS = new ArrayList<>();
    private static final double DOME_RADIUS = 3.15D;
    private static final int DOME_LAT_SEGMENTS = 10;
    private static final int DOME_LON_SEGMENTS = 18;
    private static final int MAX_STORED_SWORDS = 6;
    private static final int COLLAPSE_TICKS = 8;
    private static ClientLevel activeLevel;
    private static int readyTicks;

    public static void accept(VoidScatteringVfxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        resetFor(minecraft.level);
        switch (packet.type()) {
            case VoidScatteringVfxPacket.OPEN -> DOMAINS.put(packet.playerEntityId(),
                    new DomainVisual(packet.playerEntityId(), packet.duration(), packet.seed()));
            case VoidScatteringVfxPacket.CAPTURE -> {
                DomainVisual domain = DOMAINS.get(packet.playerEntityId());
                if (domain != null && domain.seed == packet.seed()) {
                    int previous = domain.storedSwords;
                    domain.storedSwords = Mth.clamp(packet.storedSlots(), 0, MAX_STORED_SWORDS);
                    domain.newSwordIndex = domain.storedSwords > previous
                            ? domain.storedSwords - 1 : -1;
                    domain.flashTicks = 10;
                    Vec3 impact = new Vec3(packet.impactX(), packet.impactY(), packet.impactZ());
                    if (impact.lengthSqr() > 1.0E-5D) {
                        domain.impactDirection = impact.normalize();
                    }
                    domain.remainingTicks = Math.max(domain.remainingTicks, packet.duration());
                }
            }
            case VoidScatteringVfxPacket.COLLAPSE -> {
                DomainVisual domain = DOMAINS.get(packet.playerEntityId());
                if (domain != null) {
                    domain.storedSwords = Mth.clamp(packet.storedSlots(), 0, MAX_STORED_SWORDS);
                    domain.collapsing = true;
                    domain.remainingTicks = Math.max(1, packet.duration());
                }
            }
            case VoidScatteringVfxPacket.CANCEL -> DOMAINS.remove(packet.playerEntityId());
            case VoidScatteringVfxPacket.RESIDUAL -> RESIDUALS.add(new ResidualVisual(
                    packet.playerEntityId(), packet.duration(), packet.seed()));
            case VoidScatteringVfxPacket.READY -> {
                if (minecraft.player != null
                        && minecraft.player.getId() == packet.playerEntityId()) {
                    readyTicks = Math.max(readyTicks, packet.duration());
                }
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
        Iterator<DomainVisual> domains = DOMAINS.values().iterator();
        while (domains.hasNext()) {
            DomainVisual domain = domains.next();
            domain.age++;
            if (domain.remainingTicks > 0) domain.remainingTicks--;
            if (domain.flashTicks > 0) domain.flashTicks--;
            if (domain.flashTicks <= 0) domain.newSwordIndex = -1;
            if ((domain.collapsing && domain.remainingTicks <= 0)
                    || minecraft.level == null
                    || minecraft.level.getEntity(domain.playerEntityId) == null) {
                domains.remove();
            }
        }
        RESIDUALS.removeIf(residual -> ++residual.age >= residual.duration
                || minecraft.level == null
                || minecraft.level.getEntity(residual.playerEntityId) == null);
        if (readyTicks > 0) readyTicks--;
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || (DOMAINS.isEmpty() && RESIDUALS.isEmpty())) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);

        if (!renderShaderVisuals(poses, camera, level, event.getPartialTick())) {
            renderFallbackVisuals(poses, camera, level, event.getPartialTick());
        }

        poses.popPose();
    }

    private static boolean renderShaderVisuals(PoseStack poses, Camera camera,
            ClientLevel level, float partialTick) {
        if (!ClientVisualConfig.ENABLE_VOID_SCATTERING_SHADER.get()) return false;
        ShaderInstance riftShader = VoidScatteringShaders.riftShader();
        ShaderInstance domeShader = VoidScatteringShaders.domeShader();
        if (riftShader == null || (!DOMAINS.isEmpty() && domeShader == null)) return false;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Matrix4f matrix = poses.last().pose();
        if (domeShader != null) {
            for (DomainVisual domain : DOMAINS.values()) {
                Entity player = level.getEntity(domain.playerEntityId);
                if (player == null) continue;
                renderShaderDome(domeShader, matrix, player, domain, level, partialTick);
            }
        }

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(() -> riftShader);
        if (riftShader.getUniform("RiftTime") != null) {
            riftShader.getUniform("RiftTime").set(
                    (level.getGameTime() + partialTick) * 0.05F);
        }
        if (riftShader.getUniform("RiftIntensity") != null) {
            riftShader.getUniform("RiftIntensity").set(
                    ClientVisualConfig.VOID_SCATTERING_SHADER_INTENSITY.get().floatValue());
        }
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        drawShaderSwordsAndResiduals(buffer, matrix, camera.getPosition(), level, partialTick);
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private static void renderShaderDome(ShaderInstance shader, Matrix4f matrix,
            Entity player, DomainVisual domain, ClientLevel level, float partialTick) {
        float age = domain.age + partialTick;
        float opening = openingProgress(age);
        float closing = closingProgress(domain);
        float visibility = opening * (1.0F - closing * 0.48F);
        if (visibility <= 0.001F) return;

        RenderSystem.setShader(() -> shader);
        float configured = ClientVisualConfig.VOID_SCATTERING_SHADER_INTENSITY.get().floatValue();
        if (shader.getUniform("DomeTime") != null) {
            shader.getUniform("DomeTime").set((level.getGameTime() + partialTick) * 0.04F);
        }
        if (shader.getUniform("DomeIntensity") != null) {
            shader.getUniform("DomeIntensity").set(configured);
        }
        if (shader.getUniform("DomeOpacity") != null) {
            shader.getUniform("DomeOpacity").set(0.16F * configured);
        }
        Vec3 impactUv = impactUv(domain.impactDirection);
        if (shader.getUniform("ImpactUv") != null) {
            shader.getUniform("ImpactUv").set((float) impactUv.x, (float) impactUv.y);
        }
        if (shader.getUniform("ImpactFlash") != null) {
            shader.getUniform("ImpactFlash").set(domain.flashTicks / 10.0F);
        }
        if (shader.getUniform("Collapse") != null) {
            shader.getUniform("Collapse").set(closing);
        }

        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        double radius = DOME_RADIUS * (0.82D + opening * 0.18D) * (1.0D - closing * 0.16D);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        drawShaderSphere(buffer, matrix, center, radius, domain.seed, visibility);
        Tesselator.getInstance().end();
    }

    private static void renderFallbackVisuals(PoseStack poses, Camera camera,
            ClientLevel level, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poses.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (DomainVisual domain : DOMAINS.values()) {
            Entity player = level.getEntity(domain.playerEntityId);
            if (player != null) drawFallbackDome(buffer, matrix, player, domain, partialTick);
        }
        drawFallbackSwordsAndResiduals(buffer, matrix, camera.getPosition(), level,
                partialTick, false);
        Tesselator.getInstance().end();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawFallbackSwordsAndResiduals(buffer, matrix, camera.getPosition(), level,
                partialTick, true);
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (readyTicks <= 0) return;
        float life = (readyTicks + event.getPartialTick()) / 28.0F;
        float alpha = Mth.clamp(Math.min((28.0F - readyTicks) / 5.0F, life / 0.28F),
                0.0F, 1.0F);
        int x = event.getWindow().getGuiScaledWidth() / 2 + 12;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 11;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        event.getGuiGraphics().blit(READY_ICON, x, y, 0, 0,
                32, 32, 32, 32);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawShaderSwordsAndResiduals(BufferBuilder buffer,
            Matrix4f matrix, Vec3 camera, ClientLevel level, float partialTick) {
        for (DomainVisual domain : DOMAINS.values()) {
            Entity player = level.getEntity(domain.playerEntityId);
            if (player == null) continue;
            float age = domain.age + partialTick;
            Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
            float closing = closingProgress(domain);
            for (int sword = 0; sword < domain.storedSwords; sword++) {
                Vec3 swordCenter = storedSwordCenter(center, domain.seed, sword, age, closing);
                float flash = sword == domain.newSwordIndex
                        ? domain.flashTicks / 10.0F : 0.0F;
                flash = Math.max(flash, closing * 0.72F);
                drawShaderCrack(buffer, matrix, camera, swordCenter,
                        domain.seed + sword * 71, 1.0F, true, flash,
                        0.82F + (sword % 2) * 0.04F);
            }
        }
        for (ResidualVisual residual : RESIDUALS) {
            Entity player = level.getEntity(residual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((residual.age + partialTick) / residual.duration,
                    0.0F, 1.0F);
            Vec3 look = player.getLookAngle().normalize();
            Vec3 center = player.getEyePosition(partialTick).add(look.scale(1.45D));
            drawShaderCrack(buffer, matrix, camera, center, residual.seed,
                    Mth.sin(t * Mth.PI), false, 0.52F, 1.18F);
        }
    }

    private static void drawFallbackSwordsAndResiduals(BufferBuilder buffer,
            Matrix4f matrix, Vec3 camera, ClientLevel level, float partialTick,
            boolean glow) {
        for (DomainVisual domain : DOMAINS.values()) {
            Entity player = level.getEntity(domain.playerEntityId);
            if (player == null) continue;
            float age = domain.age + partialTick;
            Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
            float closing = closingProgress(domain);
            for (int sword = 0; sword < domain.storedSwords; sword++) {
                Vec3 swordCenter = storedSwordCenter(center, domain.seed, sword, age, closing);
                float flash = sword == domain.newSwordIndex
                        ? domain.flashTicks / 10.0F : 0.0F;
                if (glow) flash = Math.max(flash, closing * 0.65F);
                drawCrack(buffer, matrix, camera, swordCenter,
                        domain.seed + sword * 71, 1.0F, true, flash, glow);
            }
        }
        for (ResidualVisual residual : RESIDUALS) {
            Entity player = level.getEntity(residual.playerEntityId);
            if (player == null) continue;
            float t = Mth.clamp((residual.age + partialTick) / residual.duration,
                    0.0F, 1.0F);
            Vec3 look = player.getLookAngle().normalize();
            Vec3 center = player.getEyePosition(partialTick).add(look.scale(1.45D));
            drawCrack(buffer, matrix, camera, center, residual.seed,
                    Mth.sin(t * Mth.PI), false, 0.35F, glow);
        }
    }

    private static void drawFallbackDome(BufferBuilder buffer, Matrix4f matrix,
            Entity player, DomainVisual domain, float partialTick) {
        float age = domain.age + partialTick;
        float opening = openingProgress(age);
        float closing = closingProgress(domain);
        float visibility = opening * (1.0F - closing * 0.52F);
        if (visibility <= 0.001F) return;
        float impact = domain.flashTicks / 10.0F;
        int domeColor = color(0.025F, 0.012F, 0.045F,
                visibility * (0.075F + impact * 0.025F + closing * 0.02F));
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        double radius = DOME_RADIUS * (0.82D + opening * 0.18D) * (1.0D - closing * 0.16D);
        drawColorSphere(buffer, matrix, center, radius, domeColor);
    }

    private static float openingProgress(float age) {
        return Mth.clamp(age / 8.0F, 0.0F, 1.0F);
    }

    private static float closingProgress(DomainVisual domain) {
        if (domain.collapsing) {
            return Mth.clamp(1.0F - domain.remainingTicks / (float) COLLAPSE_TICKS,
                    0.0F, 1.0F);
        }
        if (domain.remainingTicks <= 12) {
            return Mth.clamp((12.0F - domain.remainingTicks) / 12.0F,
                    0.0F, 1.0F);
        }
        return 0.0F;
    }

    private static Vec3 storedSwordCenter(Vec3 center, int seed, int sword,
            float age, float closing) {
        double base = Math.floorMod(seed, 360) * Math.PI / 180.0D;
        double angle = base + sword * (Math.PI * 2.0D / MAX_STORED_SWORDS)
                + age * 0.012D;
        double radius = (1.46D + ((seed >>> (sword * 3)) & 3) * 0.035D)
                * (1.0D - closing * 0.26D);
        double height = 0.02D + (sword % 3) * 0.19D
                + Math.sin(age * 0.06D + sword * 1.3D) * 0.055D;
        return center.add(Math.cos(angle) * radius, height,
                Math.sin(angle) * radius);
    }

    private static Vec3 impactUv(Vec3 direction) {
        Vec3 normalized = direction.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        double u = Math.atan2(normalized.z, normalized.x) / (Math.PI * 2.0D) + 0.5D;
        u = u - Math.floor(u);
        double v = 0.5D - Math.asin(Mth.clamp(normalized.y, -1.0D, 1.0D)) / Math.PI;
        return new Vec3(u, v, 0.0D);
    }

    private static void drawShaderSphere(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, int seed, float alpha) {
        int alphaByte = Mth.clamp(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F),
                0, 255);
        int seedByte = Math.floorMod(seed, 251);
        for (int lat = 0; lat < DOME_LAT_SEGMENTS; lat++) {
            float v0 = lat / (float) DOME_LAT_SEGMENTS;
            float v1 = (lat + 1) / (float) DOME_LAT_SEGMENTS;
            double theta0 = -Math.PI * 0.5D + Math.PI * v0;
            double theta1 = -Math.PI * 0.5D + Math.PI * v1;
            for (int lon = 0; lon < DOME_LON_SEGMENTS; lon++) {
                float u0 = lon / (float) DOME_LON_SEGMENTS;
                float u1 = (lon + 1) / (float) DOME_LON_SEGMENTS;
                shaderSphereVertex(buffer, matrix, center, radius, theta0,
                        Math.PI * 2.0D * u0, u0, v0, seedByte, alphaByte);
                shaderSphereVertex(buffer, matrix, center, radius, theta0,
                        Math.PI * 2.0D * u1, u1, v0, seedByte, alphaByte);
                shaderSphereVertex(buffer, matrix, center, radius, theta1,
                        Math.PI * 2.0D * u1, u1, v1, seedByte, alphaByte);
                shaderSphereVertex(buffer, matrix, center, radius, theta1,
                        Math.PI * 2.0D * u0, u0, v1, seedByte, alphaByte);
            }
        }
    }

    private static void shaderSphereVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, double theta, double phi,
            float u, float v, int seed, int alpha) {
        double ring = Math.cos(theta);
        Vec3 point = center.add(Math.cos(phi) * ring * radius,
                Math.sin(theta) * radius, Math.sin(phi) * ring * radius);
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .uv(u, v).color(255, 255, seed, alpha).endVertex();
    }

    private static void drawColorSphere(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, int color) {
        for (int lat = 0; lat < DOME_LAT_SEGMENTS; lat++) {
            double theta0 = -Math.PI * 0.5D + Math.PI * lat / DOME_LAT_SEGMENTS;
            double theta1 = -Math.PI * 0.5D + Math.PI * (lat + 1) / DOME_LAT_SEGMENTS;
            for (int lon = 0; lon < DOME_LON_SEGMENTS; lon++) {
                double phi0 = Math.PI * 2.0D * lon / DOME_LON_SEGMENTS;
                double phi1 = Math.PI * 2.0D * (lon + 1) / DOME_LON_SEGMENTS;
                colorSphereVertex(buffer, matrix, center, radius, theta0, phi0, color);
                colorSphereVertex(buffer, matrix, center, radius, theta0, phi1, color);
                colorSphereVertex(buffer, matrix, center, radius, theta1, phi1, color);
                colorSphereVertex(buffer, matrix, center, radius, theta1, phi0, color);
            }
        }
    }

    private static void colorSphereVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, double theta, double phi, int color) {
        double ring = Math.cos(theta);
        Vec3 point = center.add(Math.cos(phi) * ring * radius,
                Math.sin(theta) * radius, Math.sin(phi) * ring * radius);
        vertex(buffer, matrix, point, color);
    }

    private static void drawShaderCrack(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, int seed, float alpha, boolean filled, float flash,
            float scale) {
        if (alpha <= 0.001F) return;
        Vec3 right = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        right = right.lengthSqr() < 0.0001D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(right.z, 0.0D, -right.x).normalize();

        double halfWidth = 0.34D * scale * (0.94D + flash * 0.10D);
        double halfHeight = 0.72D * scale;
        Vec3 horizontal = right.scale(halfWidth);
        Vec3 vertical = new Vec3(0.0D, halfHeight, 0.0D);

        int fillByte = filled ? 255 : 0;
        int flashByte = Mth.clamp(Math.round(Mth.clamp(flash, 0.0F, 1.0F) * 255.0F),
                0, 255);
        int seedByte = Math.floorMod(seed, 251);
        int alphaByte = Mth.clamp(Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F),
                0, 255);

        shaderVertex(buffer, matrix, center.subtract(horizontal).subtract(vertical),
                0.0F, 1.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.add(horizontal).subtract(vertical),
                1.0F, 1.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.add(horizontal).add(vertical),
                1.0F, 0.0F, fillByte, flashByte, seedByte, alphaByte);
        shaderVertex(buffer, matrix, center.subtract(horizontal).add(vertical),
                0.0F, 0.0F, fillByte, flashByte, seedByte, alphaByte);
    }

    private static void shaderVertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point,
            float u, float v, int filled, int flash, int seed, int alpha) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .uv(u, v).color(filled, flash, seed, alpha).endVertex();
    }

    private static void drawCrack(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, int seed, float alpha, boolean filled, float flash,
            boolean glow) {
        if (alpha <= 0.001F) return;
        Vec3 right = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        right = right.lengthSqr() < 0.0001D ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(right.z, 0.0D, -right.x).normalize();
        Vec3[] points = new Vec3[6];
        for (int i = 0; i < points.length; i++) {
            double y = -0.58D + i * 0.235D;
            double jag = (((seed >>> (i * 4)) & 15) / 15.0D - 0.5D) * 0.34D;
            points[i] = center.add(right.scale(jag)).add(0.0D, y, 0.0D);
        }
        float pulse = 0.82F + flash * 0.62F;
        int crackColor = glow
                ? color(0.86F, 0.80F, 1.0F, alpha * (0.48F + flash * 0.40F))
                : color(0.09F, 0.045F, 0.14F, alpha * 0.92F);
        double width = (glow ? 0.032D : 0.105D) * pulse;
        for (int i = 0; i + 1 < points.length; i++) {
            line(buffer, matrix, camera, points[i], points[i + 1], width, crackColor);
        }
        if (!filled) return;
        Vec3 bottom = center.subtract(right.scale(0.12D)).add(0.0D, -0.43D, 0.0D);
        Vec3 top = center.add(right.scale(0.16D)).add(0.0D, 0.45D, 0.0D);
        int sword = glow
                ? color(0.94F, 0.91F, 1.0F, alpha * 0.88F)
                : color(0.20F, 0.11F, 0.28F, alpha * 0.95F);
        line(buffer, matrix, camera, bottom, top, glow ? 0.025D : 0.075D, sword);
        Vec3 guardCenter = bottom.lerp(top, 0.28D);
        line(buffer, matrix, camera, guardCenter.subtract(right.scale(0.22D)),
                guardCenter.add(right.scale(0.22D)), glow ? 0.018D : 0.055D, sword);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 start, Vec3 end, double halfWidth, int color) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 0.000001D) return;
        Vec3 side = direction.cross(camera.subtract(start.add(end).scale(0.5D)));
        if (side.lengthSqr() < 0.0001D) side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.0001D) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(halfWidth);
        vertex(buffer, matrix, start.subtract(side), color);
        vertex(buffer, matrix, end.subtract(side), color);
        vertex(buffer, matrix, end.add(side), color);
        vertex(buffer, matrix, start.add(side), color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 point, int color) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color((color >> 16) & 255, (color >> 8) & 255,
                        color & 255, (color >>> 24) & 255).endVertex();
    }

    private static int color(float red, float green, float blue, float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24
                | Mth.clamp(Math.round(red * 255), 0, 255) << 16
                | Mth.clamp(Math.round(green * 255), 0, 255) << 8
                | Mth.clamp(Math.round(blue * 255), 0, 255);
    }

    private static void resetFor(ClientLevel level) {
        if (level == activeLevel) return;
        activeLevel = level;
        DOMAINS.clear();
        RESIDUALS.clear();
        readyTicks = 0;
    }

    private static final class DomainVisual {
        private final int playerEntityId;
        private final int seed;
        private int remainingTicks;
        private int storedSwords;
        private int newSwordIndex = -1;
        private int flashTicks;
        private int age;
        private boolean collapsing;
        private Vec3 impactDirection = new Vec3(0.0D, 0.0D, 1.0D);

        private DomainVisual(int playerEntityId, int duration, int seed) {
            this.playerEntityId = playerEntityId;
            this.remainingTicks = duration;
            this.seed = seed;
        }
    }

    private static final class ResidualVisual {
        private final int playerEntityId;
        private final int duration;
        private final int seed;
        private int age;

        private ResidualVisual(int playerEntityId, int duration, int seed) {
            this.playerEntityId = playerEntityId;
            this.duration = Math.max(1, duration);
            this.seed = seed;
        }
    }

    private VoidScatteringVfxClient() {
    }
}
