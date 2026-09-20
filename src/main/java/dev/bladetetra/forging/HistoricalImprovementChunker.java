package dev.bladetetra.forging;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits the legacy named-imprint improvement array before Tetra/MUtil syncs it.
 *
 * <p>MUtil serializes every Tetra data resource as one UTF string. Minecraft
 * caps a single UTF value at 32767 characters, and MUtil also appends a
 * {@code sources} field to every array element before synchronization. A large
 * named-blade addon matrix can therefore make one otherwise-valid improvement
 * resource impossible to encode.</p>
 */
final class HistoricalImprovementChunker {
    /**
     * Keep each estimated synchronized JSON value comfortably below Minecraft's
     * 32767-character writeUtf ceiling.
     */
    static final int SYNC_BUDGET_CHARS = 24_000;

    /**
     * Conservative allowance for MUtil's injected per-entry sources metadata.
     * The Blade Tetra generated-pack source id is much shorter than this reserve.
     */
    static final int SOURCE_OVERHEAD_RESERVE_CHARS = 96;

    private HistoricalImprovementChunker() {}

    static List<JsonArray> chunk(List<JsonObject> improvements) {
        Objects.requireNonNull(improvements, "improvements");

        List<JsonArray> chunks = new ArrayList<>();
        JsonArray current = new JsonArray();
        int estimatedChars = 2; // []

        for (JsonObject improvement : improvements) {
            Objects.requireNonNull(improvement, "historical improvement");
            String serialized = improvement.toString();
            int standaloneEstimate = 2 + serialized.length() + SOURCE_OVERHEAD_RESERVE_CHARS;
            if (standaloneEstimate > SYNC_BUDGET_CHARS) {
                throw new IllegalArgumentException(
                        "single historical improvement exceeds sync-safe budget");
            }

            int addition = serialized.length() + SOURCE_OVERHEAD_RESERVE_CHARS
                    + (current.size() == 0 ? 0 : 1); // comma
            if (current.size() > 0 && estimatedChars + addition > SYNC_BUDGET_CHARS) {
                chunks.add(current);
                current = new JsonArray();
                estimatedChars = 2;
                addition = serialized.length() + SOURCE_OVERHEAD_RESERVE_CHARS;
            }

            current.add(improvement);
            estimatedChars += addition;
        }

        if (current.size() > 0 || chunks.isEmpty()) {
            chunks.add(current);
        }
        return List.copyOf(chunks);
    }

    static int estimatedSyncChars(JsonArray chunk) {
        Objects.requireNonNull(chunk, "chunk");
        int estimated = 2;
        for (int i = 0; i < chunk.size(); i++) {
            if (i > 0) estimated += 1;
            estimated += chunk.get(i).toString().length() + SOURCE_OVERHEAD_RESERVE_CHARS;
        }
        return estimated;
    }
}
