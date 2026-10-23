package dev.bladetetra.customstyle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Public, provider-neutral community import/export boundary.
 *
 * Share codes are portable data, NOT permissions or executable scripts.
 * JSON is used independently of Tetra's internal ScrollData codec so that
 * websites and future community tools can adopt the documented v1 format.
 * ALWAYS call importValidated before minting a scroll from untrusted data.
 */
public final class CustomStyleExchange {
    public static final String FORMAT = "blade_tetra:custom_style";
    public static final int SHARE_VERSION = 1;
    public static final String PREFIX = "BTSTYLE1:";
    public static final int MAX_CODE_LENGTH = 4096;
    private static final int MAX_JSON_BYTES = 2048;

    public enum Status {
        OK, INVALID_FORMAT, UNSUPPORTED_VERSION, INVALID_CONTENT, UNAVAILABLE_ACTION
    }

    public record ImportResult(Status status, CustomStyleSpec style) {
        public boolean accepted() {
            return status == Status.OK && style != null;
        }
    }

    /** Stable v1 export. Source mod registry IDs are preserved in-order. */
    public static String export(CustomStyleSpec style) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("version", SHARE_VERSION);
        json.addProperty("name", style.name());
        JsonArray actions = new JsonArray();
        for (ResourceLocation id : style.actions()) actions.add(id.toString());
        json.add("actions", actions);
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates structure AND installed ordinary attack permissions at import
     * time. All consumers that grant a scroll must use this result.
     */
    public static ImportResult importValidated(String code) {
        ImportResult parsed = inspect(code);
        if (!parsed.accepted()) return parsed;
        return ComboCompatibilityResolver.playable(parsed.style())
                ? parsed : new ImportResult(Status.UNAVAILABLE_ACTION, null);
    }

    /** Parse-only for external previews. NEVER mint a scroll using this alone. */
    public static ImportResult inspect(String code) {
        if (code == null || code.length() > MAX_CODE_LENGTH || !code.startsWith(PREFIX)) {
            return reject(Status.INVALID_FORMAT);
        }
        String body = code.substring(PREFIX.length());
        if (body.isEmpty()) return reject(Status.INVALID_FORMAT);
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(body);
            if (bytes.length > MAX_JSON_BYTES) return reject(Status.INVALID_CONTENT);
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return reject(Status.INVALID_CONTENT);
            JsonObject json = parsed.getAsJsonObject();
            if (!stringField(json, "format") || !FORMAT.equals(json.get("format").getAsString())) {
                return reject(Status.INVALID_FORMAT);
            }
            if (!json.has("version") || !json.get("version").isJsonPrimitive()
                    || !json.get("version").getAsJsonPrimitive().isNumber()) {
                return reject(Status.INVALID_CONTENT);
            }
            if (json.get("version").getAsInt() != SHARE_VERSION) {
                return reject(Status.UNSUPPORTED_VERSION);
            }
            if (!stringField(json, "name") || !json.has("actions")
                    || !json.get("actions").isJsonArray()) {
                return reject(Status.INVALID_CONTENT);
            }
            String name = json.get("name").getAsString();
            if (name.isBlank() || name.length() > CustomStyleSpec.MAX_NAME_LENGTH
                    || name.chars().anyMatch(Character::isISOControl)) {
                return reject(Status.INVALID_CONTENT);
            }
            JsonArray ids = json.getAsJsonArray("actions");
            if (ids.isEmpty() || ids.size() > CustomStyleSpec.MAX_ACTIONS) {
                return reject(Status.INVALID_CONTENT);
            }
            List<ResourceLocation> actions = new ArrayList<>(ids.size());
            for (JsonElement element : ids) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    return reject(Status.INVALID_CONTENT);
                }
                String raw = element.getAsString();
                if (raw.length() > 128) return reject(Status.INVALID_CONTENT);
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) return reject(Status.INVALID_CONTENT);
                actions.add(id);
            }
            return new ImportResult(Status.OK, new CustomStyleSpec(name, actions));
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException exception) {
            return reject(Status.INVALID_CONTENT);
        }
    }

    private static boolean stringField(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive()
                && json.get(name).getAsJsonPrimitive().isString();
    }

    private static ImportResult reject(Status status) {
        return new ImportResult(status, null);
    }

    private CustomStyleExchange() {}
}
