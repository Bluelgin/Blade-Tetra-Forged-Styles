package dev.bladetetra.client.vfx.render;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static dev.bladetetra.client.vfx.render.VfxPrimitives.texturedQuad;
/** Shared original atlas primitives; callers own batches and bounded lifecycles. */
public final class AwakenedAtlasBatch {
    public static void segment(BufferBuilder buffer, Matrix4f matrix, Vec3 eye,
            Vec3 from, Vec3 to, double width, int tile, int soul, float alpha) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 0.001D || axis.lengthSqr() > 64.0D) return;
        Vec3 side = axis.cross(eye.subtract(from.add(to).scale(0.5D)));
        if (side.lengthSqr() < 0.001D) side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001D) return;
        side = side.normalize().scale(width);
        uvQuad(buffer, matrix, from.add(side), from.subtract(side),
                to.subtract(side), to.add(side), tile, soul, alpha);
    }

    public static void billboardTile(BufferBuilder buffer, Matrix4f matrix,
            Vec3 eye, Vec3 center, double size, int tile, int soul,
            float alpha, float rotation) {
        Vec3 look = eye.subtract(center);
        if (look.lengthSqr() < 0.001D) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(look);
        if (right.lengthSqr() < 0.001D) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();
        Vec3 dx = right.scale(Math.cos(rotation)).add(up.scale(Math.sin(rotation)))
                .scale(size * 0.5D);
        Vec3 dy = up.scale(Math.cos(rotation)).subtract(right.scale(Math.sin(rotation)))
                .scale(size * 0.5D);
        uvQuad(buffer, matrix, center.subtract(dx).add(dy),
                center.subtract(dx).subtract(dy), center.add(dx).subtract(dy),
                center.add(dx).add(dy), tile, soul, alpha);
    }

    public static void uvQuad(BufferBuilder buffer, Matrix4f matrix,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d, int tile, int soul, float alpha) {
        int columns = soul == 1 ? 8 : 4;
        int height = soul == 1 ? 512 : 512;
        int width = soul == 1 ? 1024 : 512;
        int x = (tile % columns) * 128;
        int y = (tile / columns) * 128;
        float u0 = (x + 0.5F) / width;
        float v0 = (y + 0.5F) / height;
        float u1 = (x + 127.5F) / width;
        float v1 = (y + 127.5F) / height;
        texturedQuad(buffer, matrix, a, b, c, d,
                u0, v0, u1, v1, alpha, 0xFFFFFF);
    }

    private AwakenedAtlasBatch() {}
}
