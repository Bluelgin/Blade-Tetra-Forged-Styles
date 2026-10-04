package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable icon UV view; shared hand/world models are never mutated. */
final class InventoryBladeModel {
    private static final Map<WavefrontObject, WavefrontObject> CACHE = new LinkedHashMap<>();
    private InventoryBladeModel() {}

    static boolean isIcon(String target) {
        return "item_blade".equals(target) || "item_damaged".equals(target)
                || "item_bladens".equals(target);
    }

    static synchronized WavefrontObject forTarget(WavefrontObject source, String target) {
        if (!isIcon(target)) return source;
        WavefrontObject cached = CACHE.get(source);
        if (cached != null) return cached;
        Map<String, List<Face>> groups = new LinkedHashMap<>();
        for (var group : source.groupObjects) {
            groups.put(group.name, isIcon(group.name)
                    ? InventoryGuardGeometry.correct(remap(group.faces)) : group.faces);
        }
        WavefrontObject view = RuntimeWavefrontViewFactory.create(groups);
        CACHE.put(source, view);
        if (CACHE.size() > 128) CACHE.remove(CACHE.keySet().iterator().next());
        return view;
    }
    static synchronized void clear() { CACHE.clear(); }

    private enum Region {
        BLADE(1, 1, 63, 31), SAYA(1, 35, 63, 55), TSUKA(1, 59, 47, 81);
        final float x0, y0, x1, y1;
        Region(float x0, float y0, float x1, float y1) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        }
        static Region of(Face face) {
            if (face.textureCoordinates == null || face.textureCoordinates.length == 0) return null;
            for (Region r : values()) {
                boolean inside = true;
                for (TextureCoordinate uv : face.textureCoordinates) {
                    if (uv == null || uv.u * 128 < r.x0 || uv.u * 128 >= r.x1
                            || uv.v * 128 < r.y0 || uv.v * 128 >= r.y1) {
                        inside = false; break;
                    }
                }
                if (inside) return r;
            }
            return null;
        }
    }

    static List<Face> remap(List<Face> faces) {
        Map<Region, Projection> projections = new LinkedHashMap<>();
        for (Region r : Region.values()) {
            List<Face> selected = faces.stream().filter(f -> Region.of(f) == r).toList();
            if (!selected.isEmpty()) projections.put(r, new Projection(selected));
        }
        List<Face> result = new ArrayList<>(faces.size());
        for (Face source : faces) {
            Region r = Region.of(source);
            if (r == null) { result.add(source); continue; }
            Projection p = projections.get(r);
            Face copy = new Face();
            copy.vertices = source.vertices;
            copy.vertexNormals = source.vertexNormals;
            copy.faceNormal = source.faceNormal;
            copy.textureCoordinates = new TextureCoordinate[source.vertices.length];
            for (int i = 0; i < source.vertices.length; i++) {
                Vertex v = source.vertices[i];
                copy.textureCoordinates[i] = new TextureCoordinate(
                        (r.x0 + .5F + p.along(v) * (r.x1 - r.x0 - 1)) / 128,
                        (r.y0 + .5F + p.across(v) * (r.y1 - r.y0 - 1)) / 128);
            }
            result.add(copy);
        }
        return result;
    }

    /** Fit length and perpendicular width, rather than the rotated XY bounds. */
    private static final class Projection {
        double ax, ay, bx, by, minA, maxA, minB, maxB;
        Projection(List<Face> faces) {
            Map<String, Sample> samples = new LinkedHashMap<>();
            for (Face f : faces) for (int i = 0; i < f.vertices.length; i++) {
                Vertex v = f.vertices[i];
                samples.putIfAbsent(v.x + ":" + v.y + ":" + v.z,
                        new Sample(v, f.textureCoordinates[i]));
            }
            double mx = 0, my = 0;
            for (Sample s : samples.values()) { mx += s.v.x; my += s.v.y; }
            mx /= samples.size(); my /= samples.size();
            double xx = 0, yy = 0, xy = 0;
            for (Sample s : samples.values()) {
                double x = s.v.x - mx, y = s.v.y - my;
                xx += x * x; yy += y * y; xy += x * y;
            }
            double angle = .5 * Math.atan2(2 * xy, xx - yy);
            ax = Math.cos(angle); ay = Math.sin(angle); bx = -ay; by = ax;
            double signA = 0, signB = 0;
            for (Sample s : samples.values()) {
                signA += ((s.v.x - mx) * ax + (s.v.y - my) * ay) * s.uv.u;
                signB += ((s.v.x - mx) * bx + (s.v.y - my) * by) * s.uv.v;
            }
            if (signA < 0) { ax = -ax; ay = -ay; }
            if (signB < 0) { bx = -bx; by = -by; }
            minA = minB = Double.POSITIVE_INFINITY;
            maxA = maxB = Double.NEGATIVE_INFINITY;
            for (Sample s : samples.values()) {
                double a = s.v.x * ax + s.v.y * ay, b = s.v.x * bx + s.v.y * by;
                minA = Math.min(minA, a); maxA = Math.max(maxA, a);
                minB = Math.min(minB, b); maxB = Math.max(maxB, b);
            }
        }
        float along(Vertex v) { return unit(v.x * ax + v.y * ay, minA, maxA); }
        float across(Vertex v) { return unit(v.x * bx + v.y * by, minB, maxB); }
        static float unit(double value, double min, double max) {
            return max - min < 1e-6 ? .5F : (float) Math.max(0, Math.min(1, (value - min) / (max - min)));
        }
        private record Sample(Vertex v, TextureCoordinate uv) {}
    }
}
