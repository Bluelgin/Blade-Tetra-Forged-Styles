package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyModelAdapter.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Validated source mesh, separate from target assembly and texture/render state. */
final class LegacySourceMesh {
    enum Component {
        BLADE, HILT, SAYA;
        String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    record Layers(List<Face> base, List<Face> luminous) {
        static final Layers EMPTY = new Layers(List.of(), List.of());
        List<Face> reference() { return base.isEmpty() ? luminous : base; }
        boolean empty() { return base.isEmpty() && luminous.isEmpty(); }
    }

    static final int MAX_TRIANGLES = 32_768;
    private final Map<String, List<Face>> groups = new HashMap<>();
    final LegacyModelRules rules;
    final LegacyMeshFrame frame;
    final LegacyCalibrationProfile profile;
    final float cut;
    final boolean separateHilt;
    final boolean reliableHilt;
    final boolean ambiguousBlade;
    private final Layers blade;
    private final Layers hilt;
    private final Layers saya;

    LegacySourceMesh(WavefrontObject source, LegacyModelRules rules, LegacyCalibrationProfile supplied) {
        this.rules = rules;
        int triangles = 0;
        if (source.groupObjects == null || source.groupObjects.size() > 2048) {
            throw new IllegalArgumentException("model group budget");
        }
        for (var group : source.groupObjects) {
            if (group == null || group.name == null || group.faces == null) continue;
            List<Face> valid = groups.computeIfAbsent(group.name, ignored -> new ArrayList<>());
            for (Face face : group.faces) {
                if (face == null || face.vertices == null) continue;
                triangles += Math.max(1, face.vertices.length - 2);
                if (triangles > MAX_TRIANGLES) throw new IllegalArgumentException("model triangle budget");
                if (RuntimeWavefrontViewFactory.validFace(face)) valid.add(face);
            }
        }
        blade = layers("blade", rules.legacy().bladeGroups(), List.of("blade_luminous"));
        hilt = layers("hilt", rules.legacy().hiltGroups(), List.of("handle_luminous"));
        saya = layers("saya", rules.legacy().sayaGroups(), List.of("sheath_luminous"));
        separateHilt = !hilt.empty();
        List<Face> reference = !blade.empty() ? blade.reference() : !saya.empty() ? saya.reference() : hilt.reference();
        Axis axis = rules.explicitAxis();
        if (axis == null) axis = dominantAxis(reference);
        Axis defaultWidth = axis == Axis.X ? Axis.Y : axis == Axis.Y ? Axis.Z : Axis.X;
        Axis width = rules.axis("width_axis", defaultWidth);
        if (width == axis) width = defaultWidth;
        var forward = LegacyMeshFrame.cardinal(axis, false, width);
        Boolean reverse = rules.reversed();
        if (supplied.revision() > 0 || (reverse == null && supplied.flipped())) reverse = supplied.flipped();
        if (reverse == null) reverse = inferReversal(forward);
        frame = LegacyMeshFrame.cardinal(axis, reverse, width);
        var span = frame.span(blade.reference());
        Vector3f explicit = rules.point("blade_anchor");
        float candidate = span.at(Math.min(supplied.tsubaMin(), supplied.tsukaMin()));
        boolean authoredBlade = rules.json().has("blade_groups") || rules.json().has("blade_luminous_groups");
        boolean calibrated = explicit != null || supplied.revision() > 0 || rules.legacy().tsubaCenter() != null;
        boolean trustworthy = calibrated || separateHilt || credibleGuard(span);
        if (explicit != null) {
            candidate = frame.along(new mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex(
                    explicit.x, explicit.y, explicit.z));
        } else if (separateHilt) {
            var handle = frame.span(hilt.reference());
            if (handle.valid()) candidate = handle.min();
        } else if (!calibrated && trustworthy && !saya.empty()) {
            var sheath = frame.span(saya.reference());
            // A nearby mouth corroborates the conventional guard station. Distant
            // sheath tips, flanges and handles are not used as cutting planes.
            if (Math.abs(sheath.max() - candidate) < span.length() * .035F) candidate = sheath.max();
        }
        boolean safeCut = span.valid() && trustworthy && (separateHilt
                || (candidate > span.at(.48F) && candidate < span.at(.98F)));
        reliableHilt = separateHilt || (safeCut && rules.splitHilt() && (!authoredBlade || calibrated));
        ambiguousBlade = !blade.empty() && !safeCut && !authoredBlade;
        cut = safeCut && (!authoredBlade || calibrated || separateHilt) ? candidate : Float.POSITIVE_INFINITY;
        profile = supplied;
    }

    private Layers layers(String key, List<String> defaults, List<String> emissive) {
        List<String> normal = rules.groups(key + "_groups",
                defaults.stream().filter(name -> !name.endsWith("_luminous")).toList());
        var glowDefaults = new LinkedHashSet<>(emissive);
        defaults.stream().filter(name -> name.endsWith("_luminous")).forEach(glowDefaults::add);
        var glow = rules.groups(key + "_luminous_groups", List.copyOf(glowDefaults));
        return new Layers(faces(normal), faces(glow.stream().filter(name -> !normal.contains(name)).toList()));
    }

    private List<Face> faces(List<String> names) {
        if (names.size() == 1) return groups.getOrDefault(names.get(0), List.of());
        List<Face> result = new ArrayList<>();
        for (String name : names) result.addAll(groups.getOrDefault(name, List.of()));
        return result;
    }

