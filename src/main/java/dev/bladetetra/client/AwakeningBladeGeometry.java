package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded immutable views over the real, already-adapted Tetra mesh/UVs. */
public final class AwakeningBladeGeometry {
    private record Key(WavefrontObject mesh, String group, int step) {}
    private static final Map<Key, WavefrontObject> CACHE = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, WavefrontObject> e) {
            return size() > 128;
        }
    };
    public static WavefrontObject remaining(WavefrontObject mesh, String group, float fraction) {
        if (fraction >= .999F) return mesh;
        var key = new Key(mesh, group, Math.round(Math.max(0, fraction) * 16));
        if (CACHE.containsKey(key)) return CACHE.get(key);
        var faces = mesh.groupObjects.stream().filter(g -> g.name.equals(group))
                .flatMap(g -> g.faces.stream()).toList();
        var blade = faces.stream().filter(AwakeningBladeGeometry::steel).toList();
        if (blade.isEmpty()) return mesh; // unfamiliar provider UV: preserve appearance
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var f : blade) for (var v : f.vertices) { min = Math.min(min,v.x); max = Math.max(max,v.x); }
        double cutoff = max - (max-min) * key.step / 16.0;
        List<Face> selected = new java.util.ArrayList<>(faces.stream().filter(f -> !steel(f)).toList());
        if (key.step > 0) selected.addAll(LegacyModelPartRenderer.retainFromX(blade,(float)cutoff));
        WavefrontObject result = selected.isEmpty() ? null : RuntimeWavefrontViewFactory.create(group, selected);
        CACHE.put(key, result);
        return result;
    }
    private static double center(Face face) {
        double x = 0; for (var v : face.vertices) x += v.x; return x / face.vertices.length;
    }
    private static boolean steel(Face f) {
        if (f.textureCoordinates == null || f.textureCoordinates.length == 0) return false;
        for (var uv : f.textureCoordinates)
            if (uv.u < 0 || uv.u > .5F || uv.v < 0 || uv.v > .25F) return false;
        return true;
    }
    public static void clear() { CACHE.clear(); }
    private AwakeningBladeGeometry() {}
}
