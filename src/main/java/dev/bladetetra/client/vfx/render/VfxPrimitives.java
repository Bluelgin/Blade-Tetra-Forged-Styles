package dev.bladetetra.client.vfx.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shared low-level geometry helpers for client-only world-space VFX.
 *
 * <p>This class deliberately contains no technique timing, packet handling or
 * gameplay semantics. Effect families own their lifecycle; this layer only
 * translates reusable geometric primitives into vertices.</p>
 */
public final class VfxPrimitives {
    public static void planeBand(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            Vec3 right, double angle, double length, double width, int color) {
        Vec3 direction = planeVector(right, Math.cos(angle), Math.sin(angle));
        Vec3 perpendicular = planeVector(right, -Math.sin(angle), Math.cos(angle));
        Vec3 along = direction.scale(length * 0.5D);
        Vec3 across = perpendicular.scale(width * 0.5D);
        quad(buffer, matrix, center.subtract(along).subtract(across),
                center.add(along).subtract(across), center.add(along).add(across),
                center.subtract(along).add(across), color);
    }

    public static Vec3 planeVector(Vec3 right, double horizontal, double vertical) {
        return new Vec3(right.x * horizontal, vertical, right.z * horizontal);
    }

    public static void ringVertical(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
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

    public static void ringHorizontal(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
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

    public static void band3d(BufferBuilder buffer, Matrix4f matrix,
            Vec3 start, Vec3 end, double width, int color) {
        Vec3 direction = end.subtract(start);
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 0.0001D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize().scale(width);
        quad(buffer, matrix, start.subtract(side), end.subtract(side),
                end.add(side), start.add(side), color);
    }

    public static void texturedRibbon(BufferBuilder buffer, Matrix4f matrix,
            Vec3 start, Vec3 end, Vec3 halfWidth, float alpha, int color) {
        textureVertex(buffer, matrix, start.add(halfWidth), 0.0F, 0.0F, alpha, color);
        textureVertex(buffer, matrix, start.subtract(halfWidth), 0.0F, 1.0F, alpha, color);
        textureVertex(buffer, matrix, end.subtract(halfWidth), 1.0F, 1.0F, alpha, color);
        textureVertex(buffer, matrix, end.add(halfWidth), 1.0F, 0.0F, alpha, color);
    }

    public static void billboard(Matrix4f matrix, Vec3 camera, Vec3 center,
            double halfSize, float alpha, int color) {
        Vec3 facing = camera.subtract(center);
        if (facing.lengthSqr() < 0.001D) {
            facing = new Vec3(0.0D, 0.0D, 1.0D);
        }
        facing = facing.normalize();
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(facing);
        if (right.lengthSqr() < 0.001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize().scale(halfSize);
        Vec3 up = facing.cross(right).normalize().scale(halfSize);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textureVertex(buffer, matrix, center.subtract(right).add(up), 0.0F, 0.0F, alpha, color);
        textureVertex(buffer, matrix, center.subtract(right).subtract(up), 0.0F, 1.0F, alpha, color);
        textureVertex(buffer, matrix, center.add(right).subtract(up), 1.0F, 1.0F, alpha, color);
        textureVertex(buffer, matrix, center.add(right).add(up), 1.0F, 0.0F, alpha, color);
        Tesselator.getInstance().end();
    }

    public static void texturedPlane(Matrix4f matrix, Vec3 center, float yaw,
            double size, float alpha) {
        double angle = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).scale(size * 0.5D);
        Vec3 up = new Vec3(0.0D, size * 0.5D, 0.0D);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        textureVertex(buffer, matrix, center.subtract(right).add(up), 0.0F, 0.0F, alpha,
                0xFFFFFF);
        textureVertex(buffer, matrix, center.subtract(right).subtract(up), 0.0F, 1.0F, alpha,
                0xFFFFFF);
        textureVertex(buffer, matrix, center.add(right).subtract(up), 1.0F, 1.0F, alpha,
                0xFFFFFF);
        textureVertex(buffer, matrix, center.add(right).add(up), 1.0F, 0.0F, alpha,
                0xFFFFFF);
        Tesselator.getInstance().end();
    }

    public static void quad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(buffer, matrix, a, color);
        vertex(buffer, matrix, b, color);
        vertex(buffer, matrix, c, color);
        vertex(buffer, matrix, d, color);
    }

    public static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 point, int color) {
        buffer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF,
                        color & 0xFF, (color >>> 24) & 0xFF)
                .endVertex();
    }

    public static void textureVertex(BufferBuilder buffer, Matrix4f matrix,
            Vec3 position, float u, float v, float alpha, int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        buffer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .uv(u, v).color(red, green, blue,
                        Mth.clamp(Math.round(alpha * 255.0F), 0, 255)).endVertex();
    }

    public static int color(float red, float green, float blue, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int r = Mth.clamp(Math.round(red * 255.0F), 0, 255);
        int g = Mth.clamp(Math.round(green * 255.0F), 0, 255);
        int b = Mth.clamp(Math.round(blue * 255.0F), 0, 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private VfxPrimitives() {
    }
}
