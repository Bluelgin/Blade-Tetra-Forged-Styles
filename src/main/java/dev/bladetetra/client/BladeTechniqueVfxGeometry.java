package dev.bladetetra.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.bladetetra.client.vfx.render.VfxPrimitives;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Geometry/timing helpers shared by BladeTechniqueVfxClient render families.
 *
 * <p>Technique dispatch and lifecycle stay in the client facade; low-level
 * primitive emission is delegated to {@link VfxPrimitives} so older technique
 * effects do not maintain a second vertex toolkit.</p>
 */
final class BladeTechniqueVfxGeometry {
    static float peakedAlpha(float age, float peak, float fadeTicks) {
        if (age < 0.0F || age >= peak + fadeTicks) return 0.0F;
        if (age <= peak) {
            float rise = peak <= 0.0F ? 1.0F : Mth.clamp(age / peak, 0.0F, 1.0F);
            return 0.28F + rise * 0.72F;
        }
        float fade = 1.0F - (age - peak) / fadeTicks;
        return fade * fade;
    }

    static void texturedSlash(Matrix4f matrix, Vec3 center,
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

    static void textureVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 point, float u, float v, int red, int green, int blue, float alpha) {
        VfxPrimitives.textureVertex(buffer, matrix, point, u, v, alpha,
                red << 16 | green << 8 | blue);
    }

    static float timedFade(int age, int trigger, int life) {
        if (age < trigger || age >= trigger + life) return 0.0F;
        float local = (age - trigger) / (float) Math.max(1, life);
        return (1.0F - local) * (1.0F - local);
    }

    static Vec3 flatDirection(Vec3 start, Vec3 end, float yaw) {
        Vec3 direction = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 0.0001D ? yawDirection(yaw) : direction.normalize();
    }

    static Vec3 yawDirection(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
    }

    static void arcHorizontal(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
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

    static void arcVertical(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
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

    static void bezier(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
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

    static void drawTorii(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
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

    static void drawRibbon(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
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

    static int segments(int base, int quality) {
        return quality <= 0 ? Math.max(12, base / 2) : quality == 1 ? base : base + base / 3;
    }

    static Vec3 deterministicDirection(int seed, int index) {
        double angle = (seed * 0.000173D + index * 2.399963D) % (Math.PI * 2.0D);
        double y = -0.38D + ((seed >>> (index % 16)) & 7) / 7.0D * 1.18D;
        return new Vec3(Math.cos(angle), y, Math.sin(angle)).normalize();
    }

    static Vec3 horizontalSide(Vec3 start, Vec3 end) {
        Vec3 direction = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.0001D) return new Vec3(1.0D, 0.0D, 0.0D);
        return new Vec3(-direction.z, 0.0D, direction.x).normalize();
    }

    static Vec3 cameraRight(Vec3 center, Vec3 camera) {
        Vec3 view = camera.subtract(center).multiply(1.0D, 0.0D, 1.0D);
        if (view.lengthSqr() < 0.0001D) return new Vec3(1.0D, 0.0D, 0.0D);
        return new Vec3(view.z, 0.0D, -view.x).normalize();
    }

    static Vec3 planeVector(Vec3 right, double horizontal, double vertical) {
        return VfxPrimitives.planeVector(right, horizontal, vertical);
    }

    static void line(BufferBuilder buffer, Matrix4f matrix, Vec3 camera,
            Vec3 start, Vec3 end, double halfWidth, int color) {
        VfxPrimitives.bandFacing(buffer, matrix, start, end, camera, halfWidth, color);
    }

    static void ringVertical(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            Vec3 right, double radius, double width, int color, int segments) {
        VfxPrimitives.ringVertical(buffer, matrix, center, right,
                radius, width, color, segments);
    }

    static void ringHorizontal(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            double radius, double width, int color, int segments) {
        VfxPrimitives.ringHorizontal(buffer, matrix, center,
                radius, width, color, segments);
    }

    static void quad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        VfxPrimitives.quad(buffer, matrix, a, b, c, d, color);
    }

    static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, int color) {
        VfxPrimitives.vertex(buffer, matrix, point, color);
    }

    static int color(float red, float green, float blue, float alpha) {
        return VfxPrimitives.color(red, green, blue, alpha);
    }

    static int withAlpha(int color, float alpha) {
        return VfxPrimitives.withAlpha(color, alpha);
    }

    static int shadeColor(int color, float brightness) {
        float shade = Mth.clamp(brightness, 0.0F, 1.0F);
        int red = Mth.clamp(Math.round(((color >> 16) & 0xFF) * shade), 0, 255);
        int green = Mth.clamp(Math.round(((color >> 8) & 0xFF) * shade), 0, 255);
        int blue = Mth.clamp(Math.round((color & 0xFF) * shade), 0, 255);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static Vec3 entityCenter(Entity entity) {
        return entity instanceof LivingEntity living
                ? living.position().add(0.0D, living.getBbHeight() * 0.55D, 0.0D)
                : entity.position();
    }


    private BladeTechniqueVfxGeometry() {
    }
}
