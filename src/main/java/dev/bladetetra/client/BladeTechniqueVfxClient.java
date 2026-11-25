package dev.bladetetra.client;

import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;
import static dev.bladetetra.client.BladeTechniqueFusionVfxRenderer.*;
import static dev.bladetetra.client.BladeTechniqueStyleVfxRenderer.*;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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

/**
 * Reconstructs fusion and Akatsuki visual timelines from synced entities.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeTechniqueVfxClient {
    static final ResourceLocation SLASH_DIM_MODEL = new ResourceLocation(
            "slashblade", "model/util/slashdim.obj");
    static final ResourceLocation SLASH_DIM_TEXTURE = new ResourceLocation(
            "slashblade", "model/util/slashdim.png");
    static final MultiBufferSource.BufferSource MODEL_BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(256 * 1024));
    static final List<Effect> EFFECTS = new ArrayList<>();
    private static int cameraTicks;
    private static int cameraTotal;
    private static float cameraStrength;
    private static ClientLevel activeLevel;

    public static void spawn(BladeTechniqueVfxPacket packet) {
        boolean akatsukiTint = isAkatsukiTint(packet.type());
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get() && !akatsukiTint) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            cameraTicks = 0;
            activeLevel = minecraft.level;
        }
        if (packet.type() == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                    && effect.targetEntityId == packet.targetEntityId());
            if (minecraft.player != null
                    && minecraft.player.getId() == packet.sourceEntityId()) {
                AkatsukiExecutionTint.endAkatsuki(packet.duration());
            }
        } else if (packet.type() == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                && minecraft.player != null
                && minecraft.player.getId() == packet.sourceEntityId()) {
            AkatsukiExecutionTint.beginAkatsuki(packet.duration());
        }
        if (minecraft.player != null && !akatsukiTint) {
            double maximum = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
            if (minecraft.player.distanceToSqr(packet.startX(), packet.startY(), packet.startZ())
                    > maximum * maximum) {
                return;
            }
        }
        if (packet.type() == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS
                || packet.type() == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                    && effect.targetEntityId == packet.targetEntityId());
        }
        Effect incoming = new Effect(packet);
        addEffect(incoming);
        triggerCamera(packet, minecraft);
    }

    private static boolean isAkatsukiTint(int type) {
        return type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                || type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END;
    }

    private static void addEffect(Effect incoming) {
        int maximum = ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get();
        if (EFFECTS.size() >= maximum) {
            Effect disposable = null;
            for (Effect existing : EFFECTS) {
                if (existing.priority <= incoming.priority
                        && (disposable == null || existing.priority < disposable.priority
                        || existing.priority == disposable.priority && existing.age > disposable.age)) {
                    disposable = existing;
                }
            }
            if (disposable == null) {
                return;
            }
            EFFECTS.remove(disposable);
        }
        EFFECTS.add(incoming);
    }

    private static void triggerCamera(BladeTechniqueVfxPacket packet, Minecraft minecraft) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_CAMERA_IMPACT.get()
                || minecraft.player == null) return;
        float base = switch (packet.type()) {
            case BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT -> 1.05F;
            case BladeTechniqueVfxPacket.TWIN_PHASE_YASHA -> 0.68F;
            case BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU -> 0.84F;
            default -> 0.0F;
        };
        if (base <= 0.0F) return;
        double distance = minecraft.player.distanceToSqr(packet.startX(), packet.startY(), packet.startZ());
        float falloff = Mth.clamp(1.0F - (float) Math.sqrt(distance) / 32.0F, 0.18F, 1.0F);
        float perspective = minecraft.options.getCameraType().isFirstPerson() ? 0.76F : 1.0F;
        cameraTicks = base >= 2.0F ? 24 : base >= 1.2F ? 8 : base >= 0.7F ? 4 : 3;
        cameraTotal = cameraTicks;
        cameraStrength = base * falloff * perspective
                * ClientVisualConfig.BLADE_COMBAT_CAMERA_INTENSITY.get().floatValue();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (level != activeLevel) {
            EFFECTS.clear();
            cameraTicks = 0;
            activeLevel = level;
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            effect.tick(level);
            if (effect.age >= effect.duration) {
                iterator.remove();
            }
        }
        if (cameraTicks > 0) cameraTicks--;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (cameraTicks <= 0 || !ClientVisualConfig.ENABLE_BLADE_COMBAT_CAMERA_IMPACT.get()) return;
        float progress = (cameraTicks + (float) event.getPartialTick())
                / Math.max(1.0F, cameraTotal);
        float elapsed = cameraTotal - cameraTicks + (float) event.getPartialTick();
        float phase = elapsed * (cameraTotal >= 20 ? 7.4F : 4.8F);
        float impulse = cameraStrength * progress;
        if (cameraTotal >= 20) {
            float firstBlow = Mth.clamp(1.0F - elapsed / 3.2F, 0.0F, 1.0F);
            event.setYaw(event.getYaw() + Mth.sin(phase * 0.83F) * impulse * 0.48F);
            event.setPitch(event.getPitch() - impulse * (0.32F + firstBlow * 0.74F)
                    + Mth.sin(phase * 1.31F) * impulse * 0.18F);
            event.setRoll(event.getRoll() + Mth.cos(phase) * impulse * 0.78F);
        } else {
            event.setYaw(event.getYaw() + Mth.sin(phase) * impulse * 0.34F);
            event.setPitch(event.getPitch() - impulse * 0.20F);
            event.setRoll(event.getRoll() + Mth.cos(phase) * impulse * 0.62F);
        }
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
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = poses.last().pose();
        float global = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        int quality = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Effect effect : EFFECTS) {
            draw(buffer, matrix, camera.getPosition(), effect, event.getPartialTick(), global, quality);
        }
        Tesselator.getInstance().end();
        renderAkatsukiJudgementWinds(poses, event.getPartialTick(), global, quality);
        renderTwinFoxModels(poses, event.getPartialTick(), global);
        renderTwinPhaseModels(poses, event.getPartialTick(), global);
        renderTwinPhaseNativeWinds(poses, event.getPartialTick(), global, quality);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void draw(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Effect effect, float partialTick, float global, int quality) {
        float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0.0F, 1.0F);
        float strength = effect.intensity * global;
        switch (effect.type) {
            case BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON ->
                    drawAkatsukiFinalMoon(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END ->
                    drawAkatsukiFinalMoonEnd(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT ->
                    drawTwinFoxMoonhunt(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT ->
                    drawTwinFoxMoonhuntImpact(buffer, matrix, camera, effect, t, strength, quality);
            default -> {
            }
        }
    }

    static final class Effect {
        final int type;
        final int priority;
        final float yaw;
        final float intensity;
        final int sourceEntityId;
        final int targetEntityId;
        final int duration;
        final int seed;
        Vec3 start;
        Vec3 end;
        final Vec3 initialEnd;
        int age;

        Effect(BladeTechniqueVfxPacket packet) {
            type = packet.type();
            priority = priority(packet.type());
            start = new Vec3(packet.startX(), packet.startY(), packet.startZ());
            end = new Vec3(packet.endX(), packet.endY(), packet.endZ());
            initialEnd = end;
            yaw = packet.yaw();
            intensity = Math.max(0.1F, packet.intensity());
            sourceEntityId = packet.sourceEntityId();
            targetEntityId = packet.targetEntityId();
            duration = Math.max(1, packet.duration());
            seed = packet.seed();
        }

        private static int priority(int type) {
            return switch (type) {
                case BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON,
                        BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END,
                        BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT,
                        BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT,
                        BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS,
                        BladeTechniqueVfxPacket.TWIN_PHASE_YASHA,
                        BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU -> 2;
                case BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK -> 1;
                default -> 0;
            };
        }

        void tick(ClientLevel level) {
            age++;
            Entity source = level == null || sourceEntityId < 0
                    ? null : level.getEntity(sourceEntityId);
            Entity target = level == null || targetEntityId < 0
                    ? null : level.getEntity(targetEntityId);
            if (type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT) {
                if (target != null) end = initialEnd.lerp(target.getBoundingBox().getCenter(), .35D);
                return;
            }
            if (type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT) return;
            if (source != null) {
                start = entityCenter(source);
            }
            if (target != null) end = entityCenter(target);
        }
    }

    private BladeTechniqueVfxClient() {
    }
}
