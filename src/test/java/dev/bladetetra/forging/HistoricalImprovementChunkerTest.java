package dev.bladetetra.forging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalImprovementChunkerTest {
    private static final int MINECRAFT_UTF_LIMIT = 32_767;

    @Test
    void largeAddonMatrixIsSplitIntoSyncSafeResourcesWithoutLosingKeys() {
        List<JsonObject> improvements = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            JsonObject improvement = new JsonObject();
            improvement.addProperty("key",
                    "blade_tetra/legacy_auto/test_addon/very_long_named_blade_" + i);
            improvement.addProperty("level", 1);
            improvements.add(improvement);
        }

        List<JsonArray> chunks = HistoricalImprovementChunker.chunk(improvements);

        assertTrue(chunks.size() > 1);
        assertEquals(improvements.size(), chunks.stream().mapToInt(JsonArray::size).sum());

        List<String> actualKeys = new ArrayList<>();
        for (JsonArray chunk : chunks) {
            assertTrue(HistoricalImprovementChunker.estimatedSyncChars(chunk)
                    <= HistoricalImprovementChunker.SYNC_BUDGET_CHARS);
            assertTrue(simulatedMutilPayloadChars(chunk) < MINECRAFT_UTF_LIMIT);
            chunk.forEach(element -> actualKeys.add(
                    element.getAsJsonObject().get("key").getAsString()));
        }

        List<String> expectedKeys = improvements.stream()
                .map(entry -> entry.get("key").getAsString())
                .toList();
        assertEquals(expectedKeys, actualKeys);
    }

    @Test
    void emptyCatalogStillProducesOneValidEmptyResource() {
        List<JsonArray> chunks = HistoricalImprovementChunker.chunk(List.of());

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).size());
        assertTrue(HistoricalImprovementChunker.estimatedSyncChars(chunks.get(0))
                <= HistoricalImprovementChunker.SYNC_BUDGET_CHARS);
    }

    private static int simulatedMutilPayloadChars(JsonArray chunk) {
        JsonArray synchronizedCopy = JsonParser.parseString(chunk.toString()).getAsJsonArray();
        for (var element : synchronizedCopy) {
            JsonArray sources = new JsonArray();
            sources.add("blade_tetra_named_patterns");
            element.getAsJsonObject().add("sources", sources);
        }
        return synchronizedCopy.toString().length();
    }
}
