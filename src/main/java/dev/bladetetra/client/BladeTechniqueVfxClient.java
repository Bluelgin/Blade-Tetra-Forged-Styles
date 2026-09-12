package dev.bladetetra.client;

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
    private static final ResourceLocation SLASH_DIM_MODEL = new ResourceLocation(
            "slashblade", "model/util/slashdim.obj");
    private static final ResourceLocation SLASH_DIM_TEXTURE = new ResourceLocation(
            "slashblade", "model/util/slashdim.png");
    private static final List<Effect> EFFECTS = new ArrayList<>();
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

    private static void drawTwinFoxMoonhunt(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        // Short trailing wisps, not full-length luminous tubes.
        int steps = segments(10, quality);
        for (int sign : new int[]{1, -1}) {
            for (int i = 0; i < steps; i++) {
                double a = Math.max(0, t - .16 + .16 * i / steps);
                double b = Math.max(0, t - .16 + .16 * (i + 1) / steps);
                float alpha = (float) (i + 1) / steps * .55F;
                int tint = sign > 0 ? color(1, .94F, .84F, alpha)
                        : color(.85F, .035F, .12F, alpha);
                line(buffer, matrix, camera,
                        TwinFoxModelRenderer.path(effect.start, effect.end, a, sign),
                        TwinFoxModelRenderer.path(effect.start, effect.end, b, sign),
                        .035D * strength * (i + 1) / steps, tint);
            }
        }
    }

    private static void renderTwinFoxModels(PoseStack poses, float partialTick, float global) {
        if (EFFECTS.stream().noneMatch(e -> e.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                || e.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS)) return;
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(256));
        try {
            for (Effect effect : EFFECTS) {
                boolean impact = effect.type == BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT;
                float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0, 1);
                if (effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK
                        || effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS) {
                    TwinFoxModelRenderer.drawPursuit(poses, buffers, effect.start,
                            effect.initialEnd, effect.end, t, global,
                            effect.type == BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS);
                    continue;
                }
                if (!impact && effect.type != BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT) continue;
                TwinFoxModelRenderer.draw(poses, buffers, effect.start, effect.end, t,
                        global, impact, effect.intensity > 1);
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    private static void renderTwinPhaseModels(PoseStack poses, float partialTick,
            float global) {
        if (EFFECTS.stream().noneMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_YASHA
                        || effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU)) return;
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(
                new BufferBuilder(512));
        try {
            for (Effect effect : EFFECTS) {
                float t = Mth.clamp((effect.age + partialTick) / effect.duration,
                        0.0F, 1.0F);
                if (effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_YASHA) {
                    TwinPhaseModelRenderer.drawYasha(poses, buffers, effect.start,
                            effect.end, t, global * effect.intensity, effect.seed);
                } else if (effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU) {
                    TwinPhaseModelRenderer.drawKikouku(poses, buffers, effect.end,
                            t, global * effect.intensity, effect.seed);
                }
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    /**
     * Adds SlashBlade's mature judgement-cut wind motion around Kikouku's custom
     * meshes. Only the {@code wind} group is drawn, so the original black-hole
     * center and every gameplay effect remain absent.
     */
    private static void renderTwinPhaseNativeWinds(PoseStack poses,
            float partialTick, float global, int quality) {
        if (EFFECTS.stream().noneMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU)) return;
        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_DIM_MODEL);
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(
                new BufferBuilder(384));
        try {
            for (Effect effect : EFFECTS) {
                if (effect.type != BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU) continue;
                float age = effect.age + partialTick;
                float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
                float appear = Mth.clamp(t / 0.16F, 0.0F, 1.0F);
                float fade = Mth.clamp((1.0F - t) / 0.20F, 0.0F, 1.0F);
                float visibility = appear * fade;
                int copies = quality == 0 ? 2 : quality == 1 ? 3 : 4;
                float scale = 0.0105F * global * effect.intensity
                        * (0.78F + t * 0.72F);
                poses.pushPose();
                poses.translate(effect.end.x, effect.end.y + 0.72D, effect.end.z);
                poses.mulPose(Axis.YP.rotationDegrees(
                        Math.floorMod(effect.seed, 360) + age * 4.8F));
                poses.scale(scale, scale, scale);
                for (int index = 0; index < copies; index++) {
                    poses.pushPose();
                    poses.mulPose(Axis.YP.rotationDegrees(360.0F / copies * index));
                    poses.mulPose(Axis.XP.rotationDegrees(58.0F + index * 17.0F));
                    poses.mulPose(Axis.ZP.rotationDegrees(
                            (index % 2 == 0 ? 1.0F : -1.0F) * age * 13.0F));
                    float pulse = 0.72F + 0.28F * Mth.sin(
                            t * Mth.PI + index * 0.7F);
                    poses.scale(pulse, pulse, pulse);
                    int alpha = Mth.clamp((int) (visibility * 205.0F), 0, 255);
                    int rgb = index % 3 == 1 ? 0xF2D6EA : 0xA51E5B;
                    BladeRenderState.setCol((alpha << 24) | rgb, false);
                    BladeRenderState.renderOverridedColorWrite(ItemStack.EMPTY,
                            model, "wind", SLASH_DIM_TEXTURE, poses, buffers,
                            LightTexture.FULL_BRIGHT);
                    poses.popPose();
                }
                poses.popPose();
            }
            buffers.endBatch();
        } finally {
            BladeRenderState.resetCol();
        }
    }

    private static void drawTwinFoxMoonhuntImpact(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        Vec3 forward = flatDirection(effect.start, center, effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        float fade = (1.0F - t) * (1.0F - t);
        boolean complete = effect.intensity > 1.0F;
        double radius = (0.55D + t * (complete ? 3.8D : 2.2D)) * strength;
        int white = color(1.0F, 0.96F, 0.90F, 0.92F * fade);
        int crimson = color(0.98F, 0.025F, 0.14F, 0.88F * fade);
        ringVertical(buffer, matrix, center, side, radius,
                0.075D + 0.06D * fade, complete ? crimson : white,
                segments(44, quality));
        line(buffer, matrix, camera,
                center.subtract(side.scale(radius)).add(0.0D, -radius * 0.42D, 0.0D),
                center.add(side.scale(radius)).add(0.0D, radius * 0.42D, 0.0D),
                (complete ? 0.15D : 0.09D) * fade, white);
        if (complete) {
            line(buffer, matrix, camera,
                    center.subtract(side.scale(radius)).add(0.0D, radius * 0.42D, 0.0D),
                    center.add(side.scale(radius)).add(0.0D, -radius * 0.42D, 0.0D),
                    0.17D * fade, crimson);
            arcHorizontal(buffer, matrix, camera, center.add(0.0D, -0.62D, 0.0D),
                    radius * 0.78D, 0.0D, Math.PI * 2.0D,
                    0.055D, crimson, segments(40, quality));
        }
    }

    private static void drawAkatsukiFinalMoon(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        Vec3 facing = flatDirection(effect.start, center, effect.yaw);
        Vec3 side = new Vec3(-facing.z, 0.0D, facing.x).normalize();
        double scale = Mth.clamp(effect.intensity, 0.75F, 2.25F);
        Vec3 moonCenter = center.subtract(facing.scale(0.48D * scale))
                .add(0.0D, 0.30D * scale, 0.0D);
        int moon = withAlpha(0xFFBD1838, 0.34F + 0.22F * Mth.sin(t * 16.0F));
        int core = withAlpha(0xFFFFE4D0, 0.82F);
        arcVertical(buffer, matrix, camera, moonCenter, facing, side,
                1.45D * scale, -Math.PI * 0.82D, Math.PI * 0.82D,
                0.10D * scale, moon, segments(44, quality));
        arcVertical(buffer, matrix, camera, moonCenter, facing, side,
                1.18D * scale, -Math.PI * 0.72D, Math.PI * 0.72D,
                0.035D * scale, core, segments(36, quality));
    }

    /**
     * Reuses SlashBlade's judgement-cut wind mesh without rendering its central
     * {@code base} group. This is deliberately client-only: the execution's
     * server-authoritative pulses remain the sole source of damage and knockback.
     */
    private static void renderAkatsukiJudgementWinds(PoseStack poses,
            float partialTick, float global, int quality) {
        boolean present = EFFECTS.stream().anyMatch(effect ->
                effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                        || effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END);
        if (!present) return;

        WavefrontObject model = BladeModelManager.getInstance().getModel(SLASH_DIM_MODEL);
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(256));
        for (Effect effect : EFFECTS) {
            if (effect.type != BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON
                    && effect.type != BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END) continue;
            renderAkatsukiJudgementWind(poses, buffers, model, effect,
                    partialTick, global, quality);
        }
        buffers.endBatch();
        BladeRenderState.resetCol();
    }

    private static void renderAkatsukiJudgementWind(PoseStack poses,
            MultiBufferSource buffers, WavefrontObject model, Effect effect,
            float partialTick, float global, int quality) {
        float age = effect.age + partialTick;
        boolean ending = effect.type == BladeTechniqueVfxPacket.AKATSUKI_FINAL_MOON_END;
        float t = Mth.clamp(age / effect.duration, 0.0F, 1.0F);
        float visibility = ending
                ? (1.0F - t) * (1.0F - t)
                : Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        if (visibility <= 0.002F) return;

        int copies = quality == 0 ? 3 : quality == 1 ? 5 : 7;
        float targetScale = Mth.clamp(effect.intensity, 0.85F, 2.10F);
        float endExpansion = ending ? 1.0F + t * 0.62F : 1.0F;
        float baseScale = 0.0135F * targetScale * global * endExpansion;
        int seedAngle = Math.floorMod(effect.seed, 360);

        poses.pushPose();
        poses.translate(effect.end.x, effect.end.y, effect.end.z);
        poses.mulPose(Axis.YP.rotationDegrees(-effect.yaw + 90.0F));
        poses.mulPose(Axis.YP.rotationDegrees(seedAngle));
        poses.scale(baseScale, baseScale, baseScale);
        for (int index = 0; index < copies; index++) {
            float cycleOffset = 15.0F / copies * index;
            float cycle = Mth.positiveModulo(age + cycleOffset, 15.0F) / 15.0F;
            float sweepScale = 0.48F + cycle * 0.98F;
            float pulse = Mth.sin(cycle * Mth.PI);
            float alpha = Mth.clamp(pulse * visibility * 0.88F, 0.0F, 1.0F);
            if (alpha <= 0.01F) continue;

            poses.pushPose();
            poses.mulPose(Axis.XP.rotationDegrees(360.0F / copies * index));
            poses.mulPose(Axis.YP.rotationDegrees(24.0F + index * 3.5F));
            poses.scale(sweepScale, sweepScale, sweepScale);
            poses.mulPose(Axis.ZP.rotationDegrees(
                    18.0F * (age + seedAngle * 0.13F + cycleOffset)));
            int red = ending && index % 4 == 0 ? 0xFFF0DF : 0xFF2038;
            BladeRenderState.setCol((Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24)
                    | red);
            BladeRenderState.renderOverridedColorWrite(ItemStack.EMPTY, model, "wind",
                    SLASH_DIM_TEXTURE, poses, buffers, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
        poses.popPose();
    }

    private static void drawAkatsukiFinalMoonEnd(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        float fade = (1.0F - t) * (1.0F - t);
        int red = withAlpha(0xFFFF2038, fade * 0.94F);
        int white = withAlpha(0xFFFFF0DF, fade);
        int cuts = quality == 0 ? 10 : quality == 1 ? 16 : 22;
        for (int index = 0; index < cuts; index++) {
            Vec3 direction = deterministicDirection(effect.seed + 73, index);
            double length = (1.4D + t * 2.8D + index % 4 * 0.16D) * effect.intensity;
            Vec3 offset = direction.scale(t * 0.55D);
            line(buffer, matrix, camera,
                    center.add(offset).subtract(direction.scale(length * 0.5D)),
                    center.add(offset).add(direction.scale(length * 0.5D)),
                    0.025D + fade * 0.025D, index % 5 == 0 ? white : red);
        }
        arcHorizontal(buffer, matrix, camera, center.add(0.0D, -0.45D, 0.0D),
                (0.35D + t * 3.2D) * effect.intensity, 0.0D, Math.PI * 2.0D,
                0.045D, red, segments(42, quality));
    }

    private static void drawSolidCore(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Effect effect, float partialTick, float global, int quality) {
        if (global <= 0.001F) return;
        float t = Mth.clamp((effect.age + partialTick) / effect.duration, 0.0F, 1.0F);
        float strength = effect.intensity * global;
        switch (effect.type) {
            case BladeTechniqueVfxPacket.TORII_SWEEP ->
                    drawToriiSweepCore(buffer, matrix, camera, effect, strength, quality);
            case BladeTechniqueVfxPacket.TORII_CAGE ->
                    drawToriiCageCore(buffer, matrix, camera, effect, t, strength);
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH ->
                    drawBoundaryFlashCore(buffer, matrix, camera, effect, strength, quality);
            case BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE ->
                    drawBoundaryFlashReleaseCore(buffer, matrix, effect, strength);
            case BladeTechniqueVfxPacket.BOUNDARY_WALL ->
                    drawBoundaryWallCore(buffer, matrix, camera, effect, strength);
            case BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME ->
                    drawBoundarySuppressionFlame(buffer, matrix, camera, effect,
                            t, strength, quality, true);
            case BladeTechniqueVfxPacket.SCISSOR_FEINT ->
                    drawScissorResultCore(buffer, matrix, camera, effect, t, strength, 0);
            case BladeTechniqueVfxPacket.SCISSOR_GUARD ->
                    drawScissorResultCore(buffer, matrix, camera, effect, t, strength, 1);
            case BladeTechniqueVfxPacket.SCISSOR_FAILURE ->
                    drawScissorResultCore(buffer, matrix, camera, effect, t, strength, 2);
            case BladeTechniqueVfxPacket.SCISSOR_BREAK ->
                    drawScissorResultCore(buffer, matrix, camera, effect, t, strength, 3);
            case BladeTechniqueVfxPacket.PURSUIT_SWORD ->
                    drawPursuitSwordCore(buffer, matrix, camera, effect, t, strength);
            default -> {
            }
        }
    }

    private static boolean isSolidCoreType(int type) {
        return type == BladeTechniqueVfxPacket.TORII_SWEEP
                || type == BladeTechniqueVfxPacket.TORII_CAGE
                || type == BladeTechniqueVfxPacket.BOUNDARY_FLASH
                || type == BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE
                || type == BladeTechniqueVfxPacket.BOUNDARY_WALL
                || type == BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME
                || type == BladeTechniqueVfxPacket.SCISSOR_FEINT
                || type == BladeTechniqueVfxPacket.SCISSOR_GUARD
                || type == BladeTechniqueVfxPacket.SCISSOR_FAILURE
                || type == BladeTechniqueVfxPacket.SCISSOR_BREAK
                || type == BladeTechniqueVfxPacket.PURSUIT_SWORD;
    }

    private static void drawPursuitReturn(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        boolean warning = effect.age < 14;
        float fade = warning ? 1.0F : Math.max(0.0F, 1.0F - t);
        int red = color(1.0F, 0.035F, 0.10F, 0.82F * fade);
        int white = color(1.0F, 0.94F, 0.92F, 0.92F * fade);
        if (warning) {
            float progress = Mth.clamp(effect.age / 14.0F, 0.0F, 1.0F);
            line(buffer, matrix, camera, effect.start, effect.end,
                    (0.035D + progress * 0.025D) * strength, red);
            Vec3 right = cameraRight(effect.start, camera);
            double radius = (1.25D - progress * 0.88D) * strength;
            ringVertical(buffer, matrix, effect.start, right, radius, 0.035D, white,
                    segments(28, quality));
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3.0D + effect.seed * 0.013D;
                Vec3 dir = planeVector(right, Math.cos(angle), Math.sin(angle));
                line(buffer, matrix, camera,
                        effect.start.add(dir.scale(radius * 0.72D)),
                        effect.start.add(dir.scale(radius)), 0.025D, red);
            }
        }
        drawRibbon(buffer, matrix, camera, effect.history,
                0.17D * strength, red, quality);
        drawRibbon(buffer, matrix, camera, effect.history,
                0.055D * strength, white, quality);
    }

    private static void drawMirrorDuel(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        int white = color(1.0F, 0.96F, 0.92F, 0.94F * (1.0F - t * 0.45F));
        int purple = color(0.50F, 0.08F, 0.68F, 0.68F * (1.0F - t * 0.35F));
        int red = color(1.0F, 0.035F, 0.10F, 0.76F * (1.0F - t));
        if (effect.age < 14) {
            double pulse = 0.018D + 0.018D
                    * (0.5D + 0.5D * Math.sin((effect.age + 1) * 1.35D));
            line(buffer, matrix, camera, effect.start, effect.end, pulse * strength, white);
            Vec3 side = horizontalSide(effect.start, effect.end).scale(0.13D);
            line(buffer, matrix, camera, effect.start.add(side), effect.end.add(side),
                    0.022D * strength, purple);
            line(buffer, matrix, camera, effect.start.subtract(side), effect.end.subtract(side),
                    0.022D * strength, purple);
        } else {
            line(buffer, matrix, camera, effect.start, effect.end,
                    0.035D * strength * (1.0F - t), red);
        }
        drawRibbon(buffer, matrix, camera, effect.history,
                0.20D * strength, purple, quality);
        drawRibbon(buffer, matrix, camera, effect.history,
                0.060D * strength, white, quality);
    }

    private static void drawSealLink(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float pulse = 0.58F + 0.42F
                * Mth.sin((effect.age + effect.seed * 0.01F) * 0.42F);
        float danger = Mth.clamp((t - 0.55F) / 0.45F, 0.0F, 1.0F);
        int outer = color(0.96F, 0.018F, 0.065F,
                (0.48F + pulse * 0.24F) * strength);
        int core = color(1.0F, 0.42F - danger * 0.34F, 0.34F - danger * 0.29F,
                (0.35F + pulse * 0.32F) * strength);
        line(buffer, matrix, camera, effect.start, effect.end,
                (0.065D + danger * 0.065D) * strength, outer);
        line(buffer, matrix, camera, effect.start, effect.end,
                0.018D * strength, core);
    }

    private static void drawSealBreak(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = (1.0F - t) * (1.0F - t);
        int white = color(1.0F, 0.96F, 0.92F, fade);
        int gold = color(1.0F, 0.58F, 0.10F, fade * 0.86F);
        for (int i = 0; i < 9; i++) {
            Vec3 dir = deterministicDirection(effect.seed, i);
            Vec3 start = effect.start.add(dir.scale(0.15D));
            Vec3 end = effect.start.add(dir.scale((0.8D + i % 3 * 0.22D)
                    * strength * (0.45D + t)));
            line(buffer, matrix, camera, start, end, 0.035D,
                    i % 2 == 0 ? white : gold);
        }
    }

    private static void drawSealSuccess(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = 1.0F - t;
        int gold = color(1.0F, 0.58F, 0.10F, fade * 0.92F);
        int white = color(1.0F, 0.96F, 0.92F, fade);
        Vec3 right = cameraRight(effect.start, camera);
        double outer = (0.25D + t * 3.4D) * strength;
        double inner = Math.max(0.08D, (2.4D * (1.0D - t))) * strength;
        ringVertical(buffer, matrix, effect.start, right, outer, 0.055D, gold, 36);
        ringVertical(buffer, matrix, effect.start, right, inner, 0.032D, white, 30);
    }

    private static void drawSealFailure(BufferBuilder buffer, Matrix4f matrix,
            Effect effect, float t, float strength) {
        float fade = 1.0F - t;
        int darkRed = color(0.62F, 0.008F, 0.025F, fade * 0.76F);
        double radius = (1.0D + t * 15.0D) * strength;
        ringHorizontal(buffer, matrix, effect.start.add(0.0D, 0.12D, 0.0D),
                radius, 0.16D * (1.0D - t) + 0.035D, darkRed, 56);
    }

    private static void drawCounterClash(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = (1.0F - t) * (1.0F - t);
        Vec3 center = effect.start.lerp(effect.end, 0.5D);
        int white = color(1.0F, 0.97F, 0.92F, fade);
        int gold = color(1.0F, 0.58F, 0.10F, fade * 0.94F);
        line(buffer, matrix, camera, effect.start, effect.end,
                0.075D * strength * (1.0F - t), white);
        Vec3 right = cameraRight(center, camera);
        double radius = (0.25D + Mth.sin(t * (float) Math.PI) * 2.8D) * strength;
        ringVertical(buffer, matrix, center, right, radius, 0.075D, gold, 42);
        ringVertical(buffer, matrix, center, right, radius * 0.63D, 0.035D, white, 34);
    }

    private static void drawBoundaryStrike(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 target = effect.end;
        int red = color(1.0F, 0.025F, 0.08F, 0.82F * (1.0F - t * 0.35F));
        int white = color(1.0F, 0.95F, 0.92F, 0.90F * (1.0F - t * 0.45F));
        int gold = color(1.0F, 0.58F, 0.10F, Math.max(0.0F, 1.0F - t) * 0.82F);
        if (effect.age < 15) {
            double lock = (1.55D - effect.age / 15.0D * 1.10D) * strength;
            ringHorizontal(buffer, matrix, target.add(0.0D, 2.65D, 0.0D),
                    lock, 0.035D, effect.age >= 11 ? white : red, segments(36, quality));
            for (int i = 0; i < 4; i++) {
                int launch = 1 + i * 3;
                if (effect.age < launch) continue;
                double angle = Math.toRadians(effect.yaw) + i * Math.PI * 0.5D;
                Vec3 origin = target.add(Math.cos(angle) * (3.2D + i * 0.25D),
                        5.2D + i * 0.38D, Math.sin(angle) * (3.2D + i * 0.25D));
                line(buffer, matrix, camera, origin, target.add(0.0D, 1.0D, 0.0D),
                        0.045D * strength, i == 3 ? white : red);
            }
        }
        double top = target.y + 7.0D;
        double bottom = target.y - 0.25D;
        double gateY;
        if (effect.age < 15) {
            gateY = top;
        } else {
            double progress = Mth.clamp((effect.age - 15.0D) / 16.0D, 0.0D, 1.0D);
            gateY = Mth.lerp(progress * progress, top, bottom);
        }
        if (effect.age <= 76) {
            float gateFade = effect.age <= 66 ? 1.0F
                    : Mth.clamp((76.0F - effect.age) / 10.0F, 0.0F, 1.0F);
            drawTorii(buffer, matrix, camera,
                    new Vec3(target.x, gateY, target.z), effect.yaw,
                    1.0D * strength, withAlpha(effect.age >= 30 ? white : red,
                            gateFade * 0.94F));
            if (effect.age >= 15) {
                line(buffer, matrix, camera,
                        new Vec3(target.x, top + 1.0D, target.z),
                        new Vec3(target.x, gateY + 1.0D, target.z),
                        0.08D * strength, withAlpha(red, gateFade * 0.82F));
            }
            if (effect.age >= 31) {
                double sealRadius = 1.32D * strength;
                ringHorizontal(buffer, matrix, target.add(0.0D, 0.045D, 0.0D),
                        sealRadius, 0.045D, withAlpha(red, gateFade * 0.78F),
                        segments(36, quality));
                for (int i = 0; i < 4; i++) {
                    double angle = Math.toRadians(effect.yaw) + Math.PI * 0.25D
                            + i * Math.PI * 0.5D;
                    Vec3 corner = target.add(Math.cos(angle) * sealRadius, 0.07D,
                            Math.sin(angle) * sealRadius);
                    line(buffer, matrix, camera, corner,
                            target.add(0.0D, 1.0D, 0.0D),
                            0.028D * strength, withAlpha(red, gateFade * 0.58F));
                }
            }
        }
        if (effect.age >= 31 && effect.age <= 43) {
            float impactT = Mth.clamp((effect.age - 31.0F) / 9.0F, 0.0F, 1.0F);
            float impactFade = Mth.clamp((43.0F - effect.age) / 5.0F, 0.0F, 1.0F);
            ringHorizontal(buffer, matrix, target.add(0.0D, 0.12D, 0.0D),
                    (0.4D + impactT * 5.4D) * strength,
                    0.12D * (1.0D - impactT) + 0.025D,
                    withAlpha(impactT < 0.35F ? white : gold, impactFade),
                    segments(52, quality));
        }
    }

    private static void drawBoundarySuppressionFlame(BufferBuilder buffer,
            Matrix4f matrix, Vec3 camera, Effect effect, float t, float strength,
            int quality, boolean solidCore) {
        float rise = Mth.clamp(effect.age / 7.0F, 0.0F, 1.0F);
        float fade = effect.age < effect.duration - 20 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 20.0F, 0.0F, 1.0F);
        float envelope = rise * fade;
        if (envelope <= 0.001F) return;

        Vec3 center = effect.end.add(0.0D, 0.04D, 0.0D);
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        float visualScale = Mth.clamp(strength, 0.72F, 2.15F);
        int outer = solidCore
                ? color(0.17F, 0.001F, 0.009F, envelope * 0.94F)
                : color(0.98F, 0.012F, 0.04F, envelope * 0.74F);
        int middle = color(1.0F, 0.13F, 0.025F, envelope * 0.84F);
        int inner = color(1.0F, 0.66F, 0.20F, envelope * 0.90F);
        int flames = quality <= 0 ? 6 : quality == 1 ? 9 : 12;
        double radius = 0.58D * visualScale;
        for (int i = 0; i < flames; i++) {
            double angle = i * Math.PI * 2.0D / flames
                    + effect.seed * 0.00031D;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            Vec3 base = center.add(radial.scale(radius
                    * (0.82D + 0.18D * Math.sin(i * 2.31D + effect.seed))));
            double phase = effect.age * 0.17D + i * 1.83D
                    + effect.seed * 0.0011D;
            double height = (1.0D + 0.82D
                    * (0.5D + 0.5D * Math.sin(i * 2.17D + effect.seed * 0.017D)))
                    * visualScale * envelope;
            drawBoundaryFlameTongue(buffer, matrix, camera, base, radial, tangent,
                    height, phase, 0.82F, outer, middle, inner,
                    solidCore, i + 73);
        }
        if (!solidCore) {
            ringHorizontal(buffer, matrix, center, radius * 1.34D,
                    0.055D, withAlpha(outer, envelope * 0.72F),
                    segments(36, quality));
            for (int i = 0; i < 4; i++) {
                double angle = Math.toRadians(effect.yaw) + Math.PI * 0.25D
                        + i * Math.PI * 0.5D;
                Vec3 seal = center.add(Math.cos(angle) * radius * 1.28D, 0.02D,
                        Math.sin(angle) * radius * 1.28D);
                line(buffer, matrix, camera, seal,
                        center.add(0.0D, 0.84D * visualScale, 0.0D),
                        0.024D, withAlpha(middle, envelope * 0.56F));
            }
        }
    }

    private static void drawToriiCage(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float warning = Mth.clamp(effect.age / 25.0F, 0.0F, 1.0F);
        float recovery = Mth.clamp((t - 0.90F) / 0.10F, 0.0F, 1.0F);
        float alpha = (0.35F + warning * 0.42F) * (1.0F - recovery);
        int red = color(0.96F, 0.025F, 0.08F, alpha);
        int white = color(1.0F, 0.95F, 0.92F,
                ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? alpha * 0.74F : alpha * 0.28F);
        Vec3 base = effect.start.add(0.0D, 0.05D, 0.0D);
        ringHorizontal(buffer, matrix, base, 4.6D * strength,
                0.045D + warning * 0.025D, red, segments(48, quality));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25D;
            Vec3 gate = base.add(Math.cos(angle) * 4.6D, 0.0D,
                    Math.sin(angle) * 4.6D);
            drawTorii(buffer, matrix, camera, gate,
                    (float) Math.toDegrees(angle + Math.PI * 0.5D),
                    0.42D * strength, i % 2 == 0 ? red : white);
        }
    }

    private static void drawCagePulse(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float fade = 1.0F - t;
        int red = color(1.0F, 0.02F, 0.07F, fade * 0.88F);
        int white = color(1.0F, 0.96F, 0.92F,
                fade * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? 0.95F : 0.32F));
        double radius = Mth.lerp(t, 4.6D, 0.32D) * strength;
        ringHorizontal(buffer, matrix, effect.start.add(0.0D, 0.12D, 0.0D),
                radius, 0.10D * fade + 0.025D, t > 0.72F ? white : red,
                segments(48, quality));
        if (t > 0.58F) {
            line(buffer, matrix, camera, effect.start.add(0.0D, 0.15D, 0.0D),
                    effect.end, 0.035D * strength, white);
        }
    }

    private static void drawCageResult(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, boolean success) {
        float fade = (1.0F - t) * (1.0F - t);
        int outer = success ? color(1.0F, 0.62F, 0.12F, fade)
                : color(0.62F, 0.008F, 0.025F, fade * 0.90F);
        int core = color(1.0F, 0.96F, 0.92F,
                fade * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? 1.0F : 0.28F));
        Vec3 right = cameraRight(effect.start, camera);
        double radius = (0.25D + t * (success ? 3.2D : 2.2D)) * strength;
        ringVertical(buffer, matrix, effect.start, right, radius, 0.08D, outer, 40);
        for (int i = 0; i < (success ? 8 : 5); i++) {
            Vec3 direction = deterministicDirection(effect.seed, i);
            line(buffer, matrix, camera, effect.start,
                    effect.start.add(direction.scale((0.7D + t * 2.0D) * strength)),
                    0.035D, i % 2 == 0 ? core : outer);
        }
    }

    private static void drawBoundaryFlash(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float reveal = Mth.clamp((effect.age - 12.0F) / 148.0F, 0.0F, 1.0F);
        float pulse = 0.78F + 0.22F * Mth.sin(effect.age * 0.52F);
        int red = color(1.0F, 0.018F, 0.055F, 0.80F * reveal);
        int dark = color(0.32F, 0.002F, 0.012F, 0.52F * reveal);
        int white = color(1.0F, 0.94F, 0.92F,
                reveal * pulse * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get()
                        ? 0.92F : 0.28F));
        Vec3 forward = effect.end.subtract(effect.start).multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 0.0001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 haloCenter = effect.start.add(forward.scale(-0.75D)).add(0.0D, 0.15D, 0.0D);
        double floorY = effect.end.y - 0.9D;
        Vec3 floorCenter = new Vec3(effect.start.x, floorY + 0.05D, effect.start.z);
        float siphon = Mth.clamp((effect.age - 8.0F) / 128.0F, 0.0F, 1.0F);
        float siphonPulse = 0.72F + 0.28F * Mth.sin(effect.age * 0.22F);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25D + effect.seed * 0.00011D;
            Vec3 source = floorCenter.add(Math.cos(angle) * 50.5D, (i % 3) * 0.45D,
                    Math.sin(angle) * 50.5D);
            float nodeReveal = Mth.clamp((effect.age - 14.0F - i * 13.0F) / 22.0F,
                    0.0F, 1.0F);
            line(buffer, matrix, camera, source, haloCenter,
                    (0.030D + siphon * 0.065D) * strength,
                    withAlpha(dark, nodeReveal * 0.72F));
            line(buffer, matrix, camera, source, haloCenter,
                    (0.012D + siphon * 0.030D) * strength,
                    siphon > 0.82F && i % 2 == 0 ? white
                            : withAlpha(red, nodeReveal * siphonPulse * 0.92F));
            if (nodeReveal > 0.02F) {
                drawTorii(buffer, matrix, camera, source,
                        (float) Math.toDegrees(angle + Math.PI * 0.5D),
                        (0.82D + nodeReveal * 0.38D) * strength,
                        i % 2 == 0 ? withAlpha(red, nodeReveal) : withAlpha(dark, nodeReveal));
                ringHorizontal(buffer, matrix, source.add(0.0D, 0.05D, 0.0D),
                        (0.7D + nodeReveal * 1.35D) * strength,
                        0.035D, withAlpha(red, nodeReveal * 0.72F),
                        segments(30, quality));
            }
        }
        double radius = (0.55D + reveal * 2.75D) * strength;
        ringVertical(buffer, matrix, haloCenter, right, radius,
                0.035D + reveal * 0.025D, dark, segments(48, quality));
        int gates = Math.min(8, Math.max(0, (int) ((effect.age - 12) / 18.0D) + 1));
        double rotation = effect.age * (0.023D - reveal * 0.008D)
                + effect.seed * 0.00013D;
        for (int i = 0; i < gates; i++) {
            double angle = rotation + i * Math.PI * 0.25D;
            Vec3 radial = planeVector(right, Math.cos(angle), Math.sin(angle));
            Vec3 tangent = planeVector(right, -Math.sin(angle), Math.cos(angle));
            Vec3 gate = haloCenter.add(radial.scale(radius));
            drawHaloTorii(buffer, matrix, camera, gate, radial, tangent,
                    0.42D * strength, i % 2 == 0 ? white : red, dark);
        }
        if (reveal > 0.20F) {
            drawBoundaryBlade3d(buffer, matrix,
                    haloCenter.add(0.0D, 0.55D, 0.0D), right, forward,
                    (0.20D + reveal * 0.80D) * strength,
                    red, dark, white);
        }
        Vec3 floorTarget = new Vec3(effect.end.x, floorY + 0.04D, effect.end.z);
        line(buffer, matrix, camera, floorCenter, floorTarget,
                (0.018D + reveal * 0.018D) * strength, red);
        ringHorizontal(buffer, matrix, floorTarget, (0.48D + reveal * 0.28D) * strength,
                0.025D, white, segments(30, quality));
    }

    private static void drawBoundaryCharge(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        int charge = Mth.clamp(Math.round(effect.intensity), 1, 3);
        float normalizedStrength = strength / Math.max(1.0F, effect.intensity);
        float envelope = Mth.sin(Mth.clamp(t, 0.0F, 1.0F) * Mth.PI);
        float pulse = 0.72F + 0.28F * Mth.sin(effect.age * 0.42F);
        int red = color(1.0F, 0.012F, 0.045F, envelope * pulse * 0.88F);
        int dark = color(0.30F, 0.001F, 0.01F, envelope * 0.58F);
        int white = color(1.0F, 0.94F, 0.90F,
                envelope * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get()
                        ? 0.82F : 0.25F));
        int nodes = charge == 1 ? 2 : charge == 2 ? 5 : 8;
        Vec3 center = effect.start;
        for (int i = 0; i < nodes; i++) {
            double angle = i * Math.PI * 0.25D + effect.seed * 0.00011D;
            Vec3 source = center.add(Math.cos(angle) * 50.5D, (i % 3) * 0.4D,
                    Math.sin(angle) * 50.5D);
            float stagger = Mth.clamp((effect.age - i * 2.0F) / 9.0F, 0.0F, 1.0F);
            int beam = i == nodes - 1 ? white : red;
            drawTorii(buffer, matrix, camera, source,
                    (float) Math.toDegrees(angle + Math.PI * 0.5D),
                    (0.88D + charge * 0.10D) * normalizedStrength,
                    withAlpha(beam, envelope * stagger));
            line(buffer, matrix, camera, source, effect.end,
                    (0.026D + charge * 0.009D) * normalizedStrength,
                    withAlpha(i % 3 == 0 ? white : red, envelope * stagger * 0.82F));
            ringHorizontal(buffer, matrix, source.add(0.0D, 0.05D, 0.0D),
                    (0.72D + envelope * 1.25D) * normalizedStrength,
                    0.032D, withAlpha(red, envelope * stagger * 0.66F),
                    segments(28, quality));
        }
        ringHorizontal(buffer, matrix, center.add(0.0D, 0.04D, 0.0D),
                50.5D, 0.018D * normalizedStrength,
                withAlpha(dark, envelope * 0.54F), segments(96, quality));
    }

    private static void drawBoundaryFlashRelease(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float collapse = Mth.clamp(effect.age / 20.0F, 0.0F, 1.0F);
        float fade = effect.age < effect.duration - 8 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 8.0F, 0.0F, 1.0F);
        int red = color(1.0F, 0.015F, 0.045F, 0.88F * fade);
        int dark = color(0.28F, 0.001F, 0.008F, 0.62F * fade);
        int white = color(1.0F, 0.96F, 0.94F,
                (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get()
                        ? 0.96F : 0.30F) * fade);
        Vec3 start = effect.start;
        Vec3 groundStart = new Vec3(start.x, effect.end.y, start.z);
        Vec3 direction = effect.end.subtract(groundStart);
        if (direction.lengthSqr() < 0.0001D) return;
        direction = direction.normalize();
        Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 haloCenter = start.subtract(direction.scale(0.75D));
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25D + effect.seed * 0.00013D;
            Vec3 radial = planeVector(right, Math.cos(angle), Math.sin(angle));
            Vec3 from = haloCenter.add(radial.scale(3.25D * strength));
            double along = 3.0D + i * 5.8D;
            Vec3 to = groundStart.add(direction.scale(along))
                    .add(0.0D, 6.0D - along * 0.11D, 0.0D);
            Vec3 gate = from.lerp(to, collapse);
            Vec3 tangent = collapse < 0.82F
                    ? planeVector(right, -Math.sin(angle), Math.cos(angle))
                    : new Vec3(0.0D, 1.0D, 0.0D);
            drawHaloTorii(buffer, matrix, camera, gate,
                    radial.lerp(right, collapse).normalize(), tangent.normalize(),
                    (0.42D + collapse * 0.28D) * strength,
                    i % 3 == 0 ? white : red, dark);
        }
        drawBoundaryBlade3d(buffer, matrix,
                haloCenter.add(0.0D, 0.55D, 0.0D), right, direction,
                (1.0D + collapse * 0.72D) * strength,
                red, dark, white);
        if (effect.age >= 14) {
            float guardCue = Mth.clamp((effect.age - 14.0F) / 12.0F, 0.0F, 1.0F);
            ringHorizontal(buffer, matrix, effect.end.add(0.0D, 0.06D, 0.0D),
                    (1.25D - guardCue * 0.72D) * strength,
                    0.055D + guardCue * 0.055D, white,
                    segments(42, quality));
        }
        line(buffer, matrix, camera, groundStart, effect.end,
                (0.035D + collapse * 0.075D) * strength,
                collapse > 0.86F ? white : red);
    }

    private static void drawBoundaryFlashImpact(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float fade = 1.0F - t;
        float detonation = Mth.clamp(1.0F - effect.age / 5.5F, 0.0F, 1.0F);
        int red = color(1.0F, 0.01F, 0.035F, 0.90F * fade);
        int dark = color(0.24F, 0.001F, 0.008F, 0.74F * fade);
        int white = color(1.0F, 0.97F, 0.94F,
                (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get()
                        ? 1.0F : 0.32F) * fade);
        Vec3 direction = effect.end.subtract(effect.start);
        if (direction.lengthSqr() < 0.0001D) return;
        direction = direction.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        double height = (11.0D + effect.age * 0.72D) * strength;
        Vec3 a = effect.start.subtract(side.scale(0.42D));
        Vec3 b = effect.end.subtract(side.scale(0.42D));
        Vec3 c = effect.end.add(side.scale(0.42D)).add(0.0D, height, 0.0D);
        Vec3 d = effect.start.add(side.scale(0.42D)).add(0.0D, height, 0.0D);
        quad(buffer, matrix, a, b, c, d, withAlpha(red, fade * 0.86F));
        Vec3 innerOffset = side.scale(0.10D);
        quad(buffer, matrix, effect.start.subtract(innerOffset), effect.end.subtract(innerOffset),
                effect.end.add(innerOffset).add(0.0D, height * 0.82D, 0.0D),
                effect.start.add(innerOffset).add(0.0D, height * 0.82D, 0.0D),
                withAlpha(white, fade * (0.45F + detonation * 0.55F)));
        line(buffer, matrix, camera, effect.start, effect.end,
                (0.30D + detonation * 0.36D) * strength, white);
        line(buffer, matrix, camera, effect.start.add(side.scale(0.72D)),
                effect.end.add(side.scale(0.72D)), 0.15D * strength, red);
        line(buffer, matrix, camera, effect.start.subtract(side.scale(0.72D)),
                effect.end.subtract(side.scale(0.72D)), 0.15D * strength, dark);

        int ruptures = quality <= 0 ? 7 : quality == 1 ? 11 : 16;
        for (int i = 1; i < ruptures; i++) {
            double along = i / (double) ruptures;
            Vec3 root = effect.start.lerp(effect.end, along).add(0.0D, 0.035D, 0.0D);
            double branchSide = ((i & 1) == 0 ? 1.0D : -1.0D)
                    * (1.2D + (i % 4) * 0.72D) * (0.55D + fade * 0.45D);
            Vec3 branch = root.add(side.scale(branchSide))
                    .add(direction.scale(((i * 37 + effect.seed) & 3) * 0.34D - 0.5D));
            line(buffer, matrix, camera, root, branch,
                    (0.045D + detonation * 0.055D) * strength,
                    i % 4 == 0 ? white : red);
            double spikeHeight = (1.8D + i % 5 * 0.72D) * fade * strength;
            line(buffer, matrix, camera, root, root.add(0.0D, spikeHeight, 0.0D),
                    (0.055D + detonation * 0.07D) * strength,
                    i % 3 == 0 ? white : red);
        }
        ringHorizontal(buffer, matrix, effect.start,
                (1.0D + effect.age * 2.25D) * strength,
                0.14D * fade + 0.035D, red, segments(64, quality));
        if (effect.age > 3) {
            ringHorizontal(buffer, matrix, effect.start,
                    (0.5D + (effect.age - 3.0D) * 1.45D) * strength,
                    0.08D * fade + 0.025D, white, segments(52, quality));
        }
        ringVertical(buffer, matrix, effect.start.add(0.0D, 1.2D, 0.0D), side,
                (0.8D + effect.age * 1.65D) * strength,
                0.11D * fade + 0.025D, withAlpha(red, fade * 0.82F),
                segments(56, quality));
    }

    private static void drawBoundaryWall(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        drawBoundaryFlameField(buffer, matrix, camera, effect, strength, quality, false);
        Vec3 direction = effect.end.subtract(effect.start);
        if (direction.lengthSqr() < 0.0001D) return;
        direction = direction.normalize();
        double length = effect.start.distanceTo(effect.end);
        boolean gapWarning = effect.gapWarningTicks > 0
                && effect.pendingGapAlong > 1.65D
                && effect.pendingGapAlong < length - 1.65D;
        if (gapWarning) {
            float warningPulse = 0.55F + 0.45F * Mth.sin(effect.gapWarningTicks * 0.72F);
            Vec3 warningNear = effect.start.add(direction.scale(
                    effect.pendingGapAlong - 1.65D));
            Vec3 warningFar = effect.start.add(direction.scale(
                    effect.pendingGapAlong + 1.65D));
            int warning = color(1.0F, 0.92F, 0.82F, warningPulse * 0.92F);
            ringHorizontal(buffer, matrix, warningNear.add(0.0D, 0.025D, 0.0D),
                    (0.38D + warningPulse * 0.18D) * strength,
                    0.055D, warning, segments(24, quality));
            ringHorizontal(buffer, matrix, warningFar.add(0.0D, 0.025D, 0.0D),
                    (0.38D + warningPulse * 0.18D) * strength,
                    0.055D, warning, segments(24, quality));
            drawGapWisp(buffer, matrix, camera, warningNear, direction, strength, warning,
                    effect.age * 0.22D);
            drawGapWisp(buffer, matrix, camera, warningFar, direction, strength, warning,
                    effect.age * 0.22D + Math.PI);
        }
    }

    private static void drawBoundaryFlameField(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality, boolean solidCore) {
        float rise = Mth.clamp(effect.age / 9.0F, 0.0F, 1.0F);
        float fade = effect.age < effect.duration - 18 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 18.0F, 0.0F, 1.0F);
        Vec3 direction = effect.end.subtract(effect.start);
        if (direction.lengthSqr() < 0.0001D) return;
        double length = direction.length();
        direction = direction.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        boolean gapActive = effect.gapTicks > 0 && effect.gapAlong > 1.65D
                && effect.gapAlong < length - 1.65D;
        int ground = solidCore
                ? color(0.11F, 0.001F, 0.008F, 0.96F * fade)
                : color(0.92F, 0.008F, 0.038F, 0.70F * fade);
        int outer = solidCore
                ? color(0.20F, 0.002F, 0.014F, 0.94F * fade)
                : color(0.96F, 0.018F, 0.055F, 0.72F * fade);
        int middle = color(1.0F, 0.12F, 0.045F, 0.82F * fade);
        int inner = color(1.0F, 0.64F, 0.30F, 0.88F * fade);

        Vec3 nearEnd = gapActive
                ? effect.start.add(direction.scale(effect.gapAlong - 1.65D)) : effect.end;
        Vec3 farStart = gapActive
                ? effect.start.add(direction.scale(effect.gapAlong + 1.65D)) : effect.end;
        drawBoundaryFlameGround(buffer, matrix, camera, effect.start, nearEnd,
                side, strength, ground, solidCore);
        if (gapActive) {
            drawBoundaryFlameGround(buffer, matrix, camera, farStart, effect.end,
                    side, strength, ground, solidCore);
        }

        // A few broad, irregular clusters read as fire much better than a row
        // of evenly-spaced spikes. Higher quality adds secondary tongues, not
        // merely more copies of the same silhouette.
        int flames = quality <= 0 ? 16 : quality == 1 ? 21 : 27;
        for (int i = 0; i <= flames; i++) {
            double spacing = length / (flames + 0.55D);
            double along = spacing * (i + 0.28D)
                    + Math.sin(i * 4.17D + effect.seed * 0.007D) * spacing * 0.22D;
            if (gapActive && Math.abs(along - effect.gapAlong) <= 1.82D) continue;
            double phase = effect.age * 0.115D + i * 1.91D + effect.seed * 0.0017D;
            double height = (1.20D + 2.10D
                    * (0.5D + 0.5D * Math.sin(i * 2.17D + effect.seed * 0.013D)))
                    * rise * strength;
            if (i % 5 == 3) height += 0.78D * rise * strength;
            Vec3 base = effect.start.add(direction.scale(along));
            double lane = ((i & 1) == 0 ? 0.20D : -0.20D)
                    + Math.sin(i * 3.11D + effect.seed) * 0.13D;
            base = base.add(side.scale(lane));
            drawBoundaryFlameTongue(buffer, matrix, camera, base, direction, side,
                    height, phase, strength, outer, middle, inner, solidCore, i);

            double secondaryAlong = along + spacing * 0.48D;
            if ((!gapActive || Math.abs(secondaryAlong - effect.gapAlong) > 1.82D)
                    && secondaryAlong < length) {
                Vec3 secondaryBase = effect.start.add(direction.scale(secondaryAlong))
                        .add(side.scale(-lane * 0.82D
                                + Math.sin(i * 2.33D + effect.seed) * 0.10D));
                drawBoundaryFlameTongue(buffer, matrix, camera, secondaryBase,
                        direction, side, height * (0.48D + (i % 3) * 0.08D),
                        phase + 1.37D, strength * 0.92F,
                        outer, middle, inner, solidCore, i + 41);
            }
        }

        // A nearly continuous low fire bed hides the mechanical boundary line
        // and joins the taller tongues into one readable wall at long range.
        int lowFlames = quality <= 0 ? 24 : quality == 1 ? 34 : 44;
        double lowSpacing = length / (lowFlames + 0.35D);
        for (int i = 0; i <= lowFlames; i++) {
            double along = lowSpacing * (i + 0.18D)
                    + Math.sin(i * 5.13D + effect.seed * 0.009D) * lowSpacing * 0.16D;
            if (gapActive && Math.abs(along - effect.gapAlong) <= 1.82D) continue;
            double phase = effect.age * 0.15D + i * 2.41D + effect.seed * 0.0021D;
            double lowHeight = (0.38D + 0.58D
                    * (0.5D + 0.5D * Math.sin(i * 2.87D + effect.seed * 0.017D)))
                    * rise * strength;
            double lane = ((i & 1) == 0 ? -0.27D : 0.27D)
                    + Math.sin(i * 1.73D + effect.seed) * 0.09D;
            Vec3 base = effect.start.add(direction.scale(along)).add(side.scale(lane));
            drawBoundaryLowFlame(buffer, matrix, base, direction, side,
                    lowHeight, phase, strength, outer, middle, solidCore, i);
        }
    }

    private static void drawBoundaryFlameGround(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float strength,
            int color, boolean solidCore) {
        if (start.distanceToSqr(end) < 0.01D) return;
        Vec3 direction = end.subtract(start).normalize();
        double length = start.distanceTo(end);
        int pieces = Math.max(2, Mth.ceil(length / 1.65D));
        Vec3 previous = start;
        for (int i = 1; i <= pieces; i++) {
            double along = length * i / pieces;
            double jitter = Math.sin(i * 2.71D + start.x * 0.031D + start.z * 0.019D)
                    * 0.18D * strength;
            Vec3 next = start.add(direction.scale(along)).add(side.scale(jitter));
            line(buffer, matrix, camera, previous, next,
                    (solidCore ? 0.085D : 0.10D) * strength, color);
            if (!solidCore && i % 3 == 1) {
                double branchSide = ((i & 1) == 0 ? 1.0D : -1.0D)
                        * (0.28D + (i % 4) * 0.09D) * strength;
                Vec3 root = previous.lerp(next, 0.55D);
                line(buffer, matrix, camera, root,
                        root.add(side.scale(branchSide)).add(direction.scale(0.16D)),
                        0.035D * strength, color);
            }
            previous = next;
        }
    }

    private static void drawBoundaryFlameTongue(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 base, Vec3 direction, Vec3 side, double height,
            double phase, float strength, int outer, int middle, int inner,
            boolean solidCore, int index) {
        double sway = Math.sin(phase) * (0.18D + height * 0.105D) * strength;
        double curl = Math.sin(phase * 0.57D + index * 0.83D)
                * (0.14D + height * 0.045D) * strength;
        double flick = Math.sin(phase * 1.73D + index * 0.37D) * 0.16D * strength;
        Vec3 p1 = base.add(side.scale(sway * 0.12D))
                .add(direction.scale(curl * 0.18D)).add(0.0D, height * 0.20D, 0.0D);
        Vec3 p2 = base.add(side.scale(sway * 0.43D))
                .add(direction.scale(curl * 0.72D)).add(0.0D, height * 0.47D, 0.0D);
        Vec3 p3 = base.add(side.scale(sway * 0.78D + flick * 0.30D))
                .add(direction.scale(curl)).add(0.0D, height * 0.73D, 0.0D);
        Vec3 tip = base.add(side.scale(sway + flick))
                .add(direction.scale(curl * 0.42D - flick * 0.28D))
                .add(0.0D, height, 0.0D);

        Vec3[] flame = { base, p1, p2, p3, tip };
        double baseWidth = (solidCore ? 0.36D : 0.43D) * strength;
        double twist = phase * 0.34D + index * 0.71D;
        drawTaperedFlameVolume(buffer, matrix, flame, direction, side,
                new double[] { baseWidth, baseWidth * 0.96D, baseWidth * 0.72D,
                        baseWidth * 0.38D, 0.018D },
                solidCore ? 0.62D : 0.76D, twist, outer, solidCore ? 5 : 6);
        if (solidCore) return;

        Vec3 mid1 = base.lerp(p1, 0.96D).add(0.0D, 0.025D, 0.0D);
        Vec3 mid2 = p1.lerp(p2, 0.86D);
        Vec3 mid3 = p2.lerp(p3, 0.54D);
        drawTaperedFlameVolume(buffer, matrix,
                new Vec3[] { base.add(0.0D, 0.035D, 0.0D), mid1, mid2, mid3 },
                direction, side,
                new double[] { baseWidth * 0.58D, baseWidth * 0.52D,
                        baseWidth * 0.31D, 0.012D },
                0.70D, twist + 0.52D, middle, 5);
        drawTaperedFlameVolume(buffer, matrix,
                new Vec3[] { base.add(0.0D, 0.06D, 0.0D),
                        base.lerp(p1, 0.82D), p1.lerp(p2, 0.48D) },
                direction, side,
                new double[] { baseWidth * 0.26D, baseWidth * 0.21D, 0.009D },
                0.66D, twist + 0.95D, inner, 5);

        if (index % 5 == 2 && height > 1.35D * strength) {
            Vec3 forkRoot = p1.lerp(p2, 0.34D);
            Vec3 forkTip = forkRoot.add(side.scale(-sway * 0.48D
                    + ((index & 1) == 0 ? 0.25D : -0.25D) * strength))
                    .add(direction.scale(-curl * 0.35D))
                    .add(0.0D, height * 0.23D, 0.0D);
            Vec3 forkMid = forkRoot.lerp(forkTip, 0.58D)
                    .add(direction.scale(Math.sin(phase + index) * 0.08D * strength));
            drawTaperedFlameVolume(buffer, matrix,
                    new Vec3[] { forkRoot, forkMid, forkTip }, direction, side,
                    new double[] { baseWidth * 0.20D, baseWidth * 0.13D, 0.008D },
                    0.62D, twist + 1.31D, middle, 5);
        }
    }

    private static void drawBoundaryLowFlame(BufferBuilder buffer, Matrix4f matrix,
            Vec3 base, Vec3 direction, Vec3 side, double height, double phase,
            float strength, int outer, int middle, boolean solidCore, int index) {
        double bend = Math.sin(phase) * 0.13D * strength;
        double drift = Math.sin(phase * 0.63D + index) * 0.08D * strength;
        Vec3 middlePoint = base.add(side.scale(bend * 0.38D))
                .add(direction.scale(drift)).add(0.0D, height * 0.52D, 0.0D);
        Vec3 tip = base.add(side.scale(bend))
                .add(direction.scale(drift * 0.42D)).add(0.0D, height, 0.0D);
        double width = (solidCore ? 0.24D : 0.29D) * strength;
        double twist = phase * 0.31D + index * 0.83D;
        drawTaperedFlameVolume(buffer, matrix,
                new Vec3[] { base, middlePoint, tip }, direction, side,
                new double[] { width, width * 0.66D, 0.012D },
                0.72D, twist, outer, 5);
        if (!solidCore) {
            drawTaperedFlameVolume(buffer, matrix,
                    new Vec3[] { base.add(0.0D, 0.025D, 0.0D),
                            base.lerp(middlePoint, 0.72D) }, direction, side,
                    new double[] { width * 0.47D, 0.008D },
                    0.66D, twist + 0.57D, middle, 4);
        }
    }

    /**
     * Builds a curved, tapered polygonal flame body. Unlike a billboard this
     * has an actual horizontal cross-section, so walking around the boundary
     * changes its silhouette and never collapses it into a flat sheet.
     */
    private static void drawTaperedFlameVolume(BufferBuilder buffer, Matrix4f matrix,
            Vec3[] points, Vec3 axisA, Vec3 axisB, double[] radii,
            double depthScale, double twist, int color, int sides) {
        if (points.length < 2 || points.length != radii.length) return;
        Vec3 a = axisA.normalize();
        Vec3 b = axisB.normalize();
        int faces = Math.max(4, sides);
        for (int i = 0; i < points.length - 1; i++) {
            double taper = radii[0] <= 0.0001D ? 0.0D : radii[i] / radii[0];
            double nextTaper = radii[0] <= 0.0001D ? 0.0D : radii[i + 1] / radii[0];
            double phaseA = twist + i * 0.36D + (1.0D - taper) * 0.42D;
            double phaseB = twist + (i + 1) * 0.36D + (1.0D - nextTaper) * 0.42D;
            for (int face = 0; face < faces; face++) {
                double angle0 = face * Math.PI * 2.0D / faces;
                double angle1 = (face + 1) * Math.PI * 2.0D / faces;
                Vec3 a0 = flameRingOffset(a, b, radii[i], depthScale,
                        phaseA + angle0);
                Vec3 a1 = flameRingOffset(a, b, radii[i], depthScale,
                        phaseA + angle1);
                Vec3 b1 = flameRingOffset(a, b, radii[i + 1], depthScale,
                        phaseB + angle1);
                Vec3 b0 = flameRingOffset(a, b, radii[i + 1], depthScale,
                        phaseB + angle0);
                float faceLight = 0.76F + 0.24F
                        * (float) Math.max(0.0D, Math.sin(angle0 + twist));
                quad(buffer, matrix, points[i].add(a0), points[i].add(a1),
                        points[i + 1].add(b1), points[i + 1].add(b0),
                        shadeColor(color, faceLight));
            }
        }
    }

    private static Vec3 flameRingOffset(Vec3 axisA, Vec3 axisB,
            double radius, double depthScale, double angle) {
        return axisA.scale(Math.cos(angle) * radius)
                .add(axisB.scale(Math.sin(angle) * radius * depthScale));
    }

    private static void drawGapWisp(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 base, Vec3 direction, float strength, int color, double phase) {
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 p1 = base.add(side.scale(Math.sin(phase) * 0.12D * strength))
                .add(0.0D, 0.42D * strength, 0.0D);
        Vec3 p2 = base.add(side.scale(Math.sin(phase + 0.9D) * 0.24D * strength))
                .add(0.0D, 0.92D * strength, 0.0D);
        line(buffer, matrix, camera, base, p1, 0.065D * strength, color);
        line(buffer, matrix, camera, p1, p2, 0.032D * strength, color);
    }

    private static void drawBoundaryWallBreak(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float fade = 1.0F - t;
        int ember = color(1.0F, 0.035F, 0.045F, 0.78F * fade);
        int ash = color(0.18F, 0.015F, 0.022F, 0.66F * fade);
        int motes = quality <= 0 ? 10 : quality == 1 ? 17 : 24;
        for (int i = 0; i < motes; i++) {
            double along = ((i * 0.61803398875D + effect.seed * 0.000019D) % 1.0D + 1.0D) % 1.0D;
            Vec3 base = effect.start.lerp(effect.end, along).add(0.0D, 0.12D, 0.0D);
            Vec3 drift = deterministicDirection(effect.seed, i)
                    .multiply(0.55D, 1.5D, 0.55D).scale(t * 2.2D * strength)
                    .add(0.0D, t * (1.0D + i % 4 * 0.35D) * strength, 0.0D);
            Vec3 mote = base.add(drift);
            line(buffer, matrix, camera, mote,
                    mote.add(0.0D, 0.20D + (i % 3) * 0.12D, 0.0D),
                    0.030D * fade, i % 3 == 0 ? ash : ember);
        }
        line(buffer, matrix, camera, effect.start, effect.end,
                (0.14D * fade + 0.015D) * strength, ash);
    }

    private static void drawHaloTorii(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 center, Vec3 across, Vec3 up, double scale,
            int core, int shadow) {
        Vec3 side = across.normalize();
        Vec3 vertical = up.normalize();
        Vec3 leftBase = center.subtract(side.scale(scale * 0.42D));
        Vec3 rightBase = center.add(side.scale(scale * 0.42D));
        Vec3 leftTop = leftBase.add(vertical.scale(scale * 0.78D));
        Vec3 rightTop = rightBase.add(vertical.scale(scale * 0.78D));
        line(buffer, matrix, camera, leftBase, leftTop, scale * 0.075D, shadow);
        line(buffer, matrix, camera, rightBase, rightTop, scale * 0.075D, shadow);
        line(buffer, matrix, camera,
                leftTop.subtract(side.scale(scale * 0.24D)),
                rightTop.add(side.scale(scale * 0.24D)), scale * 0.105D, core);
        Vec3 lower = vertical.scale(scale * 0.19D);
        line(buffer, matrix, camera, leftTop.subtract(lower), rightTop.subtract(lower),
                scale * 0.055D, core);
    }

    private static void drawBoundaryBlade3d(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, Vec3 right, Vec3 forward, double scale,
            int red, int dark, int white) {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = right.normalize();
        Vec3 depth = forward.normalize();
        double[] widths = {0.42D, 0.39D, 0.35D, 0.31D, 0.27D, 0.22D, 0.16D};
        for (int i = 0; i < widths.length; i++) {
            double y = (2.65D - i * 0.88D) * scale;
            double outerWidth = widths[i] * scale;
            Vec3 segment = center.add(0.0D, y, 0.0D);
            orientedBox(buffer, matrix, segment, side, up, depth,
                    outerWidth, 0.39D * scale, 0.28D * scale, dark);
            orientedBox(buffer, matrix, segment.add(depth.scale(0.015D)),
                    side, up, depth,
                    outerWidth * 0.72D, 0.34D * scale, 0.22D * scale, red);
            orientedBox(buffer, matrix, segment.add(depth.scale(0.035D)),
                    side, up, depth,
                    Math.max(0.055D, outerWidth * 0.24D),
                    0.31D * scale, 0.245D * scale, white);
            double beamWidth = (1.28D - i * 0.085D) * scale;
            Vec3 beam = segment.add(up.scale(0.40D * scale));
            orientedBox(buffer, matrix, beam, side, up, depth,
                    beamWidth, 0.105D * scale, 0.34D * scale, dark);
            orientedBox(buffer, matrix, beam.add(depth.scale(0.025D)),
                    side, up, depth,
                    beamWidth * 0.91D, 0.070D * scale, 0.285D * scale, red);
        }
        Vec3 crown = center.add(0.0D, 3.55D * scale, 0.0D);
        orientedBox(buffer, matrix, crown, side, up, depth,
                1.62D * scale, 0.13D * scale, 0.38D * scale, dark);
        orientedBox(buffer, matrix, crown.add(depth.scale(0.03D)),
                side, up, depth,
                1.47D * scale, 0.08D * scale, 0.32D * scale, red);
    }

    private static void orientedBox(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
            double halfX, double halfY, double halfZ, int color) {
        Vec3 x = axisX.scale(halfX);
        Vec3 y = axisY.scale(halfY);
        Vec3 z = axisZ.scale(halfZ);
        Vec3 nnn = center.subtract(x).subtract(y).subtract(z);
        Vec3 pnn = center.add(x).subtract(y).subtract(z);
        Vec3 ppn = center.add(x).add(y).subtract(z);
        Vec3 npn = center.subtract(x).add(y).subtract(z);
        Vec3 nnp = center.subtract(x).subtract(y).add(z);
        Vec3 pnp = center.add(x).subtract(y).add(z);
        Vec3 ppp = center.add(x).add(y).add(z);
        Vec3 npp = center.subtract(x).add(y).add(z);
        quad(buffer, matrix, nnn, pnn, ppn, npn, color);
        quad(buffer, matrix, pnp, nnp, npp, ppp, color);
        quad(buffer, matrix, nnp, nnn, npn, npp, color);
        quad(buffer, matrix, pnn, pnp, ppp, ppn, color);
        quad(buffer, matrix, npn, ppn, ppp, npp, color);
        quad(buffer, matrix, nnp, pnp, pnn, nnn, color);
    }

    private static void drawToriiSweepCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean personal = minecraft.player != null
                && minecraft.player.getId() == effect.targetEntityId;
        float visibility = personal ? 1.0F : 0.30F;
        float fade = effect.age >= 136
                ? Mth.clamp((154 - effect.age) / 18.0F, 0.0F, 1.0F) : 1.0F;
        Vec3 pivot = effect.start.add(0.0D, 0.08D, 0.0D);
        Vec3 target = effect.end.add(0.0D, 0.08D, 0.0D);
        Vec3 aim = target.subtract(pivot).multiply(1.0D, 0.0D, 1.0D);
        if (aim.lengthSqr() < 0.0001D) aim = new Vec3(0.0D, 0.0D, 1.0D);
        double baseAngle = Math.atan2(aim.z, aim.x);
        double open = Math.toRadians(scissorOpenDegrees(effect.age));
        double targetDistance = Math.max(7.0D, aim.length());
        boolean nearImpact = Math.abs(effect.age - 34) <= 2
                || Math.abs(effect.age - 72) <= 2
                || Math.abs(effect.age - 136) <= 2;
        int core = color(0.34F, 0.003F, 0.012F,
                (nearImpact ? 0.96F : 0.88F) * visibility * fade);
        int gatesPerArm = quality <= 0 ? 4 : quality == 1 ? 6 : 8;
        for (int side = -1; side <= 1; side += 2) {
            double angle = baseAngle + side * open;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            double reach = targetDistance + 3.5D;
            Vec3 tip = pivot.add(direction.scale(reach));
            line(buffer, matrix, camera, pivot, tip,
                    (nearImpact ? 0.105D : 0.075D) * strength, core);
            for (int i = 0; i < gatesPerArm; i++) {
                double distance = Mth.lerp((i + 1.0D) / (gatesPerArm + 1.0D),
                        2.2D, reach - 0.6D);
                Vec3 gate = pivot.add(direction.scale(distance));
                drawTorii(buffer, matrix, camera, gate,
                        (float) Math.toDegrees(angle + Math.PI * 0.5D),
                        0.30D * strength, core);
            }
        }
    }

    private static void drawToriiCageCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float warning = Mth.clamp(effect.age / 25.0F, 0.0F, 1.0F);
        float recovery = Mth.clamp((t - 0.90F) / 0.10F, 0.0F, 1.0F);
        float alpha = (0.58F + warning * 0.30F) * (1.0F - recovery);
        int core = color(0.34F, 0.003F, 0.012F, alpha);
        Vec3 base = effect.start.add(0.0D, 0.05D, 0.0D);
        ringHorizontal(buffer, matrix, base, 4.6D * strength,
                0.035D + warning * 0.020D, core, 48);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 0.25D;
            Vec3 gate = base.add(Math.cos(angle) * 4.6D, 0.0D,
                    Math.sin(angle) * 4.6D);
            drawTorii(buffer, matrix, camera, gate,
                    (float) Math.toDegrees(angle + Math.PI * 0.5D),
                    0.38D * strength, core);
        }
    }

    private static void drawBoundaryFlashCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        float reveal = Mth.clamp((effect.age - 12.0F) / 148.0F, 0.0F, 1.0F);
        if (reveal <= 0.001F) return;
        Vec3 forward = effect.end.subtract(effect.start).multiply(1.0D, 0.0D, 1.0D);
        if (forward.lengthSqr() < 0.0001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 haloCenter = effect.start.add(forward.scale(-0.75D))
                .add(0.0D, 0.15D, 0.0D);
        int red = color(0.64F, 0.006F, 0.022F, 0.86F * reveal);
        int dark = color(0.16F, 0.001F, 0.006F, 0.94F * reveal);
        int pale = color(0.96F, 0.66F, 0.68F, 0.78F * reveal);
        double radius = (0.55D + reveal * 2.75D) * strength;
        int gates = Math.min(8, Math.max(0, (int) ((effect.age - 12) / 18.0D) + 1));
        double rotation = effect.age * (0.023D - reveal * 0.008D)
                + effect.seed * 0.00013D;
        for (int i = 0; i < gates; i++) {
            double angle = rotation + i * Math.PI * 0.25D;
            Vec3 radial = planeVector(right, Math.cos(angle), Math.sin(angle));
            Vec3 tangent = planeVector(right, -Math.sin(angle), Math.cos(angle));
            Vec3 gate = haloCenter.add(radial.scale(radius));
            drawHaloTorii(buffer, matrix, camera, gate, radial, tangent,
                    0.36D * strength, i % 2 == 0 ? pale : red, dark);
        }
        if (reveal > 0.20F) {
            drawBoundaryBlade3d(buffer, matrix,
                    haloCenter.add(0.0D, 0.55D, 0.0D), right, forward,
                    (0.20D + reveal * 0.80D) * strength,
                    red, dark, pale);
        }
    }

    private static void drawBoundaryFlashReleaseCore(BufferBuilder buffer, Matrix4f matrix,
            Effect effect, float strength) {
        float collapse = Mth.clamp(effect.age / 20.0F, 0.0F, 1.0F);
        float fade = effect.age < effect.duration - 8 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 8.0F, 0.0F, 1.0F);
        Vec3 start = effect.start;
        Vec3 groundStart = new Vec3(start.x, effect.end.y, start.z);
        Vec3 direction = effect.end.subtract(groundStart);
        if (direction.lengthSqr() < 0.0001D) return;
        direction = direction.normalize();
        Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
        Vec3 haloCenter = start.subtract(direction.scale(0.75D));
        int red = color(0.66F, 0.006F, 0.020F, 0.92F * fade);
        int dark = color(0.14F, 0.001F, 0.005F, 0.96F * fade);
        int pale = color(1.0F, 0.72F, 0.72F, 0.86F * fade);
        drawBoundaryBlade3d(buffer, matrix,
                haloCenter.add(0.0D, 0.55D, 0.0D), right, direction,
                (1.0D + collapse * 0.72D) * strength,
                red, dark, pale);
    }

    private static void drawBoundaryWallCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength) {
        drawBoundaryFlameField(buffer, matrix, camera, effect, strength, 1, true);
    }

    private static void drawScissorResultCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int result) {
        float fade = (1.0F - t) * (1.0F - t);
        int core = result == 1 || result == 3
                ? color(0.72F, 0.38F, 0.035F, fade * 0.92F)
                : color(0.34F, 0.003F, 0.012F, fade * 0.92F);
        double angle = Math.toRadians(effect.yaw);
        Vec3 a = new Vec3(Math.cos(angle + Math.PI * 0.25D), 0.55D,
                Math.sin(angle + Math.PI * 0.25D)).normalize();
        Vec3 b = new Vec3(Math.cos(angle - Math.PI * 0.25D), -0.55D,
                Math.sin(angle - Math.PI * 0.25D)).normalize();
        double reach = (result == 0 ? 1.25D : 2.2D) * strength * (0.6D + t * 0.4D);
        line(buffer, matrix, camera, effect.start.subtract(a.scale(reach)),
                effect.start.add(a.scale(reach)), 0.062D * strength, core);
        line(buffer, matrix, camera, effect.start.subtract(b.scale(reach)),
                effect.start.add(b.scale(reach)), 0.062D * strength, core);
    }

    private static void drawPursuitSwordCore(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = 1.0F - t;
        int core = color(0.34F, 0.004F, 0.018F, 0.86F * fade);
        Vec3 direction = effect.end.subtract(effect.start);
        if (direction.lengthSqr() < 0.0001D) return;
        Vec3 normalized = direction.normalize();
        Vec3 tail = effect.start.subtract(normalized.scale(1.15D));
        line(buffer, matrix, camera, tail, effect.start,
                0.065D * strength, core);
        line(buffer, matrix, camera, effect.start, effect.end,
                0.018D * strength, core);
    }

    private static void drawToriiSweep(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean personal = minecraft.player != null
                && minecraft.player.getId() == effect.targetEntityId;
        float visibility = personal ? 1.0F : 0.38F;
        Vec3 pivot = effect.start.add(0.0D, 0.08D, 0.0D);
        Vec3 center = effect.end.add(0.0D, 0.08D, 0.0D);
        Vec3 aim = center.subtract(pivot).multiply(1.0D, 0.0D, 1.0D);
        if (aim.lengthSqr() < 0.0001D) aim = new Vec3(0.0D, 0.0D, 1.0D);
        double baseAngle = Math.atan2(aim.z, aim.x);
        double openDegrees = scissorOpenDegrees(effect.age);
        double open = Math.toRadians(openDegrees);
        float fade = effect.age >= 136
                ? Mth.clamp((154 - effect.age) / 18.0F, 0.0F, 1.0F) : 1.0F;
        int red = color(1.0F, 0.02F, 0.07F, 0.84F * visibility * fade);
        int darkRed = color(0.48F, 0.006F, 0.02F, 0.50F * visibility * fade);
        int white = color(1.0F, 0.95F, 0.92F,
                (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? 0.82F : 0.27F)
                        * visibility * fade);
        Vec3 forward = new Vec3(Math.cos(baseAngle), 0.0D, Math.sin(baseAngle));
        double targetDistance = Math.max(7.0D,
                center.subtract(pivot).multiply(1.0D, 0.0D, 1.0D).length());
        boolean nearImpact = Math.abs(effect.age - 34) <= 2
                || Math.abs(effect.age - 72) <= 2
                || Math.abs(effect.age - 136) <= 2;
        int armColor = nearImpact ? white : red;
        int gatesPerArm = quality <= 0 ? 4 : quality == 1 ? 6 : 8;
        for (int side = -1; side <= 1; side += 2) {
            double angle = baseAngle + side * open;
            Vec3 direction = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            double reach = targetDistance + 3.5D;
            Vec3 tip = pivot.add(direction.scale(reach));
            line(buffer, matrix, camera, pivot, tip,
                    (nearImpact ? 0.13D : 0.075D) * strength, armColor);
            line(buffer, matrix, camera, pivot, tip,
                    0.19D * strength, darkRed);
            for (int i = 0; i < gatesPerArm; i++) {
                double distance = Mth.lerp((i + 1.0D) / (gatesPerArm + 1.0D),
                        2.2D, reach - 0.6D);
                Vec3 gate = pivot.add(direction.scale(distance));
                drawTorii(buffer, matrix, camera, gate,
                        (float) Math.toDegrees(angle + Math.PI * 0.5D),
                        0.34D * strength, armColor);
            }
        }
        double lockRadius = 0.72D + 0.08D
                * Math.sin((effect.age + effect.seed * 0.01D) * 0.48D);
        ringHorizontal(buffer, matrix, center, lockRadius * strength,
                0.035D, nearImpact ? white : red, segments(34, quality));
    }

    private static double scissorOpenDegrees(int age) {
        if (age < 14) return 18.0D;
        if (age <= 34) return Mth.lerp((age - 14) / 20.0D, 18.0D, -2.5D);
        if (age < 42) return -2.5D;
        if (age < 54) return 16.0D;
        if (age <= 72) return Mth.lerp((age - 54) / 18.0D, 16.0D, -2.5D);
        if (age < 80) return -2.5D;
        if (age < 92) return 17.0D;
        if (age <= 108) return Mth.lerp((age - 92) / 16.0D, 17.0D, 6.0D);
        if (age <= 116) return Mth.lerp((age - 108) / 8.0D, 6.0D, 17.0D);
        if (age <= 136) return Mth.lerp((age - 116) / 20.0D, 17.0D, -3.0D);
        return -3.0D;
    }

    private static void drawScissorResult(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int result) {
        float fade = (1.0F - t) * (1.0F - t);
        Vec3 center = effect.start;
        int red = color(1.0F, 0.02F, 0.07F, fade * 0.88F);
        int darkRed = color(0.48F, 0.004F, 0.018F, fade * 0.92F);
        int white = color(1.0F, 0.96F, 0.92F,
                fade * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? 1.0F : 0.30F));
        int gold = color(1.0F, 0.62F, 0.12F, fade);
        double angle = Math.toRadians(effect.yaw);
        Vec3 a = new Vec3(Math.cos(angle + Math.PI * 0.25D), 0.55D,
                Math.sin(angle + Math.PI * 0.25D)).normalize();
        Vec3 b = new Vec3(Math.cos(angle - Math.PI * 0.25D), -0.55D,
                Math.sin(angle - Math.PI * 0.25D)).normalize();
        int resultColor = result == 1 ? (effect.intensity > 1.0F ? gold : white)
                : result == 2 ? darkRed : result == 3 ? gold : red;
        double reach = (result == 0 ? 1.4D : 2.5D) * strength * (0.6D + t * 0.4D);
        line(buffer, matrix, camera, center.subtract(a.scale(reach)),
                center.add(a.scale(reach)), 0.09D * strength, resultColor);
        line(buffer, matrix, camera, center.subtract(b.scale(reach)),
                center.add(b.scale(reach)), 0.09D * strength, resultColor);
        if (result == 1 || result == 3) {
            ringHorizontal(buffer, matrix, center.add(0.0D, 0.05D, 0.0D),
                    (0.3D + t * (result == 3 ? 4.0D : 2.0D)) * strength,
                    0.07D * fade + 0.02D, resultColor, 42);
        }
    }

    private static void drawPursuitLock(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 target = effect.end.add(0.0D, 1.35D, 0.0D);
        float pulse = 0.62F + 0.38F * Mth.sin((effect.age + effect.seed * 0.01F) * 0.55F);
        int red = color(1.0F, 0.02F, 0.075F, 0.72F * pulse);
        double radius = (0.72D + 0.10D * pulse) * strength;
        Vec3 right = cameraRight(target, camera);
        ringVertical(buffer, matrix, target, right, radius, 0.04D, red,
                segments(32, quality));
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI * 0.5D;
            Vec3 direction = planeVector(right, Math.cos(angle), Math.sin(angle));
            line(buffer, matrix, camera, target.add(direction.scale(radius * 0.7D)),
                    target.add(direction.scale(radius * 1.18D)), 0.028D, red);
        }
    }

    private static void drawPursuitSword(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = 1.0F - t;
        int red = color(1.0F, 0.025F, 0.09F, 0.82F * fade);
        int white = color(1.0F, 0.94F, 0.92F,
                fade * (ClientVisualConfig.ENABLE_BLADE_COMBAT_HIGHLIGHTS.get() ? 0.72F : 0.22F));
        line(buffer, matrix, camera, effect.start, effect.end,
                0.018D * strength, red);
        Vec3 direction = effect.end.subtract(effect.start);
        if (direction.lengthSqr() > 0.0001D) {
            Vec3 tail = effect.start.subtract(direction.normalize().scale(1.35D));
            line(buffer, matrix, camera, tail, effect.start,
                    0.075D * strength * fade, white);
            line(buffer, matrix, camera, tail.subtract(direction.normalize().scale(0.7D)),
                    effect.start, 0.16D * strength * fade, red);
        }
    }

    private static void drawMoonEchoField(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        Vec3 center = effect.end;
        float fade = Mth.clamp((1.0F - t) * 1.8F, 0.25F, 1.0F);
        int purple = color(0.48F, 0.07F, 0.68F, 0.62F * fade);
        int red = color(0.96F, 0.02F, 0.07F, 0.54F * fade);
        ringHorizontal(buffer, matrix, center.add(0.0D, 0.08D, 0.0D),
                4.2D * strength, 0.035D, purple, segments(48, quality));
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI * 0.5D + effect.seed * 0.18D;
            Vec3 shard = center.add(Math.cos(angle) * 4.2D, 1.0D,
                    Math.sin(angle) * 4.2D);
            Vec3 right = cameraRight(shard, camera);
            ringVertical(buffer, matrix, shard, right,
                    0.58D + 0.08D * Mth.sin(effect.age * 0.35F + i),
                    0.035D, i == effect.seed ? red : purple, segments(24, quality));
            for (int piece = 0; piece < 3; piece++) {
                Vec3 direction = deterministicDirection(effect.seed + i * 31, piece)
                        .multiply(0.35D, 0.8D, 0.35D);
                line(buffer, matrix, camera, shard.add(direction.scale(-0.45D)),
                        shard.add(direction.scale(0.45D)), 0.025D, purple);
            }
        }
    }

    private static void drawMoonEchoResult(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, boolean success) {
        float fade = (1.0F - t) * (1.0F - t);
        int purple = color(0.46F, 0.055F, 0.66F, fade * 0.9F);
        int result = success ? color(1.0F, 0.62F, 0.12F, fade)
                : color(0.62F, 0.008F, 0.025F, fade);
        for (int i = 0; i < 10; i++) {
            Vec3 direction = deterministicDirection(effect.seed, i);
            Vec3 outer = effect.start.add(direction.scale((0.3D + t * 2.8D) * strength));
            line(buffer, matrix, camera, outer,
                    effect.start.add(direction.scale((0.05D + t * 0.6D) * strength)),
                    0.035D, i % 3 == 0 ? result : purple);
        }
        if (success) {
            line(buffer, matrix, camera, effect.start.add(-1.8D, -0.8D, 0.0D),
                    effect.start.add(1.8D, 1.2D, 0.0D), 0.08D * strength, result);
        }
    }

    private static void drawSwordWheel(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength, int quality) {
        float reveal = Mth.clamp(effect.age / Math.max(6.0F, effect.seed * 3.0F), 0.0F, 1.0F);
        float fade = Mth.clamp((1.0F - t) * 4.0F, 0.0F, 1.0F);
        int red = color(0.98F, 0.02F, 0.075F, 0.56F * reveal * fade);
        int purple = color(0.42F, 0.04F, 0.55F, 0.38F * reveal * fade);
        Vec3 center = effect.start;
        ringHorizontal(buffer, matrix, center, 3.8D * strength, 0.04D, red,
                segments(48, quality));
        int count = Math.max(1, effect.seed);
        for (int i = 0; i < count; i++) {
            if (effect.age < i * 3) continue;
            double angle = effect.age * 0.065D + i * Math.PI * 2.0D / count;
            Vec3 point = center.add(Math.cos(angle) * 3.8D, 0.0D,
                    Math.sin(angle) * 3.8D);
            Vec3 next = center.add(Math.cos(angle + Math.PI * 2.0D / count) * 3.8D,
                    0.0D, Math.sin(angle + Math.PI * 2.0D / count) * 3.8D);
            line(buffer, matrix, camera, point, next, 0.022D, i % 2 == 0 ? red : purple);
        }
        if (effect.targetEntityId >= 0) {
            ringHorizontal(buffer, matrix, effect.end.add(0.0D, 0.08D, 0.0D),
                    (1.4D - reveal * 0.7D) * strength, 0.03D, red,
                    segments(30, quality));
        }
    }

    private static void drawSwordWheelBreak(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float fade = (1.0F - t) * (1.0F - t);
        int red = color(1.0F, 0.02F, 0.07F, fade);
        int gold = color(1.0F, 0.62F, 0.12F, fade);
        for (int i = 0; i < 8; i++) {
            Vec3 direction = deterministicDirection(effect.seed, i);
            line(buffer, matrix, camera, effect.start,
                    effect.start.add(direction.scale((0.4D + t * 2.3D) * strength)),
                    0.04D, effect.seed == 0 && i % 2 == 0 ? gold : red);
        }
    }

    private static void drawMirrorFailure(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float t, float strength) {
        float delay = Mth.clamp((effect.age - 3.0F) / 5.0F, 0.0F, 1.0F);
        float fade = (1.0F - t) * delay;
        int darkRed = color(0.62F, 0.006F, 0.02F, fade * 0.92F);
        double angle = Math.toRadians(effect.yaw);
        Vec3 right = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 center = effect.start.subtract(new Vec3(-right.z, 0.0D, right.x).scale(0.55D));
        line(buffer, matrix, camera, center.subtract(right.scale(1.8D)).add(0.0D, -0.8D, 0.0D),
                center.add(right.scale(1.8D)).add(0.0D, 1.1D, 0.0D),
                0.12D * strength * fade, darkRed);
    }

    private static void drawMeleeCircle(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        // The textured crescent is the visible attack. A faint ground ring only
        // marks the real five-block circular hit box at the strike frame.
        float fade = timedFade(effect.age, 3, 5);
        if (fade <= 0.0F) return;
        int red = color(0.75F, 0.01F, 0.035F, 0.18F * fade);
        ringHorizontal(buffer, matrix, effect.start.add(0.0D, 0.055D, 0.0D),
                5.0D, 0.025D * strength, red, segments(34, quality));
    }

    private static void drawMeleeCombo(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        // Combo timing is carried by three textured crescents in the texture pass.
    }

    private static void drawStepIaido(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength) {
        float whiteFade = timedFade(effect.age, 0, 5);
        float redFade = timedFade(effect.age, 1, 8);
        Vec3 side = horizontalSide(effect.start, effect.end);
        if (whiteFade > 0.0F) {
            int white = color(1.0F, 0.98F, 0.94F, 0.98F * whiteFade);
            line(buffer, matrix, camera, effect.start, effect.end,
                    0.012D * strength, white);
        }
        if (redFade > 0.0F) {
            int red = color(1.0F, 0.02F, 0.06F, 0.72F * redFade);
            line(buffer, matrix, camera, effect.start.subtract(side.scale(0.10D)),
                    effect.end.subtract(side.scale(0.10D)), 0.018D * strength, red);
            line(buffer, matrix, camera,
                    effect.start.add(side.scale(0.18D)).add(0.0D, -0.24D, 0.0D),
                    effect.start.subtract(side.scale(0.28D)).add(0.0D, -0.24D, 0.0D),
                    0.025D * strength, red);
        }
    }

    private static void drawHeavyCleave(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        Vec3 forward = flatDirection(effect.start, effect.end, effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 impact = effect.end.add(0.0D, -0.68D, 0.0D);
        if (effect.age >= 7) {
            float crackFade = timedFade(effect.age, 7, 9);
            int crack = color(0.62F, 0.008F, 0.025F, 0.72F * crackFade);
            for (int i = -2; i <= 2; i++) {
                Vec3 ray = forward.yRot((float) (i * 0.16D));
                double length = (2.5D + (i & 1) * 0.65D) * strength;
                line(buffer, matrix, camera, impact.add(0.0D, 0.04D, 0.0D),
                        impact.add(ray.scale(length)).add(0.0D, 0.04D, 0.0D),
                        0.035D, crack);
            }
        }
    }

    private static void drawFlashCounter(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        Vec3 oldCenter = effect.start.add(0.0D, 0.02D, 0.0D);
        Vec3 newOrigin = effect.end;
        Vec3 forward = yawDirection(effect.yaw);
        float ringFade = timedFade(effect.age, 0, 6);
        if (ringFade > 0.0F) {
            int red = color(1.0F, 0.025F, 0.07F, 0.72F * ringFade);
            ringHorizontal(buffer, matrix, oldCenter,
                    (1.0D + effect.age * 0.58D) * strength,
                    0.065D, red, segments(32, quality));
        }
        float stepFade = timedFade(effect.age, 1, 8);
        if (stepFade > 0.0F) {
            int purple = color(0.46F, 0.045F, 0.58F, 0.56F * stepFade);
            int white = color(1.0F, 0.96F, 0.92F, 0.78F * stepFade);
            line(buffer, matrix, camera, effect.start.add(0.0D, 0.8D, 0.0D), newOrigin,
                    0.12D * strength, purple);
            line(buffer, matrix, camera, effect.start.add(0.0D, 0.8D, 0.0D), newOrigin,
                    0.028D * strength, white);
        }
        float lineFade = timedFade(effect.age, 6, 9);
        if (lineFade > 0.0F) {
            Vec3 lineEnd = newOrigin.add(forward.scale(30.0D));
            int white = color(1.0F, 0.98F, 0.94F, 0.94F * lineFade);
            line(buffer, matrix, camera, newOrigin, lineEnd,
                    0.012D * strength, white);
        }
    }

    private static void drawSakuraEnd(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        drawSakuraBeat(buffer, matrix, camera, effect, 3, 9,
                3.8D, strength, quality, false);
        drawSakuraBeat(buffer, matrix, camera, effect, 15, 10,
                5.2D, strength * 1.08F, quality, true);
    }

    private static void drawSakuraBeat(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, int trigger, int life, double radius,
            float strength, int quality, boolean finisher) {
        float pulse = timedFade(effect.age, trigger, life);
        if (pulse <= 0.0F) return;
        Vec3 center = effect.start.add(0.0D, 1.05D, 0.0D);
        int pale = color(1.0F, 0.62F, 0.70F, 0.72F * pulse);
        double spread = radius * (0.72D + (1.0D - pulse) * 0.28D);
        ringHorizontal(buffer, matrix, effect.start.add(0.0D, 0.16D, 0.0D),
                spread, (finisher ? 0.085D : 0.055D) * strength,
                pale, segments(34, quality));
        int petals = quality <= 0 ? 5 : quality == 1 ? 8 : 12;
        for (int i = 0; i < petals; i++) {
            Vec3 direction = deterministicDirection(effect.seed + trigger * 31, i);
            Vec3 petal = center.add(direction.scale(radius * (0.35D + i * 0.035D)));
            Vec3 drift = new Vec3(-direction.z, 0.15D, direction.x)
                    .normalize().scale(0.16D + (i % 3) * 0.05D);
            line(buffer, matrix, camera, petal.subtract(drift), petal.add(drift),
                    0.025D * strength, pale);
        }
    }

    private static void renderCircleSlashes(Matrix4f matrix, Effect effect,
            float age, float strength) {
        float alpha = peakedAlpha(age, 4.0F, 7.0F);
        if (alpha <= 0.0F) return;
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = effect.start.add(0.0D, 1.0D, 0.0D);
        Vec3 across = right.scale(4.75D * strength);
        Vec3 sweep = forward.scale(2.75D * strength).add(0.0D, 0.72D * strength, 0.0D);
        texturedSlash(matrix, center, across, sweep, alpha * 0.92F,
                255, 255, 255, false);
        texturedSlash(matrix, center.add(0.0D, 0.10D, 0.0D), across.reverse(), sweep,
                alpha * 0.76F, 255, 210, 215, true);
    }

    private static void renderComboSlashes(Matrix4f matrix, Effect effect,
            float age, float strength) {
        Vec3 forward = flatDirection(effect.start, effect.end, effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        renderComboSlash(matrix, effect.start, forward, side, age,
                2.0F, 6.0F, -38.0D, 3.15D, 2.25D, strength, 0.76F);
        renderComboSlash(matrix, effect.start, forward, side, age,
                8.0F, 6.0F, 34.0D, 3.45D, 2.45D, strength, 0.80F);
        renderComboSlash(matrix, effect.start, forward, side, age,
                15.0F, 7.0F, -8.0D, 4.05D, 2.75D, strength, 1.0F);
    }

    private static void renderComboSlash(Matrix4f matrix, Vec3 origin,
            Vec3 forward, Vec3 side, float age, float peak, float fadeTicks,
            double rollDegrees, double halfWidth, double halfHeight,
            float strength, float alphaScale) {
        float alpha = peakedAlpha(age, peak, fadeTicks);
        if (alpha <= 0.0F) return;
        double roll = Math.toRadians(rollDegrees);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisU = side.scale(Math.cos(roll) * halfWidth * strength)
                .add(up.scale(Math.sin(roll) * halfWidth * strength));
        Vec3 axisV = up.scale(Math.cos(roll) * halfHeight * strength)
                .subtract(side.scale(Math.sin(roll) * halfHeight * strength));
        Vec3 center = origin.add(forward.scale(halfWidth * 0.68D))
                .add(0.0D, 0.65D, 0.0D);
        texturedSlash(matrix, center, axisU, axisV, alpha * alphaScale,
                255, 245, 242, false);
    }

    private static void renderIaidoSlash(Matrix4f matrix, Vec3 start, Vec3 end,
            float alpha, float strength, boolean softer) {
        if (alpha <= 0.0F) return;
        Vec3 direction = flatDirection(start, end, 0.0F);
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        double length = Math.max(2.0D, start.distanceTo(end));
        Vec3 center = start.lerp(end, 0.5D);
        // A slight yaw offset keeps the world-space sheet readable when the
        // target looks straight down the attack path, without turning it into
        // a camera-facing billboard.
        Vec3 alongDirection = direction.scale(0.966D).add(side.scale(0.259D));
        Vec3 along = alongDirection.scale(length * 0.62D * strength);
        texturedSlash(matrix, center, along,
                new Vec3(0.0D, 0.62D * strength, 0.0D),
                alpha * (softer ? 0.72F : 0.94F), 255, 250, 246, false);
        texturedSlash(matrix, center.add(0.0D, 0.04D, 0.0D), along,
                side.scale(0.34D * strength), alpha * 0.42F,
                255, 190, 200, true);
    }

    private static void renderHeavySlash(Matrix4f matrix, Effect effect,
            float age, float strength) {
        float alpha = peakedAlpha(age, 8.0F, 8.0F);
        if (alpha <= 0.0F) return;
        Vec3 forward = flatDirection(effect.start, effect.end, effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = effect.start.add(forward.scale(3.15D)).add(0.0D, 1.55D, 0.0D);
        Vec3 cleavePlane = forward.scale(0.91D).add(side.scale(0.42D));
        texturedSlash(matrix, center, cleavePlane.scale(4.25D * strength),
                new Vec3(0.0D, 4.35D * strength, 0.0D), alpha,
                255, 235, 230, false);
    }

    private static void renderFlashSlashes(Matrix4f matrix, Effect effect,
            float age, float strength) {
        float closeAlpha = peakedAlpha(age, 3.0F, 4.0F);
        if (closeAlpha > 0.0F) {
            Vec3 forward = yawDirection(effect.yaw);
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            texturedSlash(matrix, effect.start.add(0.0D, 0.95D, 0.0D),
                    right.scale(3.75D * strength),
                    forward.scale(2.0D * strength).add(0.0D, 0.55D, 0.0D),
                    closeAlpha * 0.78F, 255, 220, 225, false);
        }
        float farAlpha = peakedAlpha(age, 8.0F, 7.0F);
        if (farAlpha > 0.0F) {
            Vec3 forward = yawDirection(effect.yaw);
            Vec3 lineEnd = effect.end.add(forward.scale(30.0D));
            renderIaidoSlash(matrix, effect.end, lineEnd, farAlpha,
                    strength, true);
        }
    }

    private static void renderSakuraSlashes(Matrix4f matrix, Effect effect,
            float age, float strength) {
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        renderSakuraSlash(matrix, effect.start, forward, side, age,
                5.0F, 7.0F, 32.0D, 3.9D, strength, 0.78F);
        renderSakuraSlash(matrix, effect.start, forward, side, age,
                17.0F, 8.0F, -34.0D, 5.25D, strength, 0.92F);
    }

    private static void renderSakuraSlash(Matrix4f matrix, Vec3 origin,
            Vec3 forward, Vec3 side, float age, float peak, float fadeTicks,
            double rollDegrees, double size, float strength, float alphaScale) {
        float alpha = peakedAlpha(age, peak, fadeTicks);
        if (alpha <= 0.0F) return;
        double roll = Math.toRadians(rollDegrees);
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 axisU = side.scale(Math.cos(roll) * size * strength)
                .add(up.scale(Math.sin(roll) * size * strength));
        Vec3 axisV = up.scale(Math.cos(roll) * size * strength)
                .subtract(side.scale(Math.sin(roll) * size * strength));
        texturedSlash(matrix, origin.add(0.0D, 1.1D, 0.0D)
                        .add(forward.scale(0.18D)), axisU, axisV,
                alpha * alphaScale, 255, 190, 205, peak > 10.0F);
    }

    private static float peakedAlpha(float age, float peak, float fadeTicks) {
        if (age < 0.0F || age >= peak + fadeTicks) return 0.0F;
        if (age <= peak) {
            float rise = peak <= 0.0F ? 1.0F : Mth.clamp(age / peak, 0.0F, 1.0F);
            return 0.28F + rise * 0.72F;
        }
        float fade = 1.0F - (age - peak) / fadeTicks;
        return fade * fade;
    }

    private static void texturedSlash(Matrix4f matrix, Vec3 center,
            Vec3 axisU, Vec3 axisV, float alpha,
            int red, int green, int blue, boolean flipU) {
        if (alpha <= 0.0F) return;
        Vec3 topLeft = center.subtract(axisU).add(axisV);
        Vec3 bottomLeft = center.subtract(axisU).subtract(axisV);
        Vec3 bottomRight = center.add(axisU).subtract(axisV);
        Vec3 topRight = center.add(axisU).add(axisV);
        float u0 = flipU ? 1.0F : 0.0F;
        float u1 = flipU ? 0.0F : 1.0F;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textureVertex(buffer, matrix, topLeft, u0, 0.0F, red, green, blue, alpha);
        textureVertex(buffer, matrix, bottomLeft, u0, 1.0F, red, green, blue, alpha);
        textureVertex(buffer, matrix, bottomRight, u1, 1.0F, red, green, blue, alpha);
        textureVertex(buffer, matrix, topRight, u1, 0.0F, red, green, blue, alpha);
        Tesselator.getInstance().end();
    }

    private static void textureVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 point, float u, float v, int red, int green, int blue, float alpha) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .uv(u, v).color(red, green, blue,
                        Mth.clamp(Math.round(alpha * 255.0F), 0, 255)).endVertex();
    }

    private static float timedFade(int age, int trigger, int life) {
        if (age < trigger || age >= trigger + life) return 0.0F;
        float local = (age - trigger) / (float) Math.max(1, life);
        return (1.0F - local) * (1.0F - local);
    }

    private static Vec3 flatDirection(Vec3 start, Vec3 end, float yaw) {
        Vec3 direction = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 0.0001D ? yawDirection(yaw) : direction.normalize();
    }

    private static Vec3 yawDirection(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
    }

    private static void arcHorizontal(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, double radius, double startAngle, double endAngle,
            double width, int color, int count) {
        int steps = Math.max(2, count);
        Vec3 previous = center.add(Math.cos(startAngle) * radius, 0.0D,
                Math.sin(startAngle) * radius);
        for (int i = 1; i <= steps; i++) {
            double angle = Mth.lerp(i / (double) steps, startAngle, endAngle);
            Vec3 next = center.add(Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            line(buffer, matrix, camera, previous, next, width, color);
            previous = next;
        }
    }

    private static void arcVertical(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, Vec3 forward, Vec3 side, double radius,
            double startAngle, double endAngle, double width, int color, int count) {
        int steps = Math.max(2, count);
        Vec3 previous = center.add(side.scale(Math.sin(startAngle) * radius))
                .add(0.0D, Math.cos(startAngle) * radius, 0.0D)
                .add(forward.scale(0.12D * radius));
        for (int i = 1; i <= steps; i++) {
            double angle = Mth.lerp(i / (double) steps, startAngle, endAngle);
            Vec3 next = center.add(side.scale(Math.sin(angle) * radius))
                    .add(0.0D, Math.cos(angle) * radius, 0.0D)
                    .add(forward.scale(0.12D * radius));
            line(buffer, matrix, camera, previous, next, width, color);
            previous = next;
        }
    }

    private static void bezier(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 start, Vec3 control, Vec3 end, float reveal,
            double width, int color, int count) {
        int visible = Math.max(1, Mth.ceil(count * reveal));
        Vec3 previous = start;
        for (int i = 1; i <= visible; i++) {
            double t = i / (double) count;
            double inverse = 1.0D - t;
            Vec3 next = start.scale(inverse * inverse)
                    .add(control.scale(2.0D * inverse * t))
                    .add(end.scale(t * t));
            line(buffer, matrix, camera, previous, next, width, color);
            previous = next;
        }
    }

    private static void drawTorii(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 base, float yaw, double scale, int color) {
        double angle = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 leftPost = base.subtract(right.scale(1.35D * scale));
        Vec3 rightPost = base.add(right.scale(1.35D * scale));
        line(buffer, matrix, camera, leftPost, leftPost.add(up.scale(3.15D * scale)),
                0.13D * scale, color);
        line(buffer, matrix, camera, rightPost, rightPost.add(up.scale(3.15D * scale)),
                0.13D * scale, color);
        Vec3 cross = base.add(up.scale(2.65D * scale));
        line(buffer, matrix, camera, cross.subtract(right.scale(1.75D * scale)),
                cross.add(right.scale(1.75D * scale)), 0.13D * scale, color);
        Vec3 top = base.add(up.scale(3.25D * scale));
        line(buffer, matrix, camera, top.subtract(right.scale(2.05D * scale)),
                top.add(right.scale(2.05D * scale)), 0.17D * scale, color);
    }

    private static void drawRibbon(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            List<Vec3> history, double width, int baseColor, int quality) {
        if (history.size() < 2) return;
        int stride = quality == 0 ? 2 : 1;
        int size = history.size();
        for (int i = stride; i < size; i += stride) {
            Vec3 a = history.get(i - stride);
            Vec3 b = history.get(i);
            float fade = i / (float) size;
            int color = withAlpha(baseColor,
                    ((baseColor >>> 24) & 0xFF) / 255.0F * fade * fade);
            line(buffer, matrix, camera, a, b, width * (0.35D + fade * 0.65D), color);
        }
    }

    private static int segments(int base, int quality) {
        return quality <= 0 ? Math.max(12, base / 2) : quality == 1 ? base : base + base / 3;
    }

    private static Vec3 deterministicDirection(int seed, int index) {
        double angle = (seed * 0.000173D + index * 2.399963D) % (Math.PI * 2.0D);
        double y = -0.38D + ((seed >>> (index % 16)) & 7) / 7.0D * 1.18D;
        return new Vec3(Math.cos(angle), y, Math.sin(angle)).normalize();
    }

    private static Vec3 horizontalSide(Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.0001D) return new Vec3(1.0D, 0.0D, 0.0D);
        return new Vec3(-direction.z, 0.0D, direction.x).normalize();
    }

    private static Vec3 cameraRight(Vec3 center, Vec3 camera) {
        Vec3 view = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        if (view.lengthSqr() < 0.0001D) return new Vec3(1.0D, 0.0D, 0.0D);
        return new Vec3(view.z, 0.0D, -view.x).normalize();
    }

    private static Vec3 planeVector(Vec3 right, double horizontal, double vertical) {
        return new Vec3(right.x * horizontal, vertical, right.z * horizontal);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 start, Vec3 end, double halfWidth, int color) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 0.000001D) return;
        Vec3 midpoint = start.add(end).scale(0.5D);
        Vec3 side = direction.cross(camera.subtract(midpoint));
        if (side.lengthSqr() < 0.0001D) {
            side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 0.0001D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize().scale(halfWidth);
        quad(buffer, matrix, start.subtract(side), end.subtract(side),
                end.add(side), start.add(side), color);
    }

    private static void ringVertical(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            Vec3 right, double radius, double width, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0D / segments;
            double a1 = (i + 1) * Math.PI * 2.0D / segments;
            Vec3 outer0 = center.add(planeVector(right,
                    Math.cos(a0) * (radius + width), Math.sin(a0) * (radius + width)));
            Vec3 outer1 = center.add(planeVector(right,
                    Math.cos(a1) * (radius + width), Math.sin(a1) * (radius + width)));
            Vec3 inner1 = center.add(planeVector(right,
                    Math.cos(a1) * Math.max(0.0D, radius - width),
                    Math.sin(a1) * Math.max(0.0D, radius - width)));
            Vec3 inner0 = center.add(planeVector(right,
                    Math.cos(a0) * Math.max(0.0D, radius - width),
                    Math.sin(a0) * Math.max(0.0D, radius - width)));
            quad(buffer, matrix, outer0, outer1, inner1, inner0, color);
        }
    }

    private static void ringHorizontal(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            double radius, double width, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0D / segments;
            double a1 = (i + 1) * Math.PI * 2.0D / segments;
            Vec3 outer0 = center.add(Math.cos(a0) * (radius + width), 0.0D,
                    Math.sin(a0) * (radius + width));
            Vec3 outer1 = center.add(Math.cos(a1) * (radius + width), 0.0D,
                    Math.sin(a1) * (radius + width));
            Vec3 inner1 = center.add(Math.cos(a1) * Math.max(0.0D, radius - width), 0.0D,
                    Math.sin(a1) * Math.max(0.0D, radius - width));
            Vec3 inner0 = center.add(Math.cos(a0) * Math.max(0.0D, radius - width), 0.0D,
                    Math.sin(a0) * Math.max(0.0D, radius - width));
            quad(buffer, matrix, outer0, outer1, inner1, inner0, color);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(buffer, matrix, a, color);
        vertex(buffer, matrix, b, color);
        vertex(buffer, matrix, c, color);
        vertex(buffer, matrix, d, color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, int color) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    private static int color(float red, float green, float blue, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(red * 255.0F), 0, 255);
        int g = Mth.clamp(Math.round(green * 255.0F), 0, 255);
        int b = Mth.clamp(Math.round(blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24)
                | (color & 0x00FFFFFF);
    }

    private static int shadeColor(int color, float brightness) {
        float shade = Mth.clamp(brightness, 0.0F, 1.0F);
        int red = Mth.clamp(Math.round(((color >> 16) & 0xFF) * shade), 0, 255);
        int green = Mth.clamp(Math.round(((color >> 8) & 0xFF) * shade), 0, 255);
        int blue = Mth.clamp(Math.round((color & 0xFF) * shade), 0, 255);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static Vec3 entityCenter(Entity entity) {
        return entity instanceof LivingEntity living
                ? living.position().add(0.0D, living.getBbHeight() * 0.55D, 0.0D)
                : entity.position();
    }

    private static final class Effect {
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
