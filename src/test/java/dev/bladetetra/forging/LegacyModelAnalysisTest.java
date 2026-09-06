package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class LegacyModelAnalysisTest {
    @Test void allBundledNamedModelFamiliesCanBePartitioned() throws Exception {
        for (String path : java.util.List.of("sange/sange.obj", "dios/dios.obj", "agito.obj",
                "yamato.obj", "muramasa/muramasa.obj", "yasha/yasha.obj", "yasha/yasha_true.obj",
                "../blade.obj")) {
            String resource = path.equals("../blade.obj") ? "/assets/slashblade/model/blade.obj"
                    : "/assets/slashblade/model/named/" + path;
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, path);
                var result = LegacyModelAnalysis.analyze(new InputStreamReader(input, StandardCharsets.UTF_8));
                assertTrue(result.usable(), path);
                assertTrue(result.saya(), path);
                assertTrue(result.profile().tsubaMax() <= result.profile().tsukaMin() + .00001F, path);
            }
        }
    }
    @Test void basicAndBrokenBladesAreNotResearchTargets() {
        assertTrue(NamedLegacyCatalog.excluded("rodai_netherite"));
        assertTrue(NamedLegacyCatalog.excluded("yamato_broken"));
        assertTrue(NamedLegacyCatalog.excluded("sabigatana"));
        assertFalse(NamedLegacyCatalog.excluded("yamato"));
        assertFalse(NamedLegacyCatalog.excluded("fox_black"));
    }

    @Test void configuredLuminousGroupsAndAxisAreAnalyzedWithoutRenamingProviderAssets() throws Exception {
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
}
