package dev.bladetetra.client.vfx.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Shared proportions of the existing thousand-gates doorway, independent of animation and transport. */
public final class MikageToriiMesh {
    public static void draw(VertexConsumer vertices, Matrix4f matrix, Vec3 at, Vec3 right,
            Vec3 camera, double scale, double spread, int red, int white, int mirror) {
        for (int side : new int[]{-1, 1}) {
            Vec3 base = at.add(right.scale(side * .81 * spread * scale));
            bar(vertices, matrix, base, base.add(0, 1.89 * scale, 0), camera, scale, red, white);
        }
        for (double[] crossbar : new double[][]{{1.59, 1.05}, {1.95, 1.23}}) {
            Vec3 middle = at.add(0, crossbar[0] * scale, 0);
            Vec3 half = right.scale(crossbar[1] * spread * scale);
            bar(vertices, matrix, middle.subtract(half), middle.add(half), camera, scale, red, white);
        }
        Vec3 left = at.add(right.scale(-.76 * spread * scale));
        Vec3 r = at.add(right.scale(.76 * spread * scale));
        quad(vertices, matrix, left.add(0, .02 * scale, 0), r.add(0, .02 * scale, 0),
                r.add(0, 1.8 * scale, 0), left.add(0, 1.8 * scale, 0), mirror);
        band(vertices, matrix, at.add(0, .03 * scale, 0), at.add(0, 1.8 * scale, 0),
                camera, .018 * scale, white);
    }
    private static void bar(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b,
            Vec3 camera, double scale, int red, int white) {
        band(v, m, a, b, camera, .065 * scale, red);
        band(v, m, a, b, camera, .018 * scale, white);
    }
    private static void band(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b,
            Vec3 camera, double width, int color) {
        Vec3 side = b.subtract(a).cross(camera.subtract(a.add(b).scale(.5)));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        quad(v, m, a.subtract(side), b.subtract(side), b.add(side), a.add(side), color);
    }
    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(v, m, a, color); vertex(v, m, b, color);
        vertex(v, m, c, color); vertex(v, m, d, color);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Vec3 p, int color) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
    }
    private MikageToriiMesh() { }
}
