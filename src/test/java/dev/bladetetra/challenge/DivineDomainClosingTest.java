package dev.bladetetra.challenge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DivineDomainClosingTest {
    @Test void NewUiAndAfterwordArePresentInBothLanguages() throws Exception {
        for (String language : new String[]{"zh_cn", "en_us"}) {
            var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/blade_tetra/lang/" + language + ".json"))).getAsJsonObject();
            for (String key : new String[]{"screen.blade_tetra.divine.enter", "screen.blade_tetra.divine.title", "screen.blade_tetra.divine.detail",
                    "message.blade_tetra.divine.fire_ready", "message.blade_tetra.divine.fire_rescue", "message.blade_tetra.divine.afterword",
                    "hud.blade_tetra.divine.fire_ready", "hud.blade_tetra.divine.fire_active", "hud.blade_tetra.divine.fire_spent",
                    "lore.blade_tetra.divine.afterword.title", "lore.blade_tetra.divine.afterword.page.1", "lore.blade_tetra.divine.afterword.page.2"}) {
                assertTrue(lang.has(key), language + ": " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), key);
            }
        }
    }
    @Test void hazardsAreNotDirectMeleeAndPlayerExitEndsOnlyTheirVisual() throws Exception {
        String phases = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/DivineDomainFinisher.java"));
        assertTrue(phases.contains("typeHolder(), null, boss"));
        assertFalse(phases.contains("bindingEnd"));
        String manager = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/MikageDivineSupportManager.java"));
        assertTrue(manager.contains("rescueTargets.entrySet().removeIf"));
        assertTrue(manager.contains("effect(\"rescue_end\""));
        String book = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/DivineDomainAfterword.java"));
        assertTrue(book.contains("persisted.getBoolean(RECEIVED)"));
        assertTrue(book.contains("player.drop(book, false)"));
    }
}
