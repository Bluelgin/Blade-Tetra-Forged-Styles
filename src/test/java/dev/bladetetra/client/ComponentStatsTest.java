package dev.bladetetra.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ComponentStatsTest {
    @Test void bothLanguagesCoverWorkbenchEffectsAndBladeBreadcrumb() throws Exception {
        for (String language : new String[]{"zh_cn", "en_us"}) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/blade_tetra/lang/" + language + ".json"))).getAsJsonObject();
            assertTrue(json.has("tetra.holo.craft.modular_slashblade"));
            for (String name : new String[]{"draw", "just", "spirit", "guard", "rank", "toss"}) {
                String key = "stat.blade_tetra.component." + name;
                assertTrue(json.has(key), key);
                assertTrue(json.has(key + ".tooltip"), key);
                String.format(json.get(key + ".tooltip").getAsString(), "15", "2.0", "1", "50");
            }
            assertFalse(json.has("stat.blade_tetra.part.damage"));
        }
    }
    @Test void rollbackLeavesNoComponentHolosphereHooksOrRegistration() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/blade_tetra.mixins.json"));
        assertFalse(config.contains("HoloVariantDetailMixin"));
        assertFalse(config.contains("HoloPartStatsMixin"));
        String source = Files.readString(Path.of("src/main/java/dev/bladetetra/client/ComponentStats.java"));
        assertTrue(source.contains("WorkbenchStatsGui.addBar"));
        assertFalse(source.contains("TetraHoloStatsCompat"));
        assertFalse(source.contains("HoloPartContext"));
    }
}
