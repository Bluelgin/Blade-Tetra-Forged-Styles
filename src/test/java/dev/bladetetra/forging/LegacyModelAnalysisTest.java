package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModelAnalysisTest {
    @Test
    void allBundledNamedModelFamiliesCanBePartitioned() throws Exception {
        for (String path : java.util.List.of("sange/sange.obj", "dios/dios.obj", "agito.obj",
                "yamato.obj", "muramasa/muramasa.obj", "yasha/yasha.obj", "yasha/yasha_true.obj",
                "../blade.obj")) {
            String resource = path.equals("../blade.obj") ? "/assets/slashblade/model/blade.obj"
                    : "/assets/slashblade/model/named/" + path;
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, path);
                var result = LegacyModelAnalysis.analyze(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                assertTrue(result.usable(), path);
                assertTrue(result.saya(), path);
                assertTrue(result.profile().tsubaMax()
                        <= result.profile().tsukaMin() + .00001F, path);
            }
        }
    }

    @Test
    void basicAndBrokenBladesAreNotResearchTargets() {
        assertTrue(NamedLegacyCatalog.excluded("rodai_netherite"));
        assertTrue(NamedLegacyCatalog.excluded("yamato_broken"));
        assertTrue(NamedLegacyCatalog.excluded("sabigatana"));
        assertFalse(NamedLegacyCatalog.excluded("yamato"));
        assertFalse(NamedLegacyCatalog.excluded("fox_black"));
    }

    @Test
    void configuredLuminousGroupsAndAxisAreAnalyzedWithoutRenamingProviderAssets()
            throws Exception {
        String obj = """
                v 0 0 0
                v 0 1 0
                v 0 0 1
                v 0 8 0
                v 1 8 0
                v 0 8 1
                v 0 10 0
                v 3 10 0
                v 0 10 3
                g blade_luminous
                f 1 2 3
                f 4 5 6
                f 7 8 9
                g sheath_luminous
                f 1 2 3
                """;
        var adapter = new LegacyModelAdapter("test", java.util.List.of("blade_luminous"),
                java.util.List.of(), java.util.List.of("sheath_luminous"),
                LegacyModelAdapter.Axis.Y, false, null, null, null, null, null, null);
        var result = LegacyModelAnalysis.analyze(new StringReader(obj), adapter);
        assertTrue(result.usable());
        assertTrue(result.saya());
        assertTrue(result.profile().tsubaCenter() > .55F);
    }

    @Test
    void radialBoundaryUsesAxesPerpendicularToConfiguredBladeAxis() throws Exception {
        String obj = """
                v 0 0 0
                v 0 10 0
                v 0 5 0
                v 10 6 0
                v 0 9 0
                v 0 6 0
                g blade
                f 1 2 3
                g handle
                f 4 5 6
                g sheath
                f 1 2 3
                """;
        var adapter = new LegacyModelAdapter("axis_y", java.util.List.of("blade"),
                java.util.List.of("handle"), java.util.List.of("sheath"),
                LegacyModelAdapter.Axis.Y, false, null, null, null, null, null, null);

        var result = LegacyModelAnalysis.analyze(new StringReader(obj), adapter);
        assertTrue(result.usable());
        assertTrue(result.profile().tsubaCenter() > .55F
                && result.profile().tsubaCenter() < .70F,
                "Y-axis analysis should use X/Z radial distance and choose the y=6 guard vertex");
    }

    @Test
    void malformedFaceIndicesAreQuarantinedInsteadOfThrowing() throws Exception {
        String obj = """
                v 0 0 0
                v 1 0 0
                v 0 1 0
                g blade
                f 1 2 999999
                g sheath
                f 1 2 3
                """;
        var result = LegacyModelAnalysis.analyze(new StringReader(obj));
        assertFalse(result.usable());
    }

    @Test
    void nonFiniteGeometryIsRejected() throws Exception {
        String obj = """
                v NaN 0 0
                v 1 0 0
                v 0 1 0
                g blade
                f 1 2 3
                g sheath
                f 1 2 3
                """;
        assertFalse(LegacyModelAnalysis.analyze(new StringReader(obj)).usable());
    }

    @Test
    void oversizedLinesAndFacesHitTheAnalysisBudget() throws Exception {
        String oversizedLine = "v 0 0 " + "0".repeat(LegacyModelAnalysis.MAX_LINE_LENGTH);
        assertFalse(LegacyModelAnalysis.analyze(new StringReader(oversizedLine)).usable());

        StringBuilder face = new StringBuilder("v 0 0 0\nv 1 0 0\nv 0 1 0\ng blade\nf");
        for (int i = 0; i < LegacyModelAnalysis.MAX_FACE_VERTICES + 1; i++) {
            face.append(" 1");
        }
        face.append("\ng sheath\nf 1 2 3\n");
        assertFalse(LegacyModelAnalysis.analyze(new StringReader(face.toString())).usable());
    }
}
