package dev.bladetetra.forging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void parsesCoupledSlashArtAndSpecialEffectWithoutCappingTheSchema() {
        var definition = LegacyFusionCatalog.parse(new StringReader("""
                [
                  {"id":"dead","saya":"a","hilt":"b","improvement":"i/dead",
                   "slashArt":"blade_tetra:blood_cherry_final_scene",
                   "specialEffects":["blade_tetra:life_erosion"],"priority":200}
                ]
                """)).get(0);

        assertEquals("blade_tetra:blood_cherry_final_scene", definition.slashArt().toString());
        assertEquals(List.of("blade_tetra:life_erosion"),
                definition.specialEffects().stream().map(Object::toString).toList());
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
    void playerGuideMirrorsCatalogOrderAndAllAbilityKeys() {
        var definitions = LegacyFusionCatalog.values();
        var entries = LegacyFusionGuide.entries(definitions, Component::literal);

        assertEquals(definitions.size(), entries.size());
        for (int index = 0; index < definitions.size(); index++) {
            var definition = definitions.get(index);
            var entry = entries.get(index);
            assertEquals(definition.id(), entry.id());
            List<String> actual = translationKeys(entry.abilityName());
            List<String> expected = new ArrayList<>();
            if (definition.slashArt() != null) {
                expected.add("slash_art." + definition.slashArt().getNamespace()
                        + "." + definition.slashArt().getPath());
            }
            for (var effect : definition.specialEffects()) {
                expected.add("se." + effect.getNamespace() + "." + effect.getPath());
            }
            assertEquals(expected, actual);
        }
    }

    private static List<String> translationKeys(Component component) {
        List<String> result = new ArrayList<>();
        collectTranslationKeys(component, result);
        return result;
    }

    private static void collectTranslationKeys(Component component, List<String> output) {
        if (component.getContents() instanceof TranslatableContents translation) {
            output.add(translation.getKey());
        }
        component.getSiblings().forEach(child -> collectTranslationKeys(child, output));
    }

    @Test
    void everyFusionHasAWorkbenchImprovementAndAttunementSchematic() throws Exception {
        JsonObject improvementIndex = new JsonObject();
        try (var stream = getClass().getResourceAsStream(
                "/data/tetra/improvements/slashblade/blade/legacy_fusions.json")) {
            assertNotNull(stream);
            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonArray().forEach(element -> improvementIndex.add(
                            element.getAsJsonObject().get("key").getAsString(), element));
        }

        for (var definition : LegacyFusionCatalog.values()) {
            assertTrue(improvementIndex.has(definition.improvement()),
                    "missing Tetra improvement " + definition.improvement());
            String path = "/data/tetra/schematics/slashblade/legacy_fusion/"
                    + definition.id() + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, "missing attunement schematic " + path);
                JsonObject schematic = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var requirements = schematic.getAsJsonObject("requirement")
                        .getAsJsonArray("requirements");
                assertTrue(requirements.toString().contains(definition.id()),
                        "schematic does not require fusion " + definition.id());
                assertTrue(schematic.getAsJsonArray("outcomes").get(0)
                                .getAsJsonObject().getAsJsonObject("improvements")
                                .has(definition.improvement()),
                        "schematic does not apply " + definition.improvement());
            }
        }
    }
}
