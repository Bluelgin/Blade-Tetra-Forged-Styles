package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.client.LegacySourceMesh.Component;
import dev.bladetetra.client.LegacySourceMesh.Layers;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;

/** Owns per-reload client source resolution, including negative results. */
final class LegacyRenderSources {
    private static final int MAX_OBJ_BYTES = 8 * 1024 * 1024;
    private static final LegacyMeshCache<ResourceLocation, WavefrontObject> PARSED =
            new LegacyMeshCache<>(96, 180_000, LegacyRenderSources::triangleCount);
    private static final LegacyMeshCache<LegacyImprintKind, Source> SOURCES =
            new LegacyMeshCache<>(96, 160_000, Source::triangles);
    private static final LegacyMeshCache<ViewKey, Layers> GEOMETRY =
            new LegacyMeshCache<>(320, 180_000, LegacyRenderSources::count);
    private static final LegacyMeshCache<ViewKey, Views> VIEWS =
            new LegacyMeshCache<>(320, 180_000, Views::triangles);
    private static final LegacyMeshCache<ViewKey, Support> COVERAGE =
            new LegacyMeshCache<>(384, 384, ignored -> 1);

    enum Support { FULL, PARTIAL, NEEDS_ADAPTER, UNSUPPORTED, MISSING_RESOURCE }

    record Source(LegacySourceMesh mesh, int triangles, String reason, Support failure, Object identity) {
        Source(LegacySourceMesh mesh, int triangles, String reason, Support failure) {
            this(mesh, triangles, reason, failure, new Object());
        }

        Support support(Component part) {
            return coverage(this, part);
        }

        private Support analyze(Component part) {
            if (mesh == null) return failure;
            Layers faces = geometry(this, part, worldGroup(part));
            if (faces.empty()) {
                if (part == Component.BLADE && mesh.ambiguousBlade) return Support.NEEDS_ADAPTER;
                return part == Component.HILT && !geometry(this, Component.BLADE, "blade").empty()
                        ? Support.NEEDS_ADAPTER : Support.UNSUPPORTED;
            }
            Views views = views(this, part, worldGroup(part));
            if (views.empty()) return Support.UNSUPPORTED;
            var blade = mesh.anchor(Component.BLADE, geometry(this, Component.BLADE, "blade").reference(), null);
            if (mesh.anchor(part, faces.reference(), blade == null ? null : blade.origin()) == null) {
                return Support.NEEDS_ADAPTER;
            }
            if (part == Component.BLADE && views(this, part, "blade_damaged").empty()) return Support.PARTIAL;
            return Support.FULL;
        }
    }

    record Views(WavefrontObject base, WavefrontObject luminous, int triangles) {
        boolean empty() { return base == null && luminous == null; }
    }

    private record ViewKey(Object identity, Component part, String group) {}

    private static synchronized Support coverage(Source source, Component part) {
        return COVERAGE.get(new ViewKey(source.identity, part, worldGroup(part)), key -> source.analyze(key.part));
    }

    static synchronized Source resolve(LegacyImprintKind kind) {
        return SOURCES.get(kind, LegacyRenderSources::load);
    }

    static synchronized Source preview(LegacyImprintKind kind, LegacyCalibrationProfile profile) {
        var resolved = resolve(kind);
        if (resolved.mesh == null || profile.equals(resolved.mesh.profile)) return resolved;
        return resolve(new LegacyImprintKind(kind.id(), kind.name(), kind.model(), kind.texture(),
                kind.material(), profile, kind.baseAttack(), kind.maxDamage(), kind.slashArt(), kind.specialEffects()));
    }

    private static Source load(LegacyImprintKind kind) {
        try {
            var resources = Minecraft.getInstance().getResourceManager();
            var rules = LegacyModelRules.load(resources, kind);
            var geometryModel = rules.geometryModel(kind.model());
            if (resources.getResource(geometryModel).isEmpty()) {
                return new Source(null, 0, "missing model resource", Support.MISSING_RESOURCE);
            }
            if (resources.getResource(kind.texture()).isEmpty()) {
                return new Source(null, 0, "missing texture resource", Support.MISSING_RESOURCE);
            }
            var model = PARSED.get(geometryModel, LegacyRenderSources::parse);
            if (model == null) return new Source(null, 0, "invalid OBJ or mesh budget", Support.UNSUPPORTED);
            return new Source(new LegacySourceMesh(model, rules, kind.defaultProfile()),
                    triangleCount(model), "", null);
        } catch (RuntimeException failure) {
            return new Source(null, 0, "cannot use model: " + failure.getClass().getSimpleName(), Support.UNSUPPORTED);
        }
    }

    private static WavefrontObject parse(ResourceLocation location) {
        try (var stream = Minecraft.getInstance().getResourceManager().open(location)) {
            byte[] bytes = stream.readNBytes(MAX_OBJ_BYTES + 1);
            if (bytes.length > MAX_OBJ_BYTES) return null;
            WavefrontObject model = new WavefrontObject(location.toString(), new ByteArrayInputStream(bytes));
            return triangleCount(model) <= LegacySourceMesh.MAX_TRIANGLES ? model : null;
        } catch (Exception failure) {
            return null;
        }
    }

    static synchronized Layers geometry(Source source, Component part, String group) {
        if (group.startsWith("item_") && !source.mesh.hasExplicitIcon(part, group)) {
            group = group.equals("item_damaged") && part == Component.BLADE ? "blade_damaged" : worldGroup(part);
        }
        return GEOMETRY.get(new ViewKey(source.identity, part, group),
                key -> source.mesh.geometry(key.part, key.group));
    }

    static synchronized Views views(Source source, Component part, String group) {
        return VIEWS.get(new ViewKey(source.identity, part, group), key -> {
            Layers layers = geometry(source, key.part, key.group);
            var base = view(key.group, layers.base());
            var luminous = view(key.group + "_luminous", layers.luminous());
            return new Views(base, luminous, triangleCount(base) + triangleCount(luminous));
        });
    }

    static WavefrontObject view(String group, java.util.List<mods.flammpfeil.slashblade.client.renderer.model.obj.Face> faces) {
        if (faces.isEmpty()) return null;
        try {
            return RuntimeWavefrontViewFactory.create(group, faces);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static int count(Layers layers) {
        return layers.base().size() + layers.luminous().size();
    }

    static int triangleCount(WavefrontObject model) {
        if (model == null || model.groupObjects == null) return 0;
        int count = 0;
        for (var group : model.groupObjects) if (group != null && group.faces != null) {
            for (var face : group.faces) if (face != null && face.vertices != null) {
                count += Math.max(1, face.vertices.length - 2);
            }
        }
        return count;
    }

    static String worldGroup(Component part) { return part == Component.SAYA ? "sheath" : "blade"; }

    static synchronized void clear() {
        PARSED.clear();
        SOURCES.clear();
        GEOMETRY.clear();
        VIEWS.clear();
        COVERAGE.clear();
    }

    private LegacyRenderSources() {}
}
