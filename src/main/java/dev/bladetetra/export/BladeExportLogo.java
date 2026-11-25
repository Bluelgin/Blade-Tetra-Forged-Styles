package dev.bladetetra.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.util.Mth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/** Generates a texture-sampled thumbnail from the final inventory mesh. */
final class BladeExportLogo {
    private static final int SIZE = 64;
    private static final int MAX_FACES = 8192;

    private BladeExportLogo() {}

    static byte[] from(BladeExportArt art) {
        byte[] mesh = art.assets().get(art.model());
        byte[] texture = art.assets().get(art.texture());
        if (mesh == null || texture == null) return new byte[0];
        try (var atlas = NativeImage.read(new ByteArrayInputStream(texture));
             var icon = new NativeImage(SIZE, SIZE, true)) {
            var model = new WavefrontObject(art.model().toString(), new ByteArrayInputStream(mesh));
            var faces = model.groupObjects.stream().filter(group -> "item_blade".equals(group.name))
                    .flatMap(group -> group.faces.stream()).limit(MAX_FACES + 1L).toList();
            if (faces.isEmpty() || faces.size() > MAX_FACES) return new byte[0];

            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
            for (var face : faces) {
                for (var vertex : face.vertices) {
                    minX = Math.min(minX, vertex.x);
                    minY = Math.min(minY, vertex.y);
                    maxX = Math.max(maxX, vertex.x);
                    maxY = Math.max(maxY, vertex.y);
                }
            }
            float extent = Math.max(maxX - minX, maxY - minY);
            if (!(extent > 0) || !Float.isFinite(extent)) return new byte[0];
            var frame = new Frame((minX + maxX) * .5F, (minY + maxY) * .5F, (SIZE - 8) / extent);
            var depth = new float[SIZE * SIZE];
            Arrays.fill(depth, Float.NEGATIVE_INFINITY);
            for (var face : faces) {
                if (face.textureCoordinates == null || face.textureCoordinates.length != face.vertices.length) continue;
                for (int last = 2; last < face.vertices.length; last++) {
                    drawTriangle(icon, atlas, depth, frame, face, last);
                }
            }
            for (float z : depth) if (Float.isFinite(z)) return icon.asByteArray();
            return new byte[0];
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().debug("Could not generate an icon from sealed blade artwork", failure);
            return new byte[0];
        }
    }

    private record Frame(float centerX, float centerY, float scale) {
        float x(Vertex v) { return SIZE * .5F + (v.x - centerX) * scale; }
        float y(Vertex v) { return SIZE * .5F - (v.y - centerY) * scale; }
    }

    private static void drawTriangle(NativeImage icon, NativeImage atlas, float[] depth, Frame frame,
                                     Face face, int last) {
        var a = face.vertices[0];
        var b = face.vertices[last - 1];
        var c = face.vertices[last];
        var uvA = face.textureCoordinates[0];
        var uvB = face.textureCoordinates[last - 1];
        var uvC = face.textureCoordinates[last];
        if (uvA == null || uvB == null || uvC == null) return;
        float ax = frame.x(a), ay = frame.y(a), bx = frame.x(b), by = frame.y(b), cx = frame.x(c), cy = frame.y(c);
        float area = cross(bx - ax, by - ay, cx - ax, cy - ay);
        if (Math.abs(area) < .0001F) return;
        int startX = Math.max(0, Mth.floor(Math.min(ax, Math.min(bx, cx))));
        int endX = Math.min(SIZE - 1, Mth.ceil(Math.max(ax, Math.max(bx, cx))));
        int startY = Math.max(0, Mth.floor(Math.min(ay, Math.min(by, cy))));
        int endY = Math.min(SIZE - 1, Mth.ceil(Math.max(ay, Math.max(by, cy))));
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                float px = x + .5F - ax, py = y + .5F - ay;
                float wb = cross(px, py, cx - ax, cy - ay) / area;
                float wc = cross(bx - ax, by - ay, px, py) / area;
                float wa = 1 - wb - wc;
                if (wa < 0 || wb < 0 || wc < 0) continue;
                float z = wa * a.z + wb * b.z + wc * c.z;
                int index = y * SIZE + x;
                if (z <= depth[index]) continue;
                float u = wa * uvA.u + wb * uvB.u + wc * uvC.u;
                float v = wa * uvA.v + wb * uvB.v + wc * uvC.v;
                int pixel = atlas.getPixelRGBA(
                        Mth.clamp((int) (u * atlas.getWidth()), 0, atlas.getWidth() - 1),
                        Mth.clamp((int) (v * atlas.getHeight()), 0, atlas.getHeight() - 1));
                if (pixel >>> 24 == 0) continue;
                depth[index] = z;
                icon.setPixelRGBA(x, y, pixel);
            }
        }
    }

    private static float cross(float ax, float ay, float bx, float by) {
        return ax * by - ay * bx;
    }
}
