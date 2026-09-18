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
import dev.bladetetra.network.BladeCombatVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static dev.bladetetra.client.vfx.render.VfxPrimitives.band3d;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.color;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.planeBand;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.planeVector;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.ringHorizontal;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.ringVertical;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.texturedPlane;

/**
 * Short-lived combat geometry rendered with Minecraft's built-in position/color
 * shader. It deliberately avoids framebuffer effects and custom GLSL so it can
 * coexist with renderer optimizers and shader packs.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeCombatVfxClient {
    private static final ResourceLocation PARRY_TEXTURE = new ResourceLocation(
            BladeTetra.MOD_ID, "textures/effect/combat/mikage_parry.png");
    private static final ResourceLocation SLASH_ARC_TEXTURE = new ResourceLocation(
            BladeTetra.MOD_ID, "textures/effect/combat/mikage_slash_arc.png");
    private static final ResourceLocation GUARD_BREAK_TEXTURE = new ResourceLocation(
            BladeTetra.MOD_ID, "textures/effect/combat/mikage_guard_break.png");
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static int cameraTicks;
    private static int cameraTotal;
    private static float cameraStrength;

    public static void spawn(BladeCombatVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) {
            return;
        }
        int duration = switch (packet.type()) {
            case BladeCombatVfxPacket.MIKAGE_DEFEAT -> 55;
            case BladeCombatVfxPacket.STAGGER -> 14;
            case BladeCombatVfxPacket.PHASE_SHIFT -> 24;
            case BladeCombatVfxPacket.PERFECT_GUARD -> 11;
            default -> 8;
        };
        Effect incoming = new Effect(packet.type(),
                new Vec3(packet.x(), packet.y(), packet.z()),
                packet.yaw(), Math.max(0.1F, packet.intensity()), duration,
                packet.focusEntityId());
        int maximum = ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get();
        if (EFFECTS.size() >= maximum) {
            Effect disposable = EFFECTS.stream()
                    .filter(effect -> priority(effect.type) <= priority(incoming.type))
                    .max((left, right) -> Integer.compare(left.age, right.age))
                    .orElse(null);
            if (disposable == null) {
                return;
            }
            EFFECTS.remove(disposable);
        }
        EFFECTS.add(incoming);

        Minecraft minecraft = Minecraft.getInstance();
        if (ClientVisualConfig.ENABLE_PARRY_CAMERA_IMPACT.get()
                && minecraft.player != null
                && minecraft.player.getId() == packet.focusEntityId()
                && (packet.type() == BladeCombatVfxPacket.PARRY
                || packet.type() == BladeCombatVfxPacket.PERFECT_GUARD)) {
            cameraTicks = packet.type() == BladeCombatVfxPacket.PERFECT_GUARD ? 3 : 2;
            cameraTotal = cameraTicks;
            cameraStrength = packet.intensity()
                    * ClientVisualConfig.BLADE_COMBAT_CAMERA_INTENSITY.get().floatValue();
        }
    }

    private static int priority(int type) {
        return switch (type) {
            case BladeCombatVfxPacket.PERFECT_GUARD,
                    BladeCombatVfxPacket.PHASE_SHIFT,
                    BladeCombatVfxPacket.MIKAGE_DEFEAT -> 2;
            case BladeCombatVfxPacket.PARRY,
                    BladeCombatVfxPacket.STAGGER -> 1;
            default -> 0;
        };
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) {
            return;
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (++effect.age >= effect.duration) {
                iterator.remove();
            }
        }
        if (cameraTicks > 0) {
            cameraTicks--;
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (cameraTicks <= 0 || !ClientVisualConfig.ENABLE_PARRY_CAMERA_IMPACT.get()) {
            return;
        }
        float progress = (cameraTicks + (float) event.getPartialTick())
                / Math.max(1.0F, cameraTotal);
        float pulse = cameraStrength * progress;
        float phase = (cameraTotal - cameraTicks + (float) event.getPartialTick()) * 5.4F;
        event.setYaw(event.getYaw() + Mth.sin(phase) * pulse * 0.38F);
        event.setPitch(event.getPitch() - pulse * 0.24F);
        event.setRoll(event.getRoll() + Mth.cos(phase) * pulse * 0.72F);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || EFFECTS.isEmpty() || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) {
            return;
        }
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poses.last().pose();
        float global = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        for (Effect effect : EFFECTS) {
            drawEffect(buffer, matrix, effect, event.getPartialTick(), global);
        }
        Tesselator.getInstance().end();
        renderTexturePass(matrix, event.getPartialTick(), global);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void drawEffect(BufferBuilder buffer, Matrix4f matrix,
            Effect effect, float partialTick, float global) {
        float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0.0F, 1.0F);
        float fade = (1.0F - t) * (1.0F - t);
        float strength = effect.intensity * global;
        double yaw = Math.toRadians(effect.yaw);
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        int white = color(1.0F, 0.96F, 0.92F, fade * 0.96F);
        int red = color(1.0F, 0.08F, 0.12F, fade * 0.82F);
        int gold = color(1.0F, 0.58F, 0.12F, fade * 0.88F);
        int purple = color(0.52F, 0.08F, 0.68F, fade * 0.78F);

        if (effect.type == BladeCombatVfxPacket.MIKAGE_DEFEAT) {
            if (effect.age < 6) {
                planeBand(buffer, matrix, effect.position, right, 0.0D,
                        7.2D * strength, 0.055D, white);
            }
            double inward = Math.max(0.10D, 3.6D * (1.0D - t)) * strength;
            ringHorizontal(buffer, matrix, effect.position.add(0.0D, 0.12D, 0.0D),
                    inward, 0.045D, effect.focusEntityId == 1 ? purple : red, 44);
            int fragments = effect.focusEntityId == 1 ? 18 : 13;
            for (int i = 0; i < fragments; i++) {
                double angle = i * Math.PI * 2.0D / fragments + effect.yaw * 0.011D;
                Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                double lift = effect.position.y + 0.25D + i % 5 * 0.34D + t * 2.1D;
                Vec3 start = new Vec3(effect.position.x, lift, effect.position.z)
                        .add(radial.scale(0.25D + t * 0.45D));
                Vec3 end = start.add(radial.scale((0.55D + i % 3 * 0.24D) * strength))
                        .add(0.0D, 0.22D + i % 2 * 0.14D, 0.0D);
                band3d(buffer, matrix, start, end, 0.026D,
                        i % 3 == 0 ? white : effect.focusEntityId == 1 ? purple : red);
            }
            return;
        }

        if (effect.type == BladeCombatVfxPacket.PHASE_SHIFT) {
            boolean thirdPhase = effect.focusEntityId == 3;
            double radius = (1.0D + t * 5.5D) * strength;
            ringHorizontal(buffer, matrix, effect.position.add(0.0D, 0.15D, 0.0D),
                    radius, 0.09D + 0.16D * (1.0D - t),
                    thirdPhase ? red : purple, 48);
            ringHorizontal(buffer, matrix, effect.position.add(0.0D, 1.0D, 0.0D),
                    radius * 0.72D, 0.055D, white, 40);
            for (int i = 0; i < 7; i++) {
                double angle = i * Math.PI / 7.0D + t * 0.9D;
                Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
                Vec3 center = effect.position.add(radial.scale(1.0D + t * 2.0D))
                        .add(0.0D, 0.45D + i * 0.19D, 0.0D);
                band3d(buffer, matrix, center.subtract(radial.scale(1.2D)),
                        center.add(radial.scale(1.2D)), 0.055D,
                        thirdPhase ? red : purple);
            }
            if (thirdPhase) {
                Vec3 postRight = right.normalize();
                Vec3 base = effect.position.add(0.0D, -0.8D, 0.0D);
                Vec3 left = base.subtract(postRight.scale(2.2D * strength));
                Vec3 rightPost = base.add(postRight.scale(2.2D * strength));
                band3d(buffer, matrix, left, left.add(0.0D, 5.2D * strength, 0.0D),
                        0.10D, red);
                band3d(buffer, matrix, rightPost,
                        rightPost.add(0.0D, 5.2D * strength, 0.0D), 0.10D, red);
                Vec3 upper = base.add(0.0D, 4.45D * strength, 0.0D);
                band3d(buffer, matrix, upper.subtract(postRight.scale(3.1D * strength)),
                        upper.add(postRight.scale(3.1D * strength)), 0.12D, red);
                Vec3 top = base.add(0.0D, 5.25D * strength, 0.0D);
                band3d(buffer, matrix, top.subtract(postRight.scale(3.55D * strength)),
                        top.add(postRight.scale(3.55D * strength)), 0.15D, white);
            }
            return;
        }

        double expansion = Mth.sin(t * (float) Math.PI * 0.72F);
        double radius = (0.34D + expansion * (effect.type == BladeCombatVfxPacket.STAGGER
                ? 3.2D : effect.type == BladeCombatVfxPacket.PERFECT_GUARD ? 2.15D : 1.45D))
                * strength;
        ringVertical(buffer, matrix, effect.position, right, radius,
                0.045D + 0.10D * (1.0D - t),
                effect.type == BladeCombatVfxPacket.STAGGER ? gold : white, 40);

        if (effect.type == BladeCombatVfxPacket.STAGGER) {
            for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI * 2.0D / 10.0D + 0.28D;
                Vec3 dir = planeVector(right, Math.cos(angle), Math.sin(angle));
                Vec3 start = effect.position.add(dir.scale(0.45D + t * 0.8D));
                Vec3 end = start.add(dir.scale((1.0D + i % 3 * 0.24D) * strength));
                band3d(buffer, matrix, start, end, 0.035D, i % 2 == 0 ? white : gold);
            }
            return;
        }
        // The generated sprite supplies the irregular pixel brushwork. Keep only
        // the expanding geometry ring here so the result still has real depth.
        ringVertical(buffer, matrix, effect.position, right, radius * 0.62D,
                0.028D, effect.type == BladeCombatVfxPacket.PERFECT_GUARD ? gold : white, 32);
    }

    private static void renderTexturePass(Matrix4f matrix, float partialTick, float global) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (Effect effect : EFFECTS) {
            float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0.0F, 1.0F);
            float fade = (1.0F - t) * (1.0F - t);
            float ease = Mth.sin(Math.min(1.0F, t * 1.6F) * (float) Math.PI * 0.5F);
            float strength = effect.intensity * global;
            if (effect.type == BladeCombatVfxPacket.PARRY
                    || effect.type == BladeCombatVfxPacket.PERFECT_GUARD) {
                RenderSystem.setShaderTexture(0, PARRY_TEXTURE);
                double size = (effect.type == BladeCombatVfxPacket.PERFECT_GUARD
                        ? 2.75D : 1.95D) * strength * (0.70D + ease * 0.30D);
                texturedPlane(matrix, effect.position, effect.yaw, size,
                        fade * (effect.type == BladeCombatVfxPacket.PERFECT_GUARD ? 1.0F : 0.88F));
            } else if (effect.type == BladeCombatVfxPacket.STAGGER) {
                RenderSystem.setShaderTexture(0, GUARD_BREAK_TEXTURE);
                texturedPlane(matrix, effect.position, effect.yaw,
                        (2.4D + t * 1.65D) * strength, fade * 0.94F);
            } else if (effect.type == BladeCombatVfxPacket.PHASE_SHIFT) {
                if (effect.focusEntityId == 3) {
                    RenderSystem.setShaderTexture(0, SLASH_ARC_TEXTURE);
                    for (int i = 0; i < 3; i++) {
                        texturedPlane(matrix,
                                effect.position.add(0.0D, i * 0.18D - 0.18D, 0.0D),
                                effect.yaw + i * 60.0F,
                                (3.2D + t * 2.4D + i * 0.32D) * strength,
                                fade * (0.78F - i * 0.12F));
                    }
                }
            } else if (effect.type == BladeCombatVfxPacket.MIKAGE_DEFEAT) {
                RenderSystem.setShaderTexture(0, SLASH_ARC_TEXTURE);
                texturedPlane(matrix, effect.position, effect.yaw,
                        (2.8D + t * 4.2D) * strength, fade * 0.72F);
            }
        }
    }

    private static final class Effect {
        final int type;
        final Vec3 position;
        final float yaw;
        final float intensity;
        final int duration;
        final int focusEntityId;
        int age;

        Effect(int type, Vec3 position, float yaw, float intensity, int duration,
                int focusEntityId) {
            this.type = type;
            this.position = position;
            this.yaw = yaw;
            this.intensity = intensity;
            this.duration = duration;
            this.focusEntityId = focusEntityId;
        }
    }

    private BladeCombatVfxClient() {
    }
}
