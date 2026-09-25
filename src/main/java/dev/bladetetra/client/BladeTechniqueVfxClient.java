package dev.bladetetra.client;

import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;
import static dev.bladetetra.client.BladeTechniqueFusionVfxRenderer.*;
import static dev.bladetetra.client.BladeTechniqueMikageVfxRenderer.*;
import static dev.bladetetra.client.BladeTechniqueStyleVfxRenderer.*;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
 * Longer-lived technique geometry: tracked ribbons, deterministic warning
 * lines, seal links and the graduation gate timeline. The server never streams
 * per-tick vertices; this client reconstructs them from synced entities.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BladeTechniqueVfxClient {
    private static final ResourceLocation BOUNDARY_EXECUTION_TEXTURE = new ResourceLocation(
            BladeTetra.MOD_ID, "textures/effect/combat/mikage_boundary_execution.png");
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
        boolean boundaryCore = isBoundaryCore(packet.type());
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get() && !boundaryCore) {
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
                MikageBloodMoonDomainClient.endAkatsuki(packet.duration());
            }
        } else if (packet.type() == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                && minecraft.player != null
                && minecraft.player.getId() == packet.sourceEntityId()) {
            MikageBloodMoonDomainClient.beginAkatsuki(packet.duration());
        }
        if (minecraft.player != null && !boundaryCore) {
            double maximum = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
            if (minecraft.player.distanceToSqr(packet.startX(), packet.startY(), packet.startZ())
                    > maximum * maximum) {
                return;
            }
        }
        if (packet.type() == BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP) {
            for (Effect effect : EFFECTS) {
                if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                        && effect.seed == packet.seed()) {
                    effect.gapAlong = packet.intensity();
                    effect.gapTicks = packet.duration();
                    return;
                }
            }
            Effect recovered = recoveredBoundaryWall(packet);
            recovered.gapAlong = packet.intensity();
            recovered.gapTicks = packet.duration();
            addEffect(recovered);
            return;
        }
        if (packet.type() == BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP_WARNING) {
            for (Effect effect : EFFECTS) {
                if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                        && effect.seed == packet.seed()) {
                    effect.pendingGapAlong = packet.intensity();
                    effect.gapWarningTicks = packet.duration();
                    return;
                }
            }
            Effect recovered = recoveredBoundaryWall(packet);
            recovered.pendingGapAlong = packet.intensity();
            recovered.gapWarningTicks = packet.duration();
            addEffect(recovered);
            return;
        }
        if (packet.type() == BladeTechniqueVfxPacket.BOUNDARY_WALL_BREAK) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL);
        } else if (packet.type() == BladeTechniqueVfxPacket.BOUNDARY_WALL) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                    && effect.seed == packet.seed());
        } else if (packet.type() == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                    && effect.targetEntityId == packet.targetEntityId());
        } else if (packet.type() == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK) {
            EFFECTS.removeIf(effect -> effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                    && effect.targetEntityId == packet.targetEntityId());
        }
        Effect incoming = new Effect(packet);
        addEffect(incoming);
        triggerCamera(packet, minecraft);
    }

    private static boolean isBoundaryCore(int type) {
        return type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                || type == BladeTechniqueVfxPacket.BOUNDARY_WALL_BREAK
                || type == BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP
                || type == BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP_WARNING
                || type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                || type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END;
    }

    private static Effect recoveredBoundaryWall(BladeTechniqueVfxPacket packet) {
        return new Effect(new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_WALL,
                packet.startX(), packet.startY(), packet.startZ(),
                packet.endX(), packet.endY(), packet.endZ(),
                packet.yaw(), 1.0F, -1, -1, 1_000_000, packet.seed()));
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
            case BladeTechniqueVfxPacket.CAGE_SUCCESS,
                    BladeTechniqueVfxPacket.MOON_ECHO_TRUE,
                    BladeTechniqueVfxPacket.COUNTER_CLASH -> 0.72F;
            case BladeTechniqueVfxPacket.CAGE_FAILURE,
                    BladeTechniqueVfxPacket.MOON_ECHO_FAILURE,
                    BladeTechniqueVfxPacket.SWORD_WHEEL_BREAK,
                    BladeTechniqueVfxPacket.MIRROR_DUEL_FAILURE,
                    BladeTechniqueVfxPacket.SCISSOR_FAILURE -> 0.52F;
            case BladeTechniqueVfxPacket.SCISSOR_BREAK -> 0.72F;
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH -> 0.18F;
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH_IMPACT -> 2.45F;
            case BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT -> 1.05F;
            case BladeTechniqueVfxPacket.TWIN_PHASE_YASHA -> 0.68F;
            case BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU -> 0.84F;
            default -> 0.0F;
        };
        if (base <= 0.0F) return;
        double distance = minecraft.player.distanceToSqr(packet.startX(), packet.startY(), packet.startZ());
        float falloff = packet.type() == BladeTechniqueVfxPacket.BOUNDARY_FLASH_IMPACT
                ? 1.0F : Mth.clamp(1.0F - (float) Math.sqrt(distance) / 32.0F, 0.18F, 1.0F);
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
        boolean optionalVfx = ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || EFFECTS.isEmpty()
                || (!optionalVfx && EFFECTS.stream().noneMatch(
                effect -> effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL))) {
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
        float global = optionalVfx
                ? ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue() : 0.58F;
        int quality = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();

        boolean hasSolidCore = EFFECTS.stream().anyMatch(effect ->
                isSolidCoreType(effect.type)
                        && (optionalVfx || effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL));
        if (hasSolidCore) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Effect effect : EFFECTS) {
                if (!optionalVfx && effect.type != BladeTechniqueVfxPacket.BOUNDARY_WALL) continue;
                float effectGlobal = effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                        ? Math.max(0.58F, global) : global;
                drawSolidCore(buffer, matrix, camera.getPosition(), effect,
                        event.getPartialTick(), effectGlobal, quality);
            }
            Tesselator.getInstance().end();
        }

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Effect effect : EFFECTS) {
            if (!optionalVfx && effect.type != BladeTechniqueVfxPacket.BOUNDARY_WALL) continue;
            float effectGlobal = effect.type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                    ? Math.max(0.58F, global) : global;
            draw(buffer, matrix, camera.getPosition(), effect,
                    event.getPartialTick(), effectGlobal, quality);
        }
        Tesselator.getInstance().end();
        if (optionalVfx) {
            renderAkatsukiJudgementWinds(poses, event.getPartialTick(), global, quality);
            renderTwinFoxModels(poses, event.getPartialTick(), global);
            renderTwinPhaseModels(poses, event.getPartialTick(), global);
            renderTwinPhaseNativeWinds(poses, event.getPartialTick(), global, quality);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE);
            renderBoundaryExecutionTexture(matrix, event.getPartialTick(), global);
        }
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void renderBoundaryExecutionTexture(Matrix4f matrix,
            float partialTick, float global) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BOUNDARY_EXECUTION_TEXTURE);
        for (Effect effect : EFFECTS) {
            if (effect.type != BladeTechniqueVfxPacket.BOUNDARY_FLASH
                    && effect.type != BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE) continue;
            float age = effect.age + partialTick;
            float alpha;
            double scale;
            if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_FLASH) {
                float reveal = Mth.clamp((age - 38.0F) / 126.0F, 0.0F, 1.0F);
                alpha = reveal * (0.72F + 0.18F * Mth.sin(age * 0.17F));
                scale = 0.72D + reveal * 0.28D;
            } else {
                float fade = Mth.clamp(1.0F - age / 32.0F, 0.0F, 1.0F);
                alpha = fade;
                scale = 1.0D + age * 0.012D;
            }
            if (alpha <= 0.001F) continue;
            Vec3 forward = effect.end.subtract(effect.start)
                    .multiply(1.0D, 0.0D, 1.0D);
            if (forward.lengthSqr() < 0.0001D) forward = yawDirection(effect.yaw);
            else forward = forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            Vec3 center = effect.start.subtract(forward.scale(0.82D))
                    .add(0.0D, 0.8D, 0.0D);
            boundaryExecutionPlane(matrix, center,
                    right.scale(3.6D * scale * effect.intensity * global),
                    new Vec3(0.0D, 5.4D * scale * effect.intensity * global, 0.0D),
                    alpha * 0.58F);
        }
    }

    private static void boundaryExecutionPlane(Matrix4f matrix, Vec3 center,
            Vec3 axisU, Vec3 axisV, float alpha) {
        Vec3 topLeft = center.subtract(axisU).add(axisV);
        Vec3 bottomLeft = center.subtract(axisU).subtract(axisV);
        Vec3 bottomRight = center.add(axisU).subtract(axisV);
        Vec3 topRight = center.add(axisU).add(axisV);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textureVertex(buffer, matrix, topLeft, 0.0F, 0.0F, 255, 255, 255, alpha);
        textureVertex(buffer, matrix, bottomLeft, 0.0F, 1.0F, 255, 255, 255, alpha);
        textureVertex(buffer, matrix, bottomRight, 1.0F, 1.0F, 255, 255, 255, alpha);
        textureVertex(buffer, matrix, topRight, 1.0F, 0.0F, 255, 255, 255, alpha);
        Tesselator.getInstance().end();
    }

    private static void draw(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Effect effect, float partialTick, float global, int quality) {
        float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0.0F, 1.0F);
        float strength = effect.intensity * global;
        switch (effect.type) {
            case BladeTechniqueVfxPacket.PURSUIT_RETURN ->
                    drawPursuitReturn(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.MIRROR_DUEL ->
                    drawMirrorDuel(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.SEAL_LINK ->
                    drawSealLink(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.SEAL_BREAK ->
                    drawSealBreak(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.SEAL_SUCCESS ->
                    drawSealSuccess(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.SEAL_FAILURE ->
                    drawSealFailure(buffer, matrix, effect, t, strength);
            case BladeTechniqueVfxPacket.COUNTER_CLASH ->
                    drawCounterClash(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.BOUNDARY_STRIKE ->
                    drawBoundaryStrike(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME ->
                    drawBoundarySuppressionFlame(buffer, matrix, camera, effect,
                            t, strength, quality, false);
            case BladeTechniqueVfxPacket.TORII_CAGE ->
                    drawToriiCage(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.CAGE_PULSE ->
                    drawCagePulse(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.CAGE_SUCCESS ->
                    drawCageResult(buffer, matrix, camera, effect, t, strength, true);
            case BladeTechniqueVfxPacket.CAGE_FAILURE ->
                    drawCageResult(buffer, matrix, camera, effect, t, strength, false);
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH ->
                    drawBoundaryFlash(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE ->
                    drawBoundaryFlashRelease(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH_IMPACT ->
                    drawBoundaryFlashImpact(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_CHARGE ->
                    drawBoundaryCharge(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_WALL ->
                    drawBoundaryWall(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_WALL_BREAK ->
                    drawBoundaryWallBreak(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.TORII_SWEEP ->
                    drawToriiSweep(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.SCISSOR_FEINT ->
                    drawScissorResult(buffer, matrix, camera, effect, t, strength, 0);
            case BladeTechniqueVfxPacket.SCISSOR_GUARD ->
                    drawScissorResult(buffer, matrix, camera, effect, t, strength, 1);
            case BladeTechniqueVfxPacket.SCISSOR_FAILURE ->
                    drawScissorResult(buffer, matrix, camera, effect, t, strength, 2);
            case BladeTechniqueVfxPacket.SCISSOR_BREAK ->
                    drawScissorResult(buffer, matrix, camera, effect, t, strength, 3);
            case BladeTechniqueVfxPacket.PURSUIT_LOCK ->
                    drawPursuitLock(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.PURSUIT_SWORD ->
                    drawPursuitSword(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.MOON_ECHO_FIELD ->
                    drawMoonEchoField(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.MOON_ECHO_FALSE,
                    BladeTechniqueVfxPacket.MOON_ECHO_FAILURE ->
                    drawMoonEchoResult(buffer, matrix, camera, effect, t, strength, false);
            case BladeTechniqueVfxPacket.MOON_ECHO_TRUE ->
                    drawMoonEchoResult(buffer, matrix, camera, effect, t, strength, true);
            case BladeTechniqueVfxPacket.SWORD_WHEEL ->
                    drawSwordWheel(buffer, matrix, camera, effect, t, strength, quality);
            case BladeTechniqueVfxPacket.SWORD_WHEEL_BREAK ->
                    drawSwordWheelBreak(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.MIRROR_DUEL_FAILURE ->
                    drawMirrorFailure(buffer, matrix, camera, effect, t, strength);
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







    /**
     * Adds SlashBlade's mature judgement-cut wind motion around Kikouku's custom
     * meshes. Only the {@code wind} group is drawn, so the original black-hole
     * center and every gameplay effect remain absent.
     */






    /**
     * Reuses SlashBlade's judgement-cut wind mesh without rendering its central
     * {@code base} group. This is deliberately client-only: the execution's
     * server-authoritative pulses remain the sole source of damage and knockback.
     */




















































    /**
     * Builds a curved, tapered polygonal flame body. Unlike a billboard this
     * has an actual horizontal cross-section, so walking around the boundary
     * changes its silhouette and never collapses it into a flat sheet.
     */














































































    static final class Effect {
        final int type;
        final int priority;
        final float yaw;
        final float intensity;
        final int sourceEntityId;
        final int targetEntityId;
        final int duration;
        final int seed;
        final List<Vec3> history = new ArrayList<>();
        Vec3 start;
        Vec3 end;
        final Vec3 initialEnd;
        float gapAlong = -1.0F;
        int gapTicks;
        float pendingGapAlong = -1.0F;
        int gapWarningTicks;
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
            history.add(start);
        }

        private static int priority(int type) {
            if (type == BladeTechniqueVfxPacket.BOUNDARY_WALL) return 3;
            return switch (type) {
                case BladeTechniqueVfxPacket.SEAL_FAILURE,
                        BladeTechniqueVfxPacket.SEAL_SUCCESS,
                        BladeTechniqueVfxPacket.COUNTER_CLASH,
                        BladeTechniqueVfxPacket.BOUNDARY_STRIKE,
                        BladeTechniqueVfxPacket.TORII_CAGE,
                        BladeTechniqueVfxPacket.CAGE_SUCCESS,
                        BladeTechniqueVfxPacket.CAGE_FAILURE,
                        BladeTechniqueVfxPacket.BOUNDARY_FLASH,
                        BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE,
                        BladeTechniqueVfxPacket.BOUNDARY_FLASH_IMPACT,
                        BladeTechniqueVfxPacket.BOUNDARY_CHARGE,
                        BladeTechniqueVfxPacket.BOUNDARY_WALL_BREAK,
                        BladeTechniqueVfxPacket.TORII_SWEEP,
                        BladeTechniqueVfxPacket.PURSUIT_LOCK,
                        BladeTechniqueVfxPacket.MOON_ECHO_FIELD,
                        BladeTechniqueVfxPacket.MOON_ECHO_TRUE,
                        BladeTechniqueVfxPacket.MOON_ECHO_FAILURE,
                        BladeTechniqueVfxPacket.SWORD_WHEEL_BREAK,
                        BladeTechniqueVfxPacket.MIRROR_DUEL_FAILURE,
                        BladeTechniqueVfxPacket.SCISSOR_GUARD,
                        BladeTechniqueVfxPacket.SCISSOR_FAILURE,
                        BladeTechniqueVfxPacket.SCISSOR_BREAK,
                        BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME,
                        BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON,
                        BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END,
                        BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT,
                        BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT,
                        BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS,
                        BladeTechniqueVfxPacket.TWIN_PHASE_YASHA,
                        BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU -> 2;
                case BladeTechniqueVfxPacket.SEAL_LINK,
                        BladeTechniqueVfxPacket.SEAL_BREAK,
                        BladeTechniqueVfxPacket.PURSUIT_RETURN,
                        BladeTechniqueVfxPacket.MIRROR_DUEL,
                        BladeTechniqueVfxPacket.CAGE_PULSE,
                        BladeTechniqueVfxPacket.PURSUIT_SWORD,
                        BladeTechniqueVfxPacket.MOON_ECHO_FALSE,
                        BladeTechniqueVfxPacket.SWORD_WHEEL,
                        BladeTechniqueVfxPacket.SCISSOR_FEINT,
                        BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK -> 1;
                default -> 0;
            };
        }

        void tick(ClientLevel level) {
            age++;
            if (gapTicks > 0) gapTicks--;
            if (gapWarningTicks > 0) gapWarningTicks--;
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
            if (target != null) {
                if (type == BladeTechniqueVfxPacket.BOUNDARY_STRIKE) {
                    if (age <= 31) end = target.position();
                } else if (type == BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME) {
                    end = target.position();
                } else {
                    end = entityCenter(target);
                }
            }
            if (type == BladeTechniqueVfxPacket.PURSUIT_RETURN
                    || type == BladeTechniqueVfxPacket.MIRROR_DUEL) {
                if (source != null && (history.isEmpty()
                        || history.get(history.size() - 1).distanceToSqr(start) > 0.001D)) {
                    history.add(start);
                }
                int quality = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();
                int maximum = quality <= 0 ? 8 : quality == 1 ? 14 : 20;
                while (history.size() > maximum) history.remove(0);
            }
            if (type == BladeTechniqueVfxPacket.SEAL_LINK && age > 10
                    && (source == null || target == null)) {
                age = duration;
            }
        }
    }

    private BladeTechniqueVfxClient() {
    }
}
