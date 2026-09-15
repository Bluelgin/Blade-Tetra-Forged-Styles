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
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
import java.util.Iterator;
import java.util.List;

/**
 * Player-side Boundary Forging presentation.
 *
 * <p>The graduation forging deliberately does not copy Mikage's full Boundary Flash.
 * The player borrows the gate: four boundary stakes lock one opponent, a projected
 * gate descends, one thin adjudication cut lands, and the remaining boundary embers
 * keep suppressing the target. Gameplay timing and damage stay server-owned in
 * {@code BoundaryForging}; this class only reconstructs the visual language.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BoundaryForgingVfxClient {
    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static ClientLevel activeLevel;

    public static void spawn(BladeTechniqueVfxPacket packet) {
        if (!ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != activeLevel) {
            EFFECTS.clear();
            activeLevel = minecraft.level;
        }
        if (minecraft.player != null) {
            double maximum = ClientVisualConfig.BLADE_COMBAT_VFX_DISTANCE.get();
            if (minecraft.player.distanceToSqr(packet.startX(), packet.startY(), packet.startZ())
                    > maximum * maximum) {
                return;
            }
        }
        EFFECTS.add(new Effect(packet));
        int maximumEffects = Math.max(1, ClientVisualConfig.BLADE_COMBAT_VFX_MAX_EFFECTS.get());
        while (EFFECTS.size() > maximumEffects) EFFECTS.remove(0);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.isPaused()) return;
        ClientLevel level = minecraft.level;
        if (level != activeLevel) {
            EFFECTS.clear();
            activeLevel = level;
        }
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            effect.tick(level);
            if (effect.age >= effect.duration) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || EFFECTS.isEmpty()
                || !ClientVisualConfig.ENABLE_BLADE_COMBAT_VFX.get()) {
            return;
        }
        float global = ClientVisualConfig.BLADE_COMBAT_VFX_INTENSITY.get().floatValue();
        if (global <= 0.001F) return;
        Camera camera = event.getCamera();
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.getPosition().x, -camera.getPosition().y,
                -camera.getPosition().z);
        Matrix4f matrix = poses.last().pose();
        int quality = ClientVisualConfig.BLADE_COMBAT_VFX_QUALITY.get();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Effect effect : EFFECTS) {
            drawSolid(buffer, matrix, camera.getPosition(), effect, global, quality);
        }
        Tesselator.getInstance().end();

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Effect effect : EFFECTS) {
            drawGlow(buffer, matrix, camera.getPosition(), effect, global, quality);
        }
        Tesselator.getInstance().end();

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poses.popPose();
    }

    private static void drawSolid(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Effect effect, float global, int quality) {
        float strength = Mth.clamp(effect.intensity * effect.targetScale * global,
                0.0F, 2.35F);
        if (strength <= 0.001F) return;
        if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_STRIKE) {
            drawBoundaryJudgementSolid(buffer, matrix, camera, effect, strength, quality);
        } else if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME) {
            drawBoundaryEmbersSolid(buffer, matrix, camera, effect, strength, quality);
        }
    }

    private static void drawGlow(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Effect effect, float global, int quality) {
        float strength = Mth.clamp(effect.intensity * effect.targetScale * global,
                0.0F, 2.35F);
        if (strength <= 0.001F) return;
        if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_STRIKE) {
            drawBoundaryJudgementGlow(buffer, matrix, camera, effect, strength, quality);
        } else if (effect.type == BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME) {
            drawBoundaryEmbersGlow(buffer, matrix, camera, effect, strength, quality);
        }
    }

    private static void drawBoundaryJudgementSolid(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        Vec3 center = effect.center;
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        float fade = effect.age <= 66 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 16.0F, 0.0F, 1.0F);
        int abyss = color(0.055F, 0.004F, 0.010F, 0.94F * fade);
        int crimson = color(0.34F, 0.008F, 0.025F, 0.90F * fade);
        int blood = color(0.66F, 0.018F, 0.050F, 0.76F * fade);

        float lock = smooth(Mth.clamp(effect.age / 13.0F, 0.0F, 1.0F));
        double sealRadius = (1.38D - lock * 0.18D) * strength;
        drawDiamondFrame(buffer, matrix, camera, center.add(0.0D, 0.055D, 0.0D),
                forward, right, sealRadius, 0.060D * strength, abyss);
        drawDiamondFrame(buffer, matrix, camera, center.add(0.0D, 0.068D, 0.0D),
                forward, right, sealRadius * 0.84D, 0.030D * strength, crimson);

        for (int i = 0; i < 4; i++) {
            int unlockAge = 1 + i * 3;
            if (effect.age < unlockAge) continue;
            float stakeReveal = smooth(Mth.clamp((effect.age - unlockAge) / 5.0F,
                    0.0F, 1.0F));
            double angle = Math.toRadians(effect.yaw) + Math.PI * 0.25D
                    + i * Math.PI * 0.5D;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            Vec3 socket = center.add(radial.scale(sealRadius * 1.06D));
            drawStakeSocket(buffer, matrix, socket, radial, tangent,
                    strength * stakeReveal, abyss, crimson);
        }

        float gateReveal = smooth(Mth.clamp((effect.age - 5.0F) / 10.0F,
                0.0F, 1.0F));
        if (gateReveal > 0.001F && effect.age <= 58) {
            float descend = smooth(Mth.clamp((effect.age - 11.0F) / 20.0F,
                    0.0F, 1.0F));
            double y = Mth.lerp(descend,
                    center.y + 6.4D * strength,
                    center.y - 0.18D);
            float gateFade = effect.age <= 44 ? 1.0F
                    : Mth.clamp((58.0F - effect.age) / 14.0F, 0.0F, 1.0F);
            drawBorrowedGate(buffer, matrix,
                    new Vec3(center.x, y, center.z), right, forward,
                    strength * (0.78D + gateReveal * 0.22D),
                    withAlpha(abyss, gateReveal * gateFade),
                    withAlpha(crimson, gateReveal * gateFade),
                    withAlpha(blood, gateReveal * gateFade));
        }

        if (effect.age >= 29 && effect.age <= 43) {
            float cutFade = timedFade(effect.age, 29, 14);
            Vec3 slashCenter = center.add(0.0D, 1.15D * strength, 0.0D);
            orientedBox(buffer, matrix, slashCenter,
                    right, new Vec3(0.0D, 1.0D, 0.0D), forward,
                    0.095D * strength, 1.70D * strength, 0.105D * strength,
                    withAlpha(abyss, cutFade));
            if (effect.age >= 31) {
                float fracture = timedFade(effect.age, 31, 15);
                drawFractures(buffer, matrix, camera, center, forward, right,
                        strength, withAlpha(crimson, fracture), quality);
            }
        }
    }

    private static void drawBoundaryJudgementGlow(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        Vec3 center = effect.center;
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        float fade = effect.age <= 66 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 16.0F, 0.0F, 1.0F);
        int red = color(0.98F, 0.035F, 0.095F, 0.74F * fade);
        int pale = color(1.0F, 0.82F, 0.73F, 0.92F * fade);
        int gold = color(1.0F, 0.50F, 0.10F, 0.72F * fade);

        float lock = smooth(Mth.clamp(effect.age / 13.0F, 0.0F, 1.0F));
        double sealRadius = (1.38D - lock * 0.18D) * strength;
        for (int i = 0; i < 4; i++) {
            int unlockAge = 1 + i * 3;
            if (effect.age < unlockAge) continue;
            float reveal = smooth(Mth.clamp((effect.age - unlockAge) / 5.0F,
                    0.0F, 1.0F));
            double angle = Math.toRadians(effect.yaw) + Math.PI * 0.25D
                    + i * Math.PI * 0.5D;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 socket = center.add(radial.scale(sealRadius * 1.06D));
            Vec3 overhead = center.add(radial.scale(2.85D * strength))
                    .add(0.0D, (4.5D + i * 0.24D) * strength, 0.0D);
            Vec3 lockPoint = center.add(0.0D, 1.05D * strength, 0.0D);
            line(buffer, matrix, camera, overhead, socket.add(0.0D, 0.20D, 0.0D),
                    0.020D * strength, withAlpha(i == 3 ? pale : red, reveal * 0.68F));
            line(buffer, matrix, camera, socket.add(0.0D, 0.12D, 0.0D), lockPoint,
                    0.014D * strength, withAlpha(gold, reveal * 0.52F));
        }

        if (effect.age >= 27 && effect.age <= 40) {
            float charge = Mth.clamp((effect.age - 27.0F) / 4.0F, 0.0F, 1.0F);
            float fadeCut = timedFade(effect.age, 31, 10);
            Vec3 top = center.add(0.0D, 4.3D * strength, 0.0D);
            Vec3 bottom = center.add(0.0D, -0.18D, 0.0D);
            line(buffer, matrix, camera, top, bottom,
                    (0.018D + charge * 0.026D) * strength,
                    withAlpha(pale, Math.max(charge * 0.72F, fadeCut)));
            if (effect.age >= 31) {
                line(buffer, matrix, camera,
                        center.subtract(right.scale(0.62D * strength)).add(0.0D, 1.0D, 0.0D),
                        center.add(right.scale(0.62D * strength)).add(0.0D, 1.0D, 0.0D),
                        0.016D * strength, withAlpha(red, fadeCut * 0.76F));
            }
        }
    }

    private static void drawBoundaryEmbersSolid(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        float rise = smooth(Mth.clamp(effect.age / 8.0F, 0.0F, 1.0F));
        float fade = effect.age < effect.duration - 18 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 18.0F, 0.0F, 1.0F);
        float envelope = rise * fade;
        if (envelope <= 0.001F) return;

        Vec3 center = effect.center.add(0.0D, 0.035D, 0.0D);
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int abyss = color(0.06F, 0.003F, 0.009F, 0.92F * envelope);
        int crimson = color(0.42F, 0.008F, 0.024F, 0.86F * envelope);
        double radius = 0.76D * strength;
        drawDiamondFrame(buffer, matrix, camera, center, forward, right,
                radius, 0.055D * strength, abyss);

        int tongues = quality <= 0 ? 4 : quality == 1 ? 6 : 8;
        for (int i = 0; i < tongues; i++) {
            double angle = i * Math.PI * 2.0D / tongues + effect.seed * 0.00023D;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);
            Vec3 base = center.add(radial.scale(radius
                    * (0.82D + 0.15D * Math.sin(i * 2.7D + effect.seed))));
            double wave = 0.5D + 0.5D * Math.sin(effect.age * 0.19D + i * 1.77D);
            double height = (0.52D + wave * 0.85D) * strength * envelope;
            Vec3 p1 = base.add(tangent.scale(Math.sin(effect.age * 0.13D + i) * 0.10D))
                    .add(0.0D, height * 0.45D, 0.0D);
            Vec3 tip = base.add(tangent.scale(Math.sin(effect.age * 0.18D + i) * 0.17D))
                    .add(0.0D, height, 0.0D);
            drawTaperedShard(buffer, matrix, new Vec3[] {base, p1, tip},
                    radial, tangent,
                    new double[] {0.18D * strength, 0.11D * strength, 0.012D},
                    abyss, crimson);
        }

        if (effect.age % 24 < 12) {
            float ghost = (1.0F - effect.age % 24 / 12.0F) * envelope * 0.22F;
            Vec3 gateBase = center.subtract(forward.scale(0.58D * strength));
            drawBorrowedGate(buffer, matrix, gateBase, right, forward,
                    0.42D * strength, withAlpha(abyss, ghost),
                    withAlpha(crimson, ghost), withAlpha(crimson, ghost * 0.8F));
        }
    }

    private static void drawBoundaryEmbersGlow(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        float rise = smooth(Mth.clamp(effect.age / 8.0F, 0.0F, 1.0F));
        float fade = effect.age < effect.duration - 18 ? 1.0F
                : Mth.clamp((effect.duration - effect.age) / 18.0F, 0.0F, 1.0F);
        float envelope = rise * fade;
        if (envelope <= 0.001F) return;
        Vec3 center = effect.center.add(0.0D, 0.05D, 0.0D);
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int ember = color(1.0F, 0.075F, 0.04F, 0.64F * envelope);
        int pale = color(1.0F, 0.54F, 0.16F, 0.54F * envelope);
        double radius = 0.76D * strength;
        drawDiamondFrame(buffer, matrix, camera, center, forward, right,
                radius * 0.82D, 0.018D * strength, ember);
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(effect.yaw) + Math.PI * 0.25D
                    + i * Math.PI * 0.5D;
            Vec3 corner = center.add(Math.cos(angle) * radius, 0.0D,
                    Math.sin(angle) * radius);
            float pulse = 0.40F + 0.60F
                    * (0.5F + 0.5F * Mth.sin(effect.age * 0.31F + i));
            line(buffer, matrix, camera, corner,
                    corner.add(0.0D, (0.30D + pulse * 0.48D) * strength, 0.0D),
                    0.018D * strength, i == 0 ? pale : ember);
        }
    }

    private static void drawStakeSocket(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, Vec3 radial, Vec3 tangent, float strength,
            int abyss, int crimson) {
        if (strength <= 0.001F) return;
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        orientedBox(buffer, matrix, center.add(0.0D, 0.055D, 0.0D),
                tangent, up, radial,
                0.30D * strength, 0.055D * strength, 0.18D * strength, abyss);
        orientedBox(buffer, matrix, center.add(0.0D, 0.095D, 0.0D),
                tangent, up, radial,
                0.18D * strength, 0.035D * strength, 0.11D * strength, crimson);
        Vec3 post = center.add(0.0D, 0.28D * strength, 0.0D);
        orientedBox(buffer, matrix, post, tangent, up, radial,
                0.055D * strength, 0.24D * strength, 0.055D * strength, abyss);
    }

    private static void drawBorrowedGate(BufferBuilder buffer, Matrix4f matrix,
            Vec3 base, Vec3 right, Vec3 forward, double scale,
            int abyss, int crimson, int edge) {
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 left = base.subtract(right.scale(1.18D * scale));
        Vec3 rightBase = base.add(right.scale(1.18D * scale));
        double postHeight = 2.85D * scale;
        orientedBox(buffer, matrix, left.add(up.scale(postHeight * 0.5D)),
                right, up, forward, 0.15D * scale, postHeight * 0.5D,
                0.14D * scale, abyss);
        orientedBox(buffer, matrix, rightBase.add(up.scale(postHeight * 0.5D)),
                right, up, forward, 0.15D * scale, postHeight * 0.5D,
                0.14D * scale, abyss);
        orientedBox(buffer, matrix, left.add(up.scale(postHeight * 0.53D)),
                right, up, forward, 0.105D * scale, postHeight * 0.46D,
                0.105D * scale, crimson);
        orientedBox(buffer, matrix, rightBase.add(up.scale(postHeight * 0.53D)),
                right, up, forward, 0.105D * scale, postHeight * 0.46D,
                0.105D * scale, crimson);

        Vec3 lower = base.add(up.scale(2.10D * scale));
        orientedBox(buffer, matrix, lower, right, up, forward,
                1.52D * scale, 0.095D * scale, 0.18D * scale, abyss);
        orientedBox(buffer, matrix, lower.add(forward.scale(0.022D * scale)),
                right, up, forward,
                1.39D * scale, 0.055D * scale, 0.13D * scale, crimson);

        Vec3 crown = base.add(up.scale(2.92D * scale));
        orientedBox(buffer, matrix, crown, right, up, forward,
                1.88D * scale, 0.14D * scale, 0.22D * scale, abyss);
        orientedBox(buffer, matrix, crown.add(forward.scale(0.028D * scale)),
                right, up, forward,
                1.72D * scale, 0.075D * scale, 0.16D * scale, crimson);
        Vec3 crownLeft = crown.subtract(right.scale(1.92D * scale))
                .add(up.scale(0.12D * scale));
        Vec3 crownRight = crown.add(right.scale(1.92D * scale))
                .add(up.scale(0.12D * scale));
        orientedBox(buffer, matrix, crownLeft, right, up, forward,
                0.27D * scale, 0.085D * scale, 0.16D * scale, edge);
        orientedBox(buffer, matrix, crownRight, right, up, forward,
                0.27D * scale, 0.085D * scale, 0.16D * scale, edge);

        Vec3 plaque = base.add(up.scale(2.48D * scale))
                .add(forward.scale(0.18D * scale));
        orientedBox(buffer, matrix, plaque, right, up, forward,
                0.25D * scale, 0.31D * scale, 0.07D * scale, edge);
    }

    private static void drawFractures(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, Vec3 forward, Vec3 right, float strength, int tint, int quality) {
        int rays = quality <= 0 ? 4 : quality == 1 ? 6 : 8;
        for (int i = 0; i < rays; i++) {
            double angle = i * Math.PI * 2.0D / rays + Math.PI * 0.125D;
            Vec3 direction = forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
            double length = (0.72D + (i % 3) * 0.38D) * strength;
            Vec3 start = center.add(0.0D, 0.07D, 0.0D);
            Vec3 mid = start.add(direction.scale(length * 0.55D))
                    .add(right.scale(((i & 1) == 0 ? 1.0D : -1.0D) * 0.12D));
            Vec3 end = start.add(direction.scale(length));
            line(buffer, matrix, camera, start, mid, 0.025D * strength, tint);
            line(buffer, matrix, camera, mid, end, 0.016D * strength, tint);
        }
    }

    private static void drawDiamondFrame(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 center, Vec3 forward, Vec3 right, double radius, double width, int tint) {
        Vec3 a = center.add(forward.scale(radius));
        Vec3 b = center.add(right.scale(radius));
        Vec3 c = center.subtract(forward.scale(radius));
        Vec3 d = center.subtract(right.scale(radius));
        line(buffer, matrix, camera, a, b, width, tint);
        line(buffer, matrix, camera, b, c, width, tint);
        line(buffer, matrix, camera, c, d, width, tint);
        line(buffer, matrix, camera, d, a, width, tint);
    }

    private static void drawTaperedShard(BufferBuilder buffer, Matrix4f matrix,
            Vec3[] points, Vec3 axisA, Vec3 axisB, double[] radii,
            int outer, int inner) {
        if (points.length < 2 || points.length != radii.length) return;
        Vec3 a = axisA.normalize();
        Vec3 b = axisB.normalize();
        int faces = 5;
        for (int segment = 0; segment < points.length - 1; segment++) {
            for (int face = 0; face < faces; face++) {
                double angle0 = face * Math.PI * 2.0D / faces;
                double angle1 = (face + 1) * Math.PI * 2.0D / faces;
                Vec3 a0 = a.scale(Math.cos(angle0) * radii[segment])
                        .add(b.scale(Math.sin(angle0) * radii[segment] * 0.7D));
                Vec3 a1 = a.scale(Math.cos(angle1) * radii[segment])
                        .add(b.scale(Math.sin(angle1) * radii[segment] * 0.7D));
                Vec3 b1 = a.scale(Math.cos(angle1) * radii[segment + 1])
                        .add(b.scale(Math.sin(angle1) * radii[segment + 1] * 0.7D));
                Vec3 b0 = a.scale(Math.cos(angle0) * radii[segment + 1])
                        .add(b.scale(Math.sin(angle0) * radii[segment + 1] * 0.7D));
                quad(buffer, matrix,
                        points[segment].add(a0), points[segment].add(a1),
                        points[segment + 1].add(b1), points[segment + 1].add(b0),
                        face == 0 || face == 3 ? inner : outer);
            }
        }
    }

    private static void orientedBox(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, Vec3 axisX, Vec3 axisY, Vec3 axisZ,
            double halfX, double halfY, double halfZ, int tint) {
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
        quad(buffer, matrix, nnn, pnn, ppn, npn, tint);
        quad(buffer, matrix, pnp, nnp, npp, ppp, tint);
        quad(buffer, matrix, nnp, nnn, npn, npp, tint);
        quad(buffer, matrix, pnn, pnp, ppp, ppn, tint);
        quad(buffer, matrix, npn, ppn, ppp, npp, tint);
        quad(buffer, matrix, nnp, pnp, pnn, nnn, tint);
    }

    private static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 start, Vec3 end, double halfWidth, int tint) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 0.000001D) return;
        Vec3 midpoint = start.add(end).scale(0.5D);
        Vec3 side = direction.cross(camera.subtract(midpoint));
        if (side.lengthSqr() < 0.0001D) side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 0.0001D) side = new Vec3(1.0D, 0.0D, 0.0D);
        side = side.normalize().scale(halfWidth);
        quad(buffer, matrix, start.subtract(side), end.subtract(side),
                end.add(side), start.add(side), tint);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int tint) {
        vertex(buffer, matrix, a, tint);
        vertex(buffer, matrix, b, tint);
        vertex(buffer, matrix, c, tint);
        vertex(buffer, matrix, d, tint);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, int tint) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color((tint >> 16) & 0xFF, (tint >> 8) & 0xFF,
                        tint & 0xFF, (tint >>> 24) & 0xFF)
                .endVertex();
    }

    private static Vec3 yawDirection(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float timedFade(int age, int trigger, int life) {
        if (age < trigger || age >= trigger + life) return 0.0F;
        float local = (age - trigger) / (float) Math.max(1, life);
        return (1.0F - local) * (1.0F - local);
    }

    private static int color(float red, float green, float blue, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(red * 255.0F), 0, 255);
        int g = Mth.clamp(Math.round(green * 255.0F), 0, 255);
        int b = Mth.clamp(Math.round(blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int tint, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24)
                | (tint & 0x00FFFFFF);
    }

    private static final class Effect {
        final int type;
        final float yaw;
        final float intensity;
        final int targetEntityId;
        final int duration;
        final int seed;
        Vec3 center;
        float targetScale = 1.0F;
        int age;

        Effect(BladeTechniqueVfxPacket packet) {
            type = packet.type();
            yaw = packet.yaw();
            intensity = Math.max(0.0F, packet.intensity());
            targetEntityId = packet.targetEntityId();
            duration = Math.max(1, packet.duration());
            seed = packet.seed();
            center = new Vec3(packet.endX(), packet.endY(), packet.endZ());
        }

        void tick(ClientLevel level) {
            age++;
            if (level == null || targetEntityId < 0) return;
            Entity target = level.getEntity(targetEntityId);
            if (target == null) return;
            if (type == BladeTechniqueVfxPacket.BOUNDARY_STRIKE) {
                targetScale = Mth.clamp(Math.max(target.getBbWidth() / 1.2F,
                        target.getBbHeight() / 3.0F), 0.9F, 2.2F);
                if (age <= 31) center = target.position();
            } else if (type == BladeTechniqueVfxPacket.BOUNDARY_SUPPRESSION_FLAME) {
                center = target.position();
            }
        }
    }

    private BoundaryForgingVfxClient() {
    }
}
