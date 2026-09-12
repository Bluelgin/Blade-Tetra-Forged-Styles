package dev.bladetetra.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.VoidScatteringVfxPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Owner-only first-person view of the inside of Void Scattering's dome.
 * This never samples the world framebuffer: it only draws a translucent edge veil,
 * so the center of the player's view remains clear and shader-pack conflicts stay low.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidScatteringFirstPersonClient {
    private static final int CAPTURE_FLASH_TICKS = 12;
    private static final int OPENING_TICKS = 12;

    private static int activePlayerEntityId = -1;
    private static int remainingTicks;
    private static int age;
    private static int storedSwords;
    private static int captureCount;
    private static int flashTicks;
    private static int collapseTicks;
    private static int collapseDuration;
    private static boolean collapsing;
    private static Vec3 impactDirection = new Vec3(0.0D, 0.0D, 1.0D);

    public static void accept(VoidScatteringVfxPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || packet.playerEntityId() != minecraft.player.getId()) {
            return;
        }

        switch (packet.type()) {
            case VoidScatteringVfxPacket.OPEN -> {
                activePlayerEntityId = packet.playerEntityId();
                remainingTicks = Math.max(1, packet.duration());
                age = 0;
                storedSwords = 0;
                captureCount = 0;
                flashTicks = 0;
                collapseTicks = 0;
                collapseDuration = 0;
                collapsing = false;
                impactDirection = new Vec3(0.0D, 0.0D, 1.0D);
            }
            case VoidScatteringVfxPacket.CAPTURE -> {
                if (!isActiveFor(packet.playerEntityId())) return;
                storedSwords = Mth.clamp(packet.storedSlots(), 0, 6);
                captureCount = Math.min(5, captureCount + 1);
                flashTicks = CAPTURE_FLASH_TICKS;
                remainingTicks = Math.max(remainingTicks, packet.duration());
                Vec3 impact = new Vec3(packet.impactX(), packet.impactY(), packet.impactZ());
                if (impact.lengthSqr() > 1.0E-5D) {
                    impactDirection = impact.normalize();
                }
            }
            case VoidScatteringVfxPacket.COLLAPSE -> {
                if (!isActiveFor(packet.playerEntityId())) return;
                storedSwords = Mth.clamp(packet.storedSlots(), 0, 6);
                collapsing = true;
                collapseDuration = Math.max(1, packet.duration());
                collapseTicks = collapseDuration;
            }
            case VoidScatteringVfxPacket.CANCEL -> {
                if (isActiveFor(packet.playerEntityId())) reset();
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }
        if (!isActiveFor(minecraft.player.getId()) || minecraft.isPaused()) {
            return;
        }

        age++;
        if (flashTicks > 0) flashTicks--;
        if (collapsing) {
            if (--collapseTicks <= 0) {
                reset();
            }
        } else {
            remainingTicks--;
            // Network delivery is reliable, but do not leave a permanent veil if a
            // resource reload or unusual disconnect loses the final collapse packet.
            if (remainingTicks < -40) {
                reset();
            }
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !isActiveFor(minecraft.player.getId())
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }

        float partialTick = event.getPartialTick();
        float opening = Mth.clamp((age + partialTick) / OPENING_TICKS, 0.0F, 1.0F);
        float captureFlash = Mth.clamp((flashTicks + partialTick) / CAPTURE_FLASH_TICKS,
                0.0F, 1.0F);
        float instability = Mth.clamp(captureCount / 5.0F, 0.0F, 1.0F);
        float storedRatio = Mth.clamp(storedSwords / 6.0F, 0.0F, 1.0F);
        float collapse = collapsing && collapseDuration > 0
                ? Mth.clamp(1.0F - (collapseTicks + partialTick) / collapseDuration,
                        0.0F, 1.0F)
                : 0.0F;

        if (!renderShaderVeil(event, minecraft, opening, captureFlash,
                instability, storedRatio, collapse)) {
            renderFallbackVeil(event, opening, captureFlash, instability, collapse);
        }
    }

    private static boolean renderShaderVeil(RenderGuiEvent.Post event,
            Minecraft minecraft, float opening, float captureFlash,
            float instability, float storedRatio, float collapse) {
        if (!ClientVisualConfig.ENABLE_VOID_SCATTERING_SHADER.get()) return false;
        ShaderInstance shader = VoidScatteringShaders.veilShader();
        if (shader == null) return false;

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) return false;

        Vec3 impact = projectImpactToScreen(minecraft);
        float intensity = ClientVisualConfig.VOID_SCATTERING_SHADER_INTENSITY.get()
                .floatValue();

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(() -> shader);
        setFloat(shader, "VeilTime",
                (minecraft.level.getGameTime() + event.getPartialTick()) * 0.045F);
        setFloat(shader, "VeilIntensity", intensity);
        setFloat(shader, "Opening", opening);
        setFloat(shader, "CaptureFlash", captureFlash);
        setFloat(shader, "Instability", instability);
        setFloat(shader, "StoredRatio", storedRatio);
        setFloat(shader, "Collapse", collapse);
        setFloat(shader, "Aspect", width / (float) height);
        if (shader.getUniform("ImpactScreen") != null) {
            shader.getUniform("ImpactScreen").set((float) impact.x, (float) impact.y);
        }

        Matrix4f matrix = event.getGuiGraphics().pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        guiVertex(buffer, matrix, 0.0F, 0.0F, 0.0F, 0.0F);
        guiVertex(buffer, matrix, width, 0.0F, 1.0F, 0.0F);
        guiVertex(buffer, matrix, width, height, 1.0F, 1.0F);
        guiVertex(buffer, matrix, 0.0F, height, 0.0F, 1.0F);
        Tesselator.getInstance().end();

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        return true;
    }

    private static void renderFallbackVeil(RenderGuiEvent.Post event,
            float opening, float captureFlash, float instability, float collapse) {
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        int edgeX = Math.max(10, width / 11);
        int edgeY = Math.max(8, height / 9);
        int alpha = Mth.clamp(Math.round((22.0F + instability * 25.0F
                + captureFlash * 34.0F + collapse * 28.0F) * opening), 0, 105);
        int violetAlpha = Mth.clamp(Math.round((12.0F + instability * 28.0F
                + captureFlash * 46.0F + collapse * 42.0F) * opening), 0, 120);
        int dark = alpha << 24 | 0x10081D;
        int violet = violetAlpha << 24 | 0x6E4AA2;

        event.getGuiGraphics().fill(0, 0, width, edgeY, dark);
        event.getGuiGraphics().fill(0, height - edgeY, width, height, dark);
        event.getGuiGraphics().fill(0, edgeY, edgeX, height - edgeY, dark);
        event.getGuiGraphics().fill(width - edgeX, edgeY, width, height - edgeY, dark);

        int line = Math.max(1, Math.round(1.0F + instability + captureFlash));
        event.getGuiGraphics().fill(0, 0, width, line, violet);
        event.getGuiGraphics().fill(0, height - line, width, height, violet);
        event.getGuiGraphics().fill(0, 0, line, height, violet);
        event.getGuiGraphics().fill(width - line, 0, width, height, violet);
    }

    private static Vec3 projectImpactToScreen(Minecraft minecraft) {
        Vec3 direction = impactDirection.lengthSqr() < 1.0E-5D
                ? new Vec3(0.0D, 0.0D, 1.0D) : impactDirection.normalize();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(look).normalize();
        double x = Mth.clamp(direction.dot(right), -1.0D, 1.0D);
        double y = Mth.clamp(-direction.dot(up), -1.0D, 1.0D);
        double length = Math.sqrt(x * x + y * y);
        if (length < 0.12D) {
            return new Vec3(0.0D, -0.82D, 0.0D);
        }
        double scale = 0.84D / Math.max(0.84D, length);
        return new Vec3(x * scale, y * scale, 0.0D);
    }

    private static void setFloat(ShaderInstance shader, String name, float value) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(value);
        }
    }

    private static void guiVertex(BufferBuilder buffer, Matrix4f matrix,
            float x, float y, float u, float v) {
        buffer.vertex(matrix, x, y, 0.0F)
                .uv(u, v).color(255, 255, 255, 255).endVertex();
    }

    private static boolean isActiveFor(int playerEntityId) {
        return activePlayerEntityId == playerEntityId;
    }

    private static void reset() {
        activePlayerEntityId = -1;
        remainingTicks = 0;
        age = 0;
        storedSwords = 0;
        captureCount = 0;
        flashTicks = 0;
        collapseTicks = 0;
        collapseDuration = 0;
        collapsing = false;
        impactDirection = new Vec3(0.0D, 0.0D, 1.0D);
    }

    private VoidScatteringFirstPersonClient() {
    }
}
