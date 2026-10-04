package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Corrects the oversized axial extrusion in the icon guard, not the world guard. */
final class InventoryGuardGeometry {
    private InventoryGuardGeometry() {}

    static boolean isGuard(Face face) { return inside(face, 52, 58, 76, 82); }

    private static boolean inside(Face face, float x0, float y0, float x1, float y1) {
        if (face.textureCoordinates == null || face.textureCoordinates.length == 0) return false;
        for (var uv : face.textureCoordinates) {
            if (uv == null || uv.u * 128 < x0 || uv.u * 128 >= x1
                    || uv.v * 128 < y0 || uv.v * 128 >= y1) return false;
        }
        return true;
    }

    static List<Face> correct(List<Face> faces) {
        List<Vertex> guard = unique(faces.stream().filter(InventoryGuardGeometry::isGuard).toList());
        List<Vertex> handle = unique(faces.stream().filter(f -> inside(f, 1, 59, 47, 81)).toList());
        if (guard.isEmpty() || handle.isEmpty()) return faces;
        Vertex center = center(guard), grip = center(handle);
        double nx = grip.x - center.x, ny = grip.y - center.y, nz = grip.z - center.z;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6) return faces;
        nx /= length; ny /= length; nz /= length;
        double thickness = 0, radius = 0;
        for (Vertex v : guard) {
            double x = v.x - center.x, y = v.y - center.y, z = v.z - center.z;
            double axial = x * nx + y * ny + z * nz;
            thickness = Math.max(thickness, Math.abs(axial));
            radius = Math.max(radius, Math.sqrt(Math.max(0, x*x + y*y + z*z - axial*axial)));
        }
        // Icon extraction inherited a ~12-unit half-thickness; the world guard
        // has ~1 unit. Keep the existing contour/opening, only flatten extrusion.
        if (thickness < 1e-6 || radius < 1e-6) return faces;
        double scale = Math.min(1, radius * .06 / thickness);
        List<Face> result = new ArrayList<>(faces.size());
        for (Face source : faces) {
            if (!isGuard(source)) { result.add(source); continue; }
            Face copy = new Face();
            copy.textureCoordinates = source.textureCoordinates;
            copy.vertices = new Vertex[source.vertices.length];
            for (int i = 0; i < source.vertices.length; i++) {
                Vertex v = source.vertices[i];
                double axial = (v.x-center.x)*nx + (v.y-center.y)*ny + (v.z-center.z)*nz;
                double delta = axial * (scale - 1);
                copy.vertices[i] = new Vertex((float)(v.x + delta*nx),
                        (float)(v.y + delta*ny), (float)(v.z + delta*nz));
            }
            // Non-uniform flattening needs new normals on the bevel/side faces.
            copy.faceNormal = copy.calculateFaceNormal();
            result.add(copy);
        }
        return result;
    }

    private static List<Vertex> unique(List<Face> faces) {
        Map<String, Vertex> points = new LinkedHashMap<>();
        for (Face f : faces) for (Vertex v : f.vertices) {
            points.putIfAbsent(v.x + ":" + v.y + ":" + v.z, v);
        }
        return new ArrayList<>(points.values());
    }

    private static Vertex center(List<Vertex> points) {
        double x = 0, y = 0, z = 0;
        for (Vertex v : points) { x += v.x; y += v.y; z += v.z; }
        return new Vertex((float)(x / points.size()), (float)(y / points.size()), (float)(z / points.size()));
    }
}
