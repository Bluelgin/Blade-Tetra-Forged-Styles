package dev.bladetetra.forging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Loads the authored fitting matches without allowing data to define executable behavior. */
final class LegacyFusionCatalog {
    private static final String RESOURCE =
            "data/blade_tetra/legacy_fusions/definitions.json";
    private static final List<LegacyFusionDefinition> DEFINITIONS = loadBuiltIn();

    static LegacyFusionDefinition get(String id) {
        return DEFINITIONS.stream().filter(value -> value.id().equals(id))
                .findFirst().orElse(null);
    }

    static LegacyFusionDefinition resolve(NamedLegacyParts parts) {
        return DEFINITIONS.stream().filter(value -> value.matches(parts))
                .findFirst().orElse(null);
    }

    static List<LegacyFusionDefinition> values() {
        return DEFINITIONS;
    }

    static List<LegacyFusionDefinition> parse(Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Legacy fusion definitions must be an array");
        }
        List<LegacyFusionDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> orderedPairs = new HashSet<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Legacy fusion entry must be an object");
            }
            JsonObject json = element.getAsJsonObject();
            String id = required(json, "id");
            String saya = required(json, "saya");
            String hilt = required(json, "hilt");
            String improvement = required(json, "improvement");

            boolean legacyShape = json.has("abilityType") || json.has("ability");
            boolean coupledShape = json.has("slashArt") || json.has("specialEffects");
            if (legacyShape && coupledShape) {
                throw new IllegalArgumentException(
                        "Legacy fusion " + id + " mixes legacy and coupled ability fields");
            }

            ResourceLocation slashArt = null;
            List<ResourceLocation> specialEffects = List.of();
            if (legacyShape) {
                String rawType = required(json, "abilityType");
                ResourceLocation ability = parseResource(required(json, "ability"), id);
                LegacyFusionDefinition.AbilityType type;
                try {
                    type = LegacyFusionDefinition.AbilityType.valueOf(
                            rawType.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                            "Invalid ability type for legacy fusion " + id, exception);
                }
                if (type == LegacyFusionDefinition.AbilityType.SLASH_ART) {
                    slashArt = ability;
                } else {
                    specialEffects = List.of(ability);
                }
            } else {
                slashArt = optionalResource(json, "slashArt", id);
                specialEffects = resourceList(json, "specialEffects", id);
                if (slashArt == null && specialEffects.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Legacy fusion " + id + " must declare at least one ability");
                }
            }

            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate legacy fusion id " + id);
            }
            if (!orderedPairs.add(saya + "\u0000" + hilt)) {
                throw new IllegalArgumentException(
                        "Duplicate ordered fitting pair " + saya + " + " + hilt);
            }
            int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
            result.add(new LegacyFusionDefinition(id, saya, hilt, improvement,
                    slashArt, specialEffects, priority));
        }
        result.sort(Comparator.comparingInt(LegacyFusionDefinition::priority)
                .reversed().thenComparing(LegacyFusionDefinition::id));
        return List.copyOf(result);
    }

    private static ResourceLocation optionalResource(JsonObject json, String key, String id) {
        if (!json.has(key)) return null;
        if (!json.get(key).isJsonPrimitive() || json.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Invalid " + key + " for legacy fusion " + id);
        }
        return parseResource(json.get(key).getAsString(), id);
    }

    private static List<ResourceLocation> resourceList(JsonObject json, String key, String id) {
        if (!json.has(key)) return List.of();
        JsonElement value = json.get(key);
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        if (value.isJsonPrimitive()) {
            result.add(parseResource(value.getAsString(), id));
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || element.getAsString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Invalid " + key + " entry for legacy fusion " + id);
                }
                result.add(parseResource(element.getAsString(), id));
            }
        } else {
            throw new IllegalArgumentException("Invalid " + key + " for legacy fusion " + id);
        }
        return List.copyOf(result);
    }

    private static ResourceLocation parseResource(String value, String id) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) {
            throw new IllegalArgumentException("Invalid ability id for legacy fusion " + id);
        }
        return result;
    }

    private static String required(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || json.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing legacy fusion field " + key);
        }
        return json.get(key).getAsString();
    }

    private static List<LegacyFusionDefinition> loadBuiltIn() {
        InputStream stream = LegacyFusionCatalog.class.getClassLoader()
                .getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing built-in legacy fusion catalog " + RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read built-in legacy fusion catalog", exception);
        }
    }

    private LegacyFusionCatalog() {
    }
}
