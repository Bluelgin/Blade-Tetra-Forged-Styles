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
 *
 * <p>Methods accepting a {@link BufferBuilder} are the preferred path for effect
 * families that render many primitives in one pass. Convenience overloads that
 * allocate their own begin/end pair remain for isolated one-off quads.</p>
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

    public static void bandFacing(BufferBuilder buffer, Matrix4f matrix,
            Vec3 start, Vec3 end, Vec3 camera, double width, int color) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 0.0000001D) {
            return;
        }
        Vec3 center = start.add(end).scale(0.5D);
        Vec3 view = camera.subtract(center);
        Vec3 side = direction.cross(view);
        if (side.lengthSqr() < 0.0001D) {
            side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        }
        if (side.lengthSqr() < 0.0001D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize().scale(width);
        quad(buffer, matrix, start.subtract(side), end.subtract(side),
                end.add(side), start.add(side), color);
    }

    public static void texturedRibbon(BufferBuilder buffer, Matrix4f matrix,
            Vec3 start, Vec3 end, Vec3 halfWidth, float alpha, int color) {
        texturedQuad(buffer, matrix,
                start.add(halfWidth), start.subtract(halfWidth),
                end.subtract(halfWidth), end.add(halfWidth),
                0.0F, 0.0F, 1.0F, 1.0F, alpha, color);
    }

    public static void horizontalTexturedQuad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 center, double radius, float alpha, int color) {
        texturedQuad(buffer, matrix,
                center.add(-radius, 0.0D, -radius),
                center.add(-radius, 0.0D, radius),
                center.add(radius, 0.0D, radius),
                center.add(radius, 0.0D, -radius),
                0.0F, 0.0F, 1.0F, 1.0F, alpha, color);
    }

    public static void billboard(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 center, double halfSize, float alpha, int color) {
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
        texturedQuad(buffer, matrix,
                center.subtract(right).add(up),
                center.subtract(right).subtract(up),
                center.add(right).subtract(up),
                center.add(right).add(up),
                0.0F, 0.0F, 1.0F, 1.0F, alpha, color);
    }

    public static void rotatedAtlasBillboard(BufferBuilder buffer, Matrix4f matrix,
            Vec3 camera, Vec3 center, double halfSize, float rotation,
            float alpha, int color, int columns, int rows, int column, int row) {
        Vec3 facing = camera.subtract(center);
        if (facing.lengthSqr() < 0.001D) {
            facing = new Vec3(0.0D, 0.0D, 1.0D);
        }
        facing = facing.normalize();
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(facing);
        if (right.lengthSqr() < 0.001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();
        Vec3 up = facing.cross(right).normalize();
        Vec3 rotatedRight = right.scale(Math.cos(rotation))
                .add(up.scale(Math.sin(rotation))).scale(halfSize);
        Vec3 rotatedUp = up.scale(Math.cos(rotation))
                .subtract(right.scale(Math.sin(rotation))).scale(halfSize);
        float u0 = column / (float) columns;
        float u1 = (column + 1) / (float) columns;
        float v0 = row / (float) rows;
        float v1 = (row + 1) / (float) rows;
        texturedQuad(buffer, matrix,
                center.subtract(rotatedRight).add(rotatedUp),
                center.subtract(rotatedRight).subtract(rotatedUp),
                center.add(rotatedRight).subtract(rotatedUp),
                center.add(rotatedRight).add(rotatedUp),
                u0, v0, u1, v1, alpha, color);
    }

    public static void billboard(Matrix4f matrix, Vec3 camera, Vec3 center,
            double halfSize, float alpha, int color) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        billboard(buffer, matrix, camera, center, halfSize, alpha, color);
        Tesselator.getInstance().end();
    }

    public static void texturedPlane(BufferBuilder buffer, Matrix4f matrix, Vec3 center,
            float yaw, double size, float alpha, int color) {
        double angle = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle)).scale(size * 0.5D);
        Vec3 up = new Vec3(0.0D, size * 0.5D, 0.0D);
        texturedQuad(buffer, matrix,
                center.subtract(right).add(up),
                center.subtract(right).subtract(up),
                center.add(right).subtract(up),
                center.add(right).add(up),
                0.0F, 0.0F, 1.0F, 1.0F, alpha, color);
    }

    public static void texturedPlane(Matrix4f matrix, Vec3 center, float yaw,
            double size, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texturedPlane(buffer, matrix, center, yaw, size, alpha, 0xFFFFFF);
        Tesselator.getInstance().end();
    }

    public static void texturedQuad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float u0, float v0, float u1, float v1, float alpha, int color) {
        textureVertex(buffer, matrix, a, u0, v0, alpha, color);
        textureVertex(buffer, matrix, b, u0, v1, alpha, color);
        textureVertex(buffer, matrix, c, u1, v1, alpha, color);
        textureVertex(buffer, matrix, d, u1, v0, alpha, color);
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

    public static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return a << 24 | rgb & 0xFFFFFF;
    }

    private VfxPrimitives() {
    }
}
