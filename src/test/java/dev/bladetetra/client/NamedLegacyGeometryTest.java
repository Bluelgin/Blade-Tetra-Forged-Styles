package dev.bladetetra.client;

import dev.bladetetra.forging.LegacyModelAnalysis;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class NamedLegacyGeometryTest {
    @Test void everyBundledModelProducesRealGuardGripAndSayaFaces() throws Exception {
        for (String file : java.util.List.of("named/sange/sange.obj", "named/dios/dios.obj",
                "named/agito.obj", "named/yamato.obj", "named/muramasa/muramasa.obj",
                "named/yasha/yasha.obj", "named/yasha/yasha_true.obj", "blade.obj")) {
            try (InputStream input = getClass().getResourceAsStream("/assets/slashblade/model/" + file)) {
                assertNotNull(input, file);
                byte[] bytes = input.readAllBytes();
                var analysis = LegacyModelAnalysis.analyze(new InputStreamReader(
                        new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
                var model = new WavefrontObject(file, new ByteArrayInputStream(bytes));
                var result = LegacyModelPartRenderer.validateModel(model, analysis.profile());
                assertTrue(result.valid(), file + ": " + result);
                assertTrue(LegacyModelPartRenderer.supportsAutomaticHiltModel(model, analysis.profile()), file);
                try (InputStream ironInput = getClass().getResourceAsStream(
                        "/assets/blade_tetra/model/modular/alpha9/katana_basic_simple_stable.obj")) {
                    assertNotNull(ironInput);
                    var iron = new WavefrontObject("iron", ironInput);
                    for (String part : java.util.List.of("TSUBA", "TSUKA", "SAYA", "HILT")) {
                        var mount = LegacyModelPartRenderer.inspectMount(model, iron, analysis.profile(), part);
                        assertNotNull(mount, file + "/" + part);
                        assertTrue(Float.isFinite(mount.lengthScale()) && mount.lengthScale() > 0);
                        assertTrue(Float.isFinite(mount.widthScale()) && mount.widthScale() > 0);
                        var tweak = new dev.bladetetra.forging.LegacyCalibrationProfile.PartTransform(1.2F, 0, 0, 7, 1.3F, .8F);
                        var root = mount.point(mount.sourceX(), mount.sourceY(), mount.sourceZ(), tweak);
                        assertEquals(mount.targetX(), root.x, .0001F, file + "/" + part + " anchor X");
                        assertEquals(mount.targetY(), root.y, .0001F, file + "/" + part + " anchor Y");
                        assertEquals(mount.targetZ(), root.z, .0001F, file + "/" + part + " anchor Z");
                    }
                }
            }
        }
    }
    @Test void namedBladeRootFollowsTheAuthoredSocketAcrossLengths() throws Exception {
        String path = "/assets/slashblade/model/named/sange/sange.obj";
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            bytes = stream.readAllBytes();
        }
        var profile = LegacyModelAnalysis.analyze(new InputStreamReader(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)).profile();
        var source = new WavefrontObject("sange", new ByteArrayInputStream(bytes));
        var part = LegacyModelPartRenderer.Part.BLADE;
        var sourceIcon = LegacyModelPartRenderer.iconSpan(source, "item_blade", part, profile, false);
        assertNotNull(sourceIcon);
        float previousWorldLength = 0, previousIconLength = 0;
        for (String style : java.util.List.of("wakizashi", "orthodox", "katana", "nodachi")) {
            String filename = "/assets/blade_tetra/model/modular/alpha9/" + style + "_basic_simple_stable.obj";
            try (var stream = getClass().getResourceAsStream(filename)) {
                assertNotNull(stream, filename);
                var target = new WavefrontObject(style, stream);
                var collar = LegacyModelPartRenderer.modularFaces(target, "blade",
                        LegacyModelPartRenderer.Part.TSUBA);
                double front = collar.stream().flatMap(face -> java.util.Arrays.stream(face.vertices))
                        .mapToDouble(vertex -> vertex.x).min().orElseThrow();
                var mount = LegacyModelPartRenderer.inspectMount(source, target, profile, "BLADE");
                assertNotNull(mount, style);
                assertEquals(mount.sourceMaxX(), mount.sourceX(), .0001F, style + " source uses the blade root");
                assertTrue(mount.targetX() >= front && mount.targetX() <= front + 6,
                        style + " blade root must seat inside the ordinary collar");
                assertTrue(mount.lengthScale() > previousWorldLength, style + " world blade length");
                previousWorldLength = mount.lengthScale();

                var targetIcon = LegacyModelPartRenderer.iconSpan(target, "item_blade", part,
                        dev.bladetetra.forging.LegacyCalibrationProfile.DEFAULT, true);
                assertNotNull(targetIcon, style);
                var poses = new com.mojang.blaze3d.vertex.PoseStack();
                LegacyModelPartRenderer.applyIconMount(poses, source, target, "item_blade", part, profile);
                var fitted = poses.last().pose().transformPosition(new org.joml.Vector3f(sourceIcon.root()));
                assertTrue(fitted.distance(targetIcon.root()) < .001F, style + " icon socket");
                assertTrue(targetIcon.length() > previousIconLength, style + " icon blade length");
                previousIconLength = targetIcon.length();
            }
        }
    }
}