    private static Axis dominantAxis(List<Face> faces) {
        Axis dominant = Axis.X;
        float longest = 0;
        for (Axis axis : Axis.values()) {
            Axis width = axis == Axis.X ? Axis.Y : Axis.X;
            float length = LegacyMeshFrame.cardinal(axis, false, width).span(faces).length();
            if (Float.isFinite(length) && length > longest) {
                longest = length;
                dominant = axis;
            }
        }
        return dominant;
    }

    private boolean inferReversal(LegacyMeshFrame forward) {
        var bladeSpan = forward.span(blade.reference());
        if (!bladeSpan.valid()) return false;
        var handleSpan = forward.span(hilt.reference());
        if (handleSpan.valid()) {
            return handleSpan.at(.5F) < bladeSpan.at(.5F);
        }
        var sheathSpan = forward.span(saya.reference());
        return sheathSpan.valid() && sheathSpan.max() > bladeSpan.at(.88F)
                && sheathSpan.min() > bladeSpan.at(.08F);
    }

    private boolean credibleGuard(LegacyMeshFrame.Span span) {
        if (!span.valid()) return false;
        var rotation = new org.joml.Quaternionf(frame.rotation()).conjugate();
        Vector3f point = new Vector3f();
        float middleMin = Float.POSITIVE_INFINITY, middleMax = Float.NEGATIVE_INFINITY;
        float rearMin = Float.POSITIVE_INFINITY, rearMax = Float.NEGATIVE_INFINITY;
        for (Face face : blade.reference()) for (var v : face.vertices) {
            rotation.transform(point.set(v.x, v.y, v.z));
            float t = (point.x - span.min()) / span.length();
            if (t >= .18F && t <= .62F) {
                middleMin = Math.min(middleMin, point.z);
                middleMax = Math.max(middleMax, point.z);
            } else if (t > .62F && t < .96F) {
                rearMin = Math.min(rearMin, point.z);
                rearMax = Math.max(rearMax, point.z);
            }
        }
        return Float.isFinite(middleMin) && rearMax - rearMin > Math.max(.001F, (middleMax - middleMin) * 1.4F);
    }

    Layers geometry(Component part, String state) {
        String prefix = state + "_" + part.key();
        if (rules.json().has(prefix + "_groups") || rules.json().has(prefix + "_luminous_groups")) {
            return layers(prefix, List.of(), List.of());
        }
        if (part == Component.SAYA) return saya;
        if (part == Component.HILT) {
            if (separateHilt) return hilt;
            if (!reliableHilt || !Float.isFinite(cut)) return Layers.EMPTY;
            return new Layers(clip(blade.base, cut, Float.POSITIVE_INFINITY),
                    clip(blade.luminous, cut, Float.POSITIVE_INFINITY));
        }
        if (ambiguousBlade) return Layers.EMPTY;
        Layers selected = state.equals("blade_damaged") || state.equals("blade_fragment")
                ? layers(state + "_blade", List.of(state), List.of(state + "_luminous")) : blade;
        if (!Float.isFinite(cut)) return selected;
        return new Layers(clip(selected.base, Float.NEGATIVE_INFINITY, cut),
                clip(selected.luminous, Float.NEGATIVE_INFINITY, cut));
    }

    List<Face> clip(List<Face> faces, float min, float max) {
        return LegacyFaceClipper.clip(faces, frame.longitudinal()::coordinate, min, max);
    }

    boolean hasExplicitIcon(Component part, String state) {
        String prefix = state + "_" + part.key();
        return rules.json().has(prefix + "_groups") || rules.json().has(prefix + "_luminous_groups");
    }

    LegacyMeshFrame anchor(Component part, List<Face> faces, Vector3f bladeRoot) {
        Vector3f origin = rules.point(part.key() + "_anchor");
        Vector3f direction = rules.point(part.key() + "_direction");
        var span = frame.span(faces);
        if (!span.valid() && origin == null) return null;
        if (origin == null && part == Component.HILT) origin = rules.point("blade_anchor");
        if (origin == null) {
            float station = part == Component.HILT ? span.min() : span.max();
            if (part == Component.SAYA && bladeRoot != null) {
                float expected = frame.direction().dot(bladeRoot);
                float difference = Math.abs(expected - station);
                if (difference > span.length() * .18F) return null;
                station = Math.min(station, expected);
            }
            origin = frame.section(faces, station, Math.max(.001F, span.length() * .06F));
        }
        if (origin == null) return null;
        if (part == Component.SAYA && direction == null) {
            // Keep the authored transverse orientation. Infer a modest lean
            // from two local sections; large differences require a declaration.
            Vector3f back = frame.section(faces, span.at(.25F), span.length() * .12F);
            if (back != null) {
                Vector3f candidate = new Vector3f(origin).sub(back);
                if (candidate.lengthSquared() > .000001F && candidate.normalize().dot(frame.direction()) > .94F) {
                    direction = candidate;
                }
            }
        }
        if (direction == null) return frame.at(origin);
        if (direction.lengthSquared() < .000001F
                || new Vector3f(direction).normalize().cross(frame.width()).lengthSquared() < .000001F) return null;
        return LegacyMeshFrame.of(origin, direction, frame.width());
    }
}
