package dev.bladetetra.forging;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    void playerGuideMirrorsCatalogOrderAndAbilityKeys() {
        var definitions = LegacyFusionCatalog.values();
        var entries = LegacyFusionGuide.entries(definitions, Component::literal);

        assertEquals(definitions.size(), entries.size());
        for (int index = 0; index < definitions.size(); index++) {
            var definition = definitions.get(index);
            var entry = entries.get(index);
            assertEquals(definition.id(), entry.id());

            var translation = assertInstanceOf(TranslatableContents.class,
                    entry.abilityName().getContents());
            String prefix = definition.abilityType()
                    == LegacyFusionDefinition.AbilityType.SLASH_ART
                    ? "slash_art."
                    : "se.";
            assertEquals(prefix + definition.ability().getNamespace() + "."
                    + definition.ability().getPath(), translation.getKey());
        }
    }
}
