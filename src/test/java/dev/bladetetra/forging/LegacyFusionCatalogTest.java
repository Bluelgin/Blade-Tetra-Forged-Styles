package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyFusionCatalogTest {
    @Test
    void parsesAndOrdersDefinitionsByPriority() {
        var definitions = LegacyFusionCatalog.parse(new StringReader("""
                [
                  {"id":"low","saya":"a","hilt":"b","improvement":"i/low",
                   "abilityType":"special_effect","ability":"blade_tetra:low","priority":1},
                  {"id":"high","saya":"c","hilt":"d","improvement":"i/high",
                   "abilityType":"slash_art","ability":"blade_tetra:high","priority":20}
                ]
                """));

        assertEquals("high", definitions.get(0).id());
        assertEquals("low", definitions.get(1).id());
        assertEquals(LegacyFusionDefinition.AbilityType.SLASH_ART,
                definitions.get(0).abilityType());
    }

    @Test
    void rejectsDuplicateIdsAndOrderedPairs() {
        assertThrows(IllegalArgumentException.class, () ->
                LegacyFusionCatalog.parse(new StringReader("""
                        [
                          {"id":"same","saya":"a","hilt":"b","improvement":"i/1",
                           "abilityType":"slash_art","ability":"blade_tetra:one"},
                          {"id":"same","saya":"c","hilt":"d","improvement":"i/2",
                           "abilityType":"slash_art","ability":"blade_tetra:two"}
                        ]
                        """)));
        assertThrows(IllegalArgumentException.class, () ->
                LegacyFusionCatalog.parse(new StringReader("""
                        [
                          {"id":"one","saya":"a","hilt":"b","improvement":"i/1",
                           "abilityType":"slash_art","ability":"blade_tetra:one"},
                          {"id":"two","saya":"a","hilt":"b","improvement":"i/2",
                           "abilityType":"special_effect","ability":"blade_tetra:two"}
                        ]
                        """)));
    }
}
