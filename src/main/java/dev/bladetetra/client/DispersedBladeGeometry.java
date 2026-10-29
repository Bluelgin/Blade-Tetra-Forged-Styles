package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, bounded face views. Original positions, UVs and normals survive.
 * The standard Tetra atlas declares steel in its upper-left 64x32 UV island.
 * Unrecognised providers retain their complete model instead of losing a grip.
 * Named legacy hilts use the existing calibrated assembly, not this UV policy.
 */
public final class DispersedBladeGeometry {
    private record Key(WavefrontObject model, String group) {}
    private static final Map<Key, WavefrontObject> CACHE = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, WavefrontObject> e) {
            return size() > 128;
        }
    };
    public static WavefrontObject hilt(WavefrontObject model, String group) {
        var key = new Key(model, group);
        if (CACHE.containsKey(key)) return CACHE.get(key);
        List<Face> base = faces(model, group.contains("damaged") ? "blade_damaged" : "blade");
        boolean known = base.stream().anyMatch(DispersedBladeGeometry::steel)
                && base.stream().anyMatch(f -> !steel(f));
        if (!known) { CACHE.put(key, model); return model; }
        List<Face> retained = faces(model, group).stream().filter(f -> !steel(f)).toList();
        WavefrontObject result = retained.isEmpty() ? null : RuntimeWavefrontViewFactory.create(group, retained);
        CACHE.put(key, result);
        return result;
    }
    private static List<Face> faces(WavefrontObject model, String group) {
        return model.groupObjects.stream().filter(g -> group.equals(g.name))
                .flatMap(g -> g.faces.stream()).toList();
    }
    private static boolean steel(Face face) {
        if (face.textureCoordinates == null || face.textureCoordinates.length == 0) return false;
        for (var uv : face.textureCoordinates)
            if (uv == null || uv.u < 0 || uv.u > .5F || uv.v < 0 || uv.v > .25F) return false;
        return true;
    }
    public static void clear() { CACHE.clear(); }
    private DispersedBladeGeometry() {}
}
