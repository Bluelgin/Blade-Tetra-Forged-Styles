package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/** Projects the original OBJ backing geometry into the inventory portrait. */
final class NamedBackplateProjection {
    static final int MAX_FACES = 2048;

    static NativeImage bake(List<Face> faces, NativeImage atlas, int size) throws IOException {
        var textured = faces.stream().filter(NamedBackplateProjection::hasTexture).toList();
        if (textured.isEmpty()) throw new IOException("No textured named backplate faces");

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Face face : textured) {
            for (Vertex v : face.vertices) {
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
            }
        }

        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double radius = 0;
        // The farthest vertex in the diamond metric bounds every edge as well.
        for (Face face : textured) {
            for (Vertex v : face.vertices) {
                radius = Math.max(radius, Math.abs(v.x - centerX) + Math.abs(v.y - centerY));
            }
        }
        if (radius == 0 || !Double.isFinite(radius)) throw new IOException("Invalid named backplate bounds");

        double[] depth = new double[size * size];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        var output = new NativeImage(size, size, true);
        boolean drawn = false;
        double scale = size * .47 / radius;
        for (Face face : textured) {
            Point first = project(face, 0, centerX, centerY, scale, size);
            Point second = project(face, 1, centerX, centerY, scale, size);
            for (int i = 2; i < face.vertices.length; i++) {
                Point third = project(face, i, centerX, centerY, scale, size);
                drawn |= drawTriangle(output, depth, atlas, first, second, third);
                second = third;
            }
        }
        if (drawn) return output;
        output.close();
        throw new IOException("Empty named backplate projection");
    }

    private static boolean hasTexture(Face face) {
        if (face.vertices == null || face.vertices.length < 3 || face.textureCoordinates == null
                || face.textureCoordinates.length != face.vertices.length) return false;
        for (int i = 0; i < face.vertices.length; i++) {
            var vertex = face.vertices[i];
            var uv = face.textureCoordinates[i];
            if (vertex == null || uv == null || !Float.isFinite(vertex.x) || !Float.isFinite(vertex.y)
                    || !Float.isFinite(vertex.z) || !Float.isFinite(uv.u) || !Float.isFinite(uv.v)) return false;
        }
        return true;
    }

    private static Point project(Face face, int i, double centerX, double centerY, double scale, int size) {
        var vertex = face.vertices[i];
        var uv = face.textureCoordinates[i];
        return new Point(size / 2.0 + (vertex.x - centerX) * scale,
                size / 2.0 - (vertex.y - centerY) * scale, vertex.z, uv.u, uv.v);
    }

    private static boolean drawTriangle(NativeImage output, double[] depth, NativeImage atlas,
            Point a, Point b, Point c) {
        double area = edge(a, b, c.x, c.y);
        if (Math.abs(area) < 1e-8) return false;
        int size = output.getWidth();
        int left = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int right = Math.min(size, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int top = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int bottom = Math.min(size, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));

        boolean drawn = false;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                double wa = edge(b, c, x + .5, y + .5) / area;
                double wb = edge(c, a, x + .5, y + .5) / area;
                double wc = 1 - wa - wb;
                if (wa < -1e-8 || wb < -1e-8 || wc < -1e-8) continue;

                double z = wa * a.z + wb * b.z + wc * c.z;
                int index = y * size + x;
                if (z < depth[index]) continue;

                double u = wa * a.u + wb * b.u + wc * c.u;
                double v = wa * a.v + wb * b.v + wc * c.v;
                int pixel = atlas.getPixelRGBA(texel(u, atlas.getWidth()), texel(v, atlas.getHeight()));
                if ((pixel >>> 24) == 0) continue;
                output.setPixelRGBA(x, y, pixel);
                depth[index] = z;
                drawn = true;
            }
        }
        return drawn;
    }

    private static double edge(Point a, Point b, double x, double y) {
        return (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
    }

    private static int texel(double coordinate, int size) {
        if (coordinate < 0 || coordinate > 1) coordinate -= Math.floor(coordinate);
        return Math.min(size - 1, (int) (coordinate * size));
    }

    private record Point(double x, double y, double z, double u, double v) {}

    private NamedBackplateProjection() {}
}
