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
}
