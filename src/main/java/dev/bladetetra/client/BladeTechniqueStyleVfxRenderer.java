package dev.bladetetra.client;

import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;
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
import static dev.bladetetra.client.BladeTechniqueVfxClient.*;
import static dev.bladetetra.client.BladeTechniqueVfxGeometry.*;

/** Player style, Iaido, combo and Sakura render families. */
final class BladeTechniqueStyleVfxRenderer {
    static void drawMeleeCircle(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        // The textured crescent is the visible attack. A faint ground ring only
        // marks the real five-block circular hit box at the strike frame.
        float fade = timedFade(effect.age, 3, 5);
        if (fade <= 0.0F) return;
        int red = color(0.75F, 0.01F, 0.035F, 0.18F * fade);
        ringHorizontal(buffer, matrix, effect.start.add(0.0D, 0.055D, 0.0D),
                5.0D, 0.025D * strength, red, segments(34, quality));
    }

    static void drawMeleeCombo(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        // Combo timing is carried by three textured crescents in the texture pass.
    }

    static void drawStepIaido(BufferBuilder buffer, Matrix4f matrix,
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

    static void drawHeavyCleave(BufferBuilder buffer, Matrix4f matrix,
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

    static void drawFlashCounter(BufferBuilder buffer, Matrix4f matrix,
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

    static void drawSakuraEnd(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Effect effect, float strength, int quality) {
        drawSakuraBeat(buffer, matrix, camera, effect, 3, 9,
                3.8D, strength, quality, false);
        drawSakuraBeat(buffer, matrix, camera, effect, 15, 10,
                5.2D, strength * 1.08F, quality, true);
    }

    static void drawSakuraBeat(BufferBuilder buffer, Matrix4f matrix,
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

    static void renderCircleSlashes(Matrix4f matrix, Effect effect,
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

    static void renderComboSlashes(Matrix4f matrix, Effect effect,
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

    static void renderComboSlash(Matrix4f matrix, Vec3 origin,
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

    static void renderIaidoSlash(Matrix4f matrix, Vec3 start, Vec3 end,
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

    static void renderHeavySlash(Matrix4f matrix, Effect effect,
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

    static void renderFlashSlashes(Matrix4f matrix, Effect effect,
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

    static void renderSakuraSlashes(Matrix4f matrix, Effect effect,
            float age, float strength) {
        Vec3 forward = yawDirection(effect.yaw);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        renderSakuraSlash(matrix, effect.start, forward, side, age,
                5.0F, 7.0F, 32.0D, 3.9D, strength, 0.78F);
        renderSakuraSlash(matrix, effect.start, forward, side, age,
                17.0F, 8.0F, -34.0D, 5.25D, strength, 0.92F);
    }

    static void renderSakuraSlash(Matrix4f matrix, Vec3 origin,
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

    private BladeTechniqueStyleVfxRenderer() {
    }
}
