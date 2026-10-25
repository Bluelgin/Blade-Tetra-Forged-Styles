package dev.bladetetra.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The renderer uses authored indices, not texture-guessing. Catch changed
 * artwork UV/tile assignments before a release silently samples another soul.
 */
class AwakenedArtContractTest {
    private static final Map<String, Integer> KNOWN_TILES = Map.ofEntries(
            Map.entry("akatsuki/crescent", 19),
            Map.entry("akatsuki/blade_trace", 22),
            Map.entry("akatsuki/execution_cross", 21),
            Map.entry("akatsuki/true_name_seal", 23),
            Map.entry("kyouka/water_mirror", 0),
            Map.entry("kyouka/mirror_cracked", 1),
            Map.entry("kyouka/replay_cut", 3),
            Map.entry("kyouka/true_name_seal", 12),
            Map.entry("senbonzakura/blade_small", 0),
            Map.entry("senbonzakura/blade_medium", 1),
            Map.entry("senbonzakura/phantom_blade", 2),
            Map.entry("senbonzakura/falling_petal", 5),
            Map.entry("senbonzakura/array_core", 6),
            Map.entry("senbonzakura/true_name_seal", 7),
            Map.entry("raikiri/step_ribbon", 3),
            Map.entry("raikiri/direction_arc", 4),
            Map.entry("raikiri/landing_pulse", 6),
            Map.entry("raikiri/piercing_light", 8),
            Map.entry("raikiri/true_name_seal", 9));

    @Test
    void everyRuntimeTileAgreesWithAstrasEditableArtManifest() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(
                Path.of("docs/awakened-rework/assets.json"))).getAsJsonObject();
        Map<String, JsonObject> byId = new HashMap<>();
        for (JsonElement e : root.getAsJsonArray("assets")) {
            JsonObject asset = e.getAsJsonObject();
            byId.put(asset.get("id").getAsString(), asset);
        }
        assertEquals(62, byId.size(), "unexpected art manifest schema");
        for (var entry : KNOWN_TILES.entrySet()) {
            JsonObject a = byId.get(entry.getKey());
            assertNotNull(a, entry.getKey() + " missing");
            int columns = a.getAsJsonArray("canvas_size").get(0).getAsInt() / 128;
            var rect = a.getAsJsonArray("rect");
            assertEquals(128, rect.get(2).getAsInt());
            assertEquals(128, rect.get(3).getAsInt());
            assertEquals(entry.getValue().intValue(),
                    rect.get(0).getAsInt() / 128
                            + columns * (rect.get(1).getAsInt() / 128),
                    entry.getKey() + " tile moved: update VFX UV contract");
            assertTrue(a.get("texture").getAsString()
                    .startsWith("blade_tetra:textures/effect/awakened/"));
        }
    }
}
