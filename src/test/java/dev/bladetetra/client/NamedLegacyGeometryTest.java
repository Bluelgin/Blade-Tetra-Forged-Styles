package dev.bladetetra.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.bladetetra.client.LegacySourceMesh.Component;
import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyModelAdapter;
import dev.bladetetra.forging.LegacyModelAdapter.Axis;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Vertex;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NamedLegacyGeometryTest {
    @AfterEach
    void resetGeometryCaches() {
        LegacyRenderSources.clear();
        NamedBladeRenderPlan.clear();
    }

    @Test
    void clippingUsesAllSixCardinalDirectionsAndInterpolatesUv() {
        for (Axis axis : Axis.values()) for (boolean flip : List.of(false, true)) {
            var frame = LegacyMeshFrame.cardinal(axis, flip, axis == Axis.X ? Axis.Y : Axis.X);
            var along = frame.direction();
            var width = frame.width();
            Face face = triangle(vertex(new Vector3f(along).mul(-4)), vertex(new Vector3f(along).mul(4)),
                    vertex(new Vector3f(along).mul(4).add(width)));
            var clipped = LegacyFaceClipper.clip(List.of(face), frame::along, -10, 0);
            assertEquals(1, clipped.size(), axis + "/" + flip);
            var result = clipped.get(0);
            for (int i = 0; i < result.vertices.length; i++) {
                float distance = frame.along(result.vertices[i]);
                assertTrue(distance <= .0001F);
                if (Math.abs(distance) < .0001F) assertEquals(.5F, result.textureCoordinates[i].u, .0001F);
            }
        }
    }

    @Test
    void sourceIdentityAndSeparateChannelsArePreservedForMixedGroups() throws Exception {
        var model = parse("""
                o cutting_edge
                v -250 27 2
                v -30 27 2
                v -30 34 2
                f 1 2 3
                o trim
                v -245 24 2
                v -30 24 2
                v -30 25 2
                f 4 5 6
                o accents
                v -220 27 1
                v -30 27 1
                v -30 34 1
                f 7 8 9
                o fitting
                v -30 25 2
                v 50 27 2
                v 50 35 2
                f 10 11 12
                """);
        var rules = rules("""
                {"blade_groups":["cutting_edge","trim"],"blade_luminous_groups":["accents"],
                "hilt_groups":["fitting"],"blade_anchor":[-30,29,2]}
                """);
        var source = source(model, rules);
        assertEquals(LegacyRenderSources.Support.PARTIAL, source.support(Component.BLADE));
        assertEquals(LegacyRenderSources.Support.FULL, source.support(Component.HILT));
        assertEquals(LegacyRenderSources.Support.UNSUPPORTED, source.support(Component.SAYA));
        var views = LegacyRenderSources.views(source, Component.BLADE, "blade");
        assertEquals(2, LegacyRenderSources.triangleCount(views.base()));
        assertEquals(1, LegacyRenderSources.triangleCount(views.luminous()));
        assertSame(views, LegacyRenderSources.views(source, Component.BLADE, "blade"));
        LegacyRenderSources.clear();
        assertNotSame(views, LegacyRenderSources.views(source, Component.BLADE, "blade"));
    }

    @Test
    void fullHiltPreservesLengthAndTheSameMountWorksInThePoseStack() throws Exception {
        var source = source(model("/assets/slashblade/model/blade.obj"), LegacyModelRules.AUTOMATIC);
        float previousWorld = 0;
        for (String style : List.of("wakizashi", "orthodox", "katana", "nodachi")) {
            var target = target(style);
            var blade = assertMount(source, target, Component.BLADE, "blade");
            var hilt = assertMount(source, target, Component.HILT, "blade");
            assertEquals(1, hilt.scale(), .0001F);
            assertTrue(blade.target().origin().distance(hilt.target().origin()) < .0001F);
            assertTrue(blade.scale() > previousWorld);
            previousWorld = blade.scale();
            assertMount(source, target, Component.BLADE, "item_blade");
            assertMount(source, target, Component.BLADE, "item_bladens");
            assertMount(source, target, Component.BLADE, "item_damaged");
            assertMount(source, target, Component.SAYA, "sheath");
            assertMount(source, target, Component.SAYA, "item_blade");
        }
    }

    @Test
    void explicitAnchorAndCalibrationComposeInTargetCoordinates() {
        var from = LegacyMeshFrame.cardinal(Axis.Z, true, Axis.Y).at(new Vector3f(17, 5, 11));
        var to = LegacyMeshFrame.cardinal(Axis.X, false, Axis.Y).at(new Vector3f(7, 3, 2));
        var tweak = new LegacyCalibrationProfile.PartTransform(1.2F, .1F, .2F, 15);
        var mount = new LegacyMeshFrame.Mount(from, to, tweak, 1, 40);
        var point = mount.matrix().transformPosition(new Vector3f(from.origin()));
        assertEquals(11, point.x, .0001F);
        assertEquals(11, point.y, .0001F);
        assertEquals(2, point.z, .0001F);
        var poses = new PoseStack();
        mount.apply(poses);
        assertTrue(point.distance(poses.last().pose().transformPosition(new Vector3f(from.origin()))) < .0001F);
    }

    @Test
    void weightBudgetAndNegativeCacheHoldUntilReload() {
        var cache = new LegacyMeshCache<String, Integer>(4, 10, value -> value);
        assertEquals(8, cache.get("large", key -> 8));
        assertNull(cache.get("missing", key -> null));
        assertNull(cache.get("missing", key -> 3));
        assertEquals(4, cache.get("small", key -> 4));
        assertEquals(2, cache.get("large", key -> 2));
        cache.clear();
        assertEquals(3, cache.get("missing", key -> 3));
    }

    @Test
    void malformedFacesAndExcessiveTriangleCountsAreRejected() {
        WavefrontObject large = parse("""
                o blade
                v -250 0 0
                v -30 2 0
                v -30 3 0
                f 1 2 3
                """);
        var group = large.groupObjects.get(0);
        Face invalid = triangle(new Vertex(Float.NaN, 0, 0), new Vertex(1, 0, 0), new Vertex(0, 1, 0));
        group.faces.add(invalid);
        var accepted = source(large, rules("{\"blade_groups\":[\"blade\"]}"));
        assertEquals(1, LegacyRenderSources.geometry(accepted, Component.BLADE, "blade").base().size());
        group.faces.addAll(java.util.Collections.nCopies(LegacySourceMesh.MAX_TRIANGLES, group.faces.get(0)));
        assertThrows(IllegalArgumentException.class, () -> source(large, LegacyModelRules.AUTOMATIC));
    }

    @Test
    void crossSourceWorldSheathCoversTheMountedBladeWithoutMovingIt() throws Exception {
        var blade = source(model("/assets/slashblade/model/named/yamato.obj"), LegacyModelRules.AUTOMATIC);
        var sheath = source(model("/assets/slashblade/model/named/agito.obj"), LegacyModelRules.AUTOMATIC);
        var target = model("/assets/blade_tetra/model/modular/alpha9/orthodox_basic_simple_wrapped.obj");
        var mountedBlade = NamedBladeRenderPlan.mounted(blade, Component.BLADE, target, "blade");
        var mountedSheath = NamedBladeRenderPlan.mounted(sheath, Component.SAYA, target, "sheath");
        assertNotNull(mountedBlade);
        assertNotNull(mountedSheath);
        var edge = NamedBladeSheathAlignment.profile(
                LegacyRenderSources.geometry(blade, Component.BLADE, "blade").reference(),
                mountedBlade.mount().matrix(), false);
        var original = NamedBladeSheathAlignment.profile(
                LegacyRenderSources.geometry(sheath, Component.SAYA, "sheath").reference(),
                mountedSheath.mount().matrix(), true);
        assertFalse(NamedBladeSheathAlignment.covers(edge, original, edge.length() * .005F));
        long start = System.nanoTime();
        var combined = NamedSheathRender.resolve(blade, sheath, target);
        long miss = System.nanoTime() - start;
        assertNotNull(combined);
        assertFalse(combined.stockTexture(), "the real mixed fixture should keep the named sheath");
        var fitted = NamedBladeSheathAlignment.profile(combined.views().base().groupObjects.get(0).faces,
                new org.joml.Matrix4f(), true);
        assertTrue(NamedBladeSheathAlignment.covers(edge, fitted, edge.length() * .005F));
        start = System.nanoTime();
        for (int attempt = 0; attempt < 10_000; attempt++) {
            assertSame(combined, NamedSheathRender.resolve(blade, sheath, target));
        }
        System.out.printf(java.util.Locale.ROOT, "Mixed sheath cache miss %.3f ms, 10,000 hits %.3f ms%n",
                miss / 1_000_000.0, (System.nanoTime() - start) / 1_000_000.0);
        assertSame(mountedBlade, NamedBladeRenderPlan.mounted(blade, Component.BLADE, target, "blade"));
        NamedBladeRenderPlan.clear();
        assertNotSame(combined, NamedSheathRender.resolve(blade, sheath, target));
    }

    @Test
    void incompatibleSheathUsesVerifiedModularGeometry() throws Exception {
        var incompatible = source(model("/assets/slashblade/model/named/agito.obj"),
                rules("{\"saya_transform\":{\"rotation\":75}}"));
        var target = target("orthodox");
        var selected = NamedBladeRenderPlan.mounted(incompatible, Component.SAYA, target, "sheath");
        assertNotNull(selected);
        var result = NamedSheathRender.resolve(null, incompatible, target);
        assertNotNull(result);
        assertTrue(result.stockTexture());
        assertNotNull(result.views().base());
    }

    @Test
    void missingMeshesFallBackWithoutReplacingTheStockPass() throws Exception {
        var missing = new LegacyRenderSources.Source(null, 0, "missing",
                LegacyRenderSources.Support.MISSING_RESOURCE);
        var target = target("orthodox");
        assertNull(NamedSheathRender.resolve(missing, missing, target));
        assertNull(NamedSheathRender.resolve(missing, null, target));
    }

    @Test
    void optionalSjapModelsUseActualProviderGeometry() throws Exception {
        String fixturePath = System.getProperty("blade_tetra.sjapFixtures");
        org.junit.jupiter.api.Assumptions.assumeTrue(fixturePath != null);
        var target = target("katana");
        for (String file : List.of("toyako/toyako.obj", "murakumo/model.obj", "kirisaya/kirisaya.obj",
                "fluorescentbar/fluorescent_bar.obj", "murasama/murasama.obj", "darkraven/dark_raven.obj")) {
            Path override = Path.of("src/main/resources/assets/slashblade_addon/bladetetra/legacy_models/model/" + file + ".json");
            var rules = Files.exists(override) ? rules(Files.readString(override)) : LegacyModelRules.AUTOMATIC;
            Path path = Path.of(fixturePath).resolve(file);
            try (var stream = Files.newInputStream(path)) {
                var source = source(new WavefrontObject(file, stream), rules);
                assertMount(source, target, Component.BLADE, "blade");
                if (file.startsWith("toyako")) {
                    assertEquals(LegacyRenderSources.Support.UNSUPPORTED, source.support(Component.SAYA));
                } else {
                    assertMount(source, target, Component.SAYA, "sheath");
                }
                if (file.startsWith("murakumo")) {
                    assertTrue(source.mesh().separateHilt);
                    assertMount(source, target, Component.HILT, "blade");
                    var icon = assertMount(source, target, Component.HILT, "item_blade");
                    var faces = LegacyRenderSources.geometry(source, Component.HILT, "item_blade").reference();
                    var matrix = icon.matrix();
                    for (Face face : faces) for (Vertex v : face.vertices) {
                        var point = matrix.transformPosition(new Vector3f(v.x, v.y, v.z));
                        assertTrue(Math.abs(point.x) <= 100 && Math.abs(point.y) <= 100,
                                "Murakumo's grip must fit inside the normal Tetra icon envelope");
                    }
                }
                if (file.startsWith("kirisaya")) {
                    var views = LegacyRenderSources.views(source, Component.BLADE, "blade");
                    assertNull(views.base());
                    assertNotNull(views.luminous());
                }
                if (file.startsWith("fluorescentbar")) {
                    var views = LegacyRenderSources.views(source, Component.BLADE, "blade");
                    assertNotNull(views.base());
                    assertNotNull(views.luminous());
                }
            }
        }
    }

    private static LegacyMeshFrame.Mount assertMount(LegacyRenderSources.Source source, WavefrontObject target,
            Component part, String group) {
        var result = NamedBladeRenderPlan.mounted(source, part, target, group);
        assertNotNull(result, () -> target + "/" + part + "/" + group + " " + source.support(part));
        var mount = result.mount();
        var mapped = mount.matrix().transformPosition(new Vector3f(mount.source().origin()));
        assertTrue(mapped.distance(mount.target().origin()) < .001F);
        return mount;
    }

    private static LegacyRenderSources.Source source(WavefrontObject model, LegacyModelRules rules) {
        return new LegacyRenderSources.Source(new LegacySourceMesh(model, rules, LegacyCalibrationProfile.DEFAULT),
                LegacyRenderSources.triangleCount(model), "", null);
    }

    private static LegacyModelRules rules(String json) {
        return new LegacyModelRules(JsonParser.parseString(json).getAsJsonObject(), LegacyModelAdapter.STANDARD);
    }

    private static WavefrontObject target(String style) throws Exception {
        return model("/assets/blade_tetra/model/modular/alpha9/" + style + "_basic_simple_stable.obj");
    }

    private static WavefrontObject model(String resource) throws Exception {
        try (var stream = NamedLegacyGeometryTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new WavefrontObject(resource, stream);
        }
    }

    private static WavefrontObject parse(String obj) {
        return new WavefrontObject("fixture", new ByteArrayInputStream(obj.getBytes(StandardCharsets.UTF_8)));
    }

    private static Face triangle(Vertex a, Vertex b, Vertex c) {
        Face face = new Face();
        face.vertices = new Vertex[]{a, b, c};
        face.textureCoordinates = new TextureCoordinate[]{new TextureCoordinate(0, 0),
                new TextureCoordinate(1, 0), new TextureCoordinate(1, 1)};
        return face;
    }

    private static Vertex vertex(Vector3f vector) {
        return new Vertex(vector.x, vector.y, vector.z);
    }
}
