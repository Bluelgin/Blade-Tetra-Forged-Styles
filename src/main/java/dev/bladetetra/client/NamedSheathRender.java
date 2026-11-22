package dev.bladetetra.client;

import dev.bladetetra.client.LegacyModelPartRenderer.Part;
import dev.bladetetra.client.LegacySourceMesh.Component;
import dev.bladetetra.client.LegacySourceMesh.Layers;
import dev.bladetetra.forging.LegacyImprintKind;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.joml.Matrix4f;

/** Caches measured mounted parts and complete, texture-independent sheath replacements. */
final class NamedSheathRender {
    private record PartKey(Object source, WavefrontObject target, Component part) {}
    private record PairKey(Object blade, Object sheath, WavefrontObject target) {}
    record Result(LegacyRenderSources.Views views, boolean stockTexture) {}
    private static final LegacyMeshCache<PartKey, NamedBladeSheathAlignment.Profile> PROFILES =
            new LegacyMeshCache<>(384, 384, ignored -> 1);
    private static final LegacyMeshCache<PairKey, Result> PAIRS =
            new LegacyMeshCache<>(192, 160_000, result -> result.views.triangles());

    static Result resolve(LegacyImprintKind blade, LegacyImprintKind sheath, WavefrontObject target) {
        if (blade == null && sheath == null) return null;
        return resolve(blade == null ? null : LegacyRenderSources.resolve(blade),
                sheath == null ? null : LegacyRenderSources.resolve(sheath), target);
    }

    static Result resolve(LegacyRenderSources.Source blade, LegacyRenderSources.Source sheath, WavefrontObject target) {
        var key = new PairKey(identity(blade), identity(sheath), target);
        return PAIRS.get(key, ignored -> assemble(blade, sheath, target));
    }

    private static Result assemble(LegacyRenderSources.Source blade, LegacyRenderSources.Source sheath,
            WavefrontObject target) {
        var bladePart = part(blade, target, Component.BLADE);
        var sheathPart = part(sheath, target, Component.SAYA);
        if (!bladePart.named && !sheathPart.named) return null;
        var edge = profile(bladePart);
        var shell = profile(sheathPart);
        var fit = NamedBladeSheathAlignment.fit(edge, shell);
        if (fit != null && fit.unchanged()) return null;
        Result adapted = build(edge, sheathPart, fit);
        if (adapted != null) return adapted;

        if (sheathPart.named) {
            var ordinary = part(null, target, Component.SAYA);
            var stockFit = NamedBladeSheathAlignment.fit(edge, profile(ordinary));
            if (stockFit != null && stockFit.unchanged()) {
                return views(ordinary.layers, true);
            }
            adapted = build(edge, ordinary, stockFit);
            if (adapted != null) return adapted;
        }
        // Keep the existing animation and geometry when the model cannot be
        // measured reliably. This also keeps unsupported providers playable.
        if (Boolean.getBoolean("blade_tetra.sheathDiagnostics")) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Named sheath kept original rendering: no verified fit for the mounted blade and sheath");
        }
        return null;
    }

    private record MountedPart(PartKey key, Layers layers, Matrix4f transform, boolean named) {}

    private static MountedPart part(LegacyRenderSources.Source source, WavefrontObject target, Component part) {
        String group = LegacyRenderSources.worldGroup(part);
        var mounted = source == null ? null : NamedBladeRenderPlan.mounted(source, part, target, group);
        if (mounted != null) {
            return new MountedPart(new PartKey(source.identity(), target, part),
                    LegacyRenderSources.geometry(source, part, group), mounted.mount().matrix(), true);
        }
        Part stock = part == Component.SAYA ? Part.SAYA : Part.BLADE;
        return new MountedPart(new PartKey(null, target, part),
                new Layers(LegacyModelPartRenderer.modularFaces(target, group, stock),
                        LegacyModelPartRenderer.modularFaces(target, group + "_luminous", stock)),
                new Matrix4f(), false);
    }

    private static NamedBladeSheathAlignment.Profile profile(MountedPart part) {
        return PROFILES.get(part.key, ignored -> NamedBladeSheathAlignment.profile(
                part.layers.reference(), part.transform, part.key.part == Component.SAYA));
    }

    private static Result build(NamedBladeSheathAlignment.Profile blade, MountedPart sheath,
            NamedBladeSheathAlignment.Fit fit) {
        if (fit == null || sheath.layers.base().size() + sheath.layers.luminous().size()
                > NamedBladeSheathAlignment.MAX_FACES) return null;
        var baked = new Layers(NamedBladeSheathAlignment.bake(sheath.layers.base(), sheath.transform, fit),
                NamedBladeSheathAlignment.bake(sheath.layers.luminous(), sheath.transform, fit));
        // Check the actual transformed faces, including interpolation between
        // original vertices, before replacing either native sheath pass.
        var shell = NamedBladeSheathAlignment.profile(baked.reference(), new Matrix4f(), true);
        return NamedBladeSheathAlignment.covers(blade, shell, blade.length() * .005F)
                ? views(baked, !sheath.named) : null;
    }

    private static Result views(Layers layers, boolean stockTexture) {
        var base = LegacyRenderSources.view("sheath", layers.base());
        var glow = LegacyRenderSources.view("sheath_luminous", layers.luminous());
        if (base == null && glow == null || base == null && !layers.base().isEmpty()
                || glow == null && !layers.luminous().isEmpty()) return null;
        return new Result(new LegacyRenderSources.Views(base, glow,
                LegacyRenderSources.triangleCount(base) + LegacyRenderSources.triangleCount(glow)), stockTexture);
    }

    private static Object identity(LegacyRenderSources.Source source) {
        return source == null ? null : source.identity();
    }

    static void clear() {
        PROFILES.clear();
        PAIRS.clear();
    }

    private NamedSheathRender() {}
}
