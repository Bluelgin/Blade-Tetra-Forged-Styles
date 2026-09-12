package dev.bladetetra.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.network.VoidScatteringVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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

/** Client reconstruction of the five stable void rifts around an active domain. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidScatteringVfxClient {
    private static final ResourceLocation READY_ICON = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "textures/gui/void_scattering_ready.png");
    private static final Map<Integer, DomainVisual> DOMAINS = new HashMap<>();
    private static final ArrayList<ResidualVisual> RESIDUALS = new ArrayList<>();
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
                    domain.storedSlots = Mth.clamp(packet.storedSlots(), 0, 5);
                    domain.flashSlot = Math.max(0, domain.storedSlots - 1);
                    domain.flashTicks = 9;
                    domain.remainingTicks = Math.max(domain.remainingTicks, packet.duration());
                }
            }
            case VoidScatteringVfxPacket.COLLAPSE -> {
                DomainVisual domain = DOMAINS.get(packet.playerEntityId());
                if (domain != null) {
                    domain.storedSlots = Mth.clamp(packet.storedSlots(), 0, 5);
                    domain.collapsing = true;
                    domain.remainingTicks = Math.max(8, packet.duration());
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
            domain.remainingTicks--;
            if (domain.flashTicks > 0) domain.flashTicks--;
            if (domain.remainingTicks <= 0
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
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = poses.last().pose();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawAll(buffer, matrix, camera.getPosition(), level,
                event.getPartialTick(), false);
        Tesselator.getInstance().end();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawAll(buffer, matrix, camera.getPosition(), level,
                event.getPartialTick(), true);
        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
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

    private static void drawAll(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            ClientLevel level, float partialTick, boolean glow) {
        for (DomainVisual domain : DOMAINS.values()) {
            Entity player = level.getEntity(domain.playerEntityId);
            if (player == null) continue;
            float age = domain.age + partialTick;
            Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
            for (int slot = 0; slot < 5; slot++) {
                int revealTick = slot == 0 ? 1 : slot <= 2 ? 4 : 8;
                float reveal = Mth.clamp((age - revealTick) / 5.0F, 0.0F, 1.0F);
                if (reveal <= 0.0F) continue;
                float collapse = domain.collapsing
                        ? Mth.clamp(domain.remainingTicks / 10.0F, 0.0F, 1.0F) : 1.0F;
                Vec3 slotCenter = slotCenter(center, domain.seed, slot, age);
                float closing = domain.collapsing ? 1.0F
                        : Mth.clamp((10.0F - domain.remainingTicks) / 10.0F,
                        0.0F, 1.0F);
                if (closing > 0.0F) {
                    slotCenter = center.lerp(slotCenter, 1.0D - closing * 0.22D);
                }
                boolean filled = slot < domain.storedSlots;
                float flash = slot == domain.flashSlot
                        ? domain.flashTicks / 9.0F : 0.0F;
                if (filled) flash = Math.max(flash, closing * 0.62F);
                drawCrack(buffer, matrix, camera, slotCenter,
                        domain.seed + slot * 71, reveal * collapse, filled, flash, glow);
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

    private static Vec3 slotCenter(Vec3 center, int seed, int slot, float age) {
        double base = Math.floorMod(seed, 360) * Math.PI / 180.0D;
        double[] spacing = {0.0D, 1.19D, 2.47D, 3.82D, 5.08D};
        double angle = base + spacing[slot];
        double radius = 1.78D + ((seed >>> (slot * 3)) & 3) * 0.11D;
        double height = 0.05D + (slot % 2) * 0.34D
                + Math.sin(age * 0.055D + slot * 1.7D) * 0.07D;
        return center.add(Math.cos(angle) * radius, height,
                Math.sin(angle) * radius);
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
        private int storedSlots;
        private int flashSlot = -1;
        private int flashTicks;
        private int age;
        private boolean collapsing;

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
