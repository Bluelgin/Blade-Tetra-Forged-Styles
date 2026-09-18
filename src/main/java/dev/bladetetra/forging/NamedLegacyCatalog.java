package dev.bladetetra.forging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Discovers standard Resharped named-blade metadata from installed providers.
 *
 * <p>Discovery deliberately does not parse OBJ geometry. Third-party visual
 * complexity is a client-side concern and must never decide whether a server or
 * player can enter a world. Unsupported models are handled lazily by the client
 * renderer and fall back to ordinary Blade Tetra fittings.</p>
 */
public final class NamedLegacyCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum DiagnosticReason {
        INVALID_DEFINITION,
        CONDITION_DISABLED,
        DUPLICATE_ID,
        UNSUPPORTED_MODEL,
        RECIPE_IGNORED
    }

    public record Diagnostic(String subject, DiagnosticReason reason, String detail) {}

    private record Definition(JsonObject json, Path source, Path namespaceRoot) {}

    private static Map<String, LegacyImprintKind> entries;
    private static List<Diagnostic> diagnostics = List.of();
    private static final Map<String, JsonObject> languages = new HashMap<>();

    public static synchronized Collection<LegacyImprintKind> values() {
        if (entries == null) load();
        return List.copyOf(entries.values());
    }

    public static LegacyImprintKind get(String id) {
        values();
        return entries.get(id);
    }

    public static synchronized List<Diagnostic> diagnostics() {
        values();
        return diagnostics;
    }

    public static synchronized Map<DiagnosticReason, Integer> diagnosticCounts() {
        values();
        EnumMap<DiagnosticReason, Integer> result = new EnumMap<>(DiagnosticReason.class);
        for (Diagnostic diagnostic : diagnostics) {
            result.merge(diagnostic.reason(), 1, Integer::sum);
        }
        return Map.copyOf(result);
    }

    public static synchronized String localizedName(LegacyImprintKind kind, String locale) {
        String key = kind.name().getNamespace() + "/" + locale;
        JsonObject language = languages.computeIfAbsent(key, unused -> {
            JsonObject result = new JsonObject();
            for (var info : ModList.get().getModFiles()) {
                Path path = info.getFile().findResource(
                        "assets", kind.name().getNamespace(), "lang", locale + ".json");
                if (!Files.isRegularFile(path)) continue;
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    parsed.getAsJsonObject().entrySet()
                            .forEach(e -> result.add(e.getKey(), e.getValue()));
                } catch (Exception exception) {
                    LOGGER.debug("Ignoring unreadable optional named-blade language file {}",
                            path, exception);
                }
            }
            return result;
        });
        JsonElement translated = language.get(kind.translationKey());
        return translated != null && translated.isJsonPrimitive()
                ? translated.getAsString()
                : kind.name().getPath().replace('_', ' ');
    }

    private static void load() {
        Map<String, Definition> definitions = new TreeMap<>();
        Set<String> ambiguous = new HashSet<>();
        Set<Path> providerNamespaces = new HashSet<>();
        List<Diagnostic> foundDiagnostics = new ArrayList<>();

        for (var info : ModList.get().getModFiles()) {
            Path dataRoot = info.getFile().findResource("data");
            if (!Files.isDirectory(dataRoot)) continue;
            scanDefinitionNamespaces(dataRoot, definitions, ambiguous,
                    providerNamespaces, foundDiagnostics);
        }

        Map<String, List<JsonObject>> recipes = scanRelevantRecipes(
                providerNamespaces, definitions.keySet(), foundDiagnostics);

        LinkedHashMap<String, LegacyImprintKind> accepted = new LinkedHashMap<>();
        for (var entry : definitions.entrySet()) {
            String sourceName = entry.getKey();
            Definition definition = entry.getValue();
            try {
                ResourceLocation name = ResourceLocation.tryParse(sourceName);
                if (name == null) {
                    reject(foundDiagnostics, sourceName, DiagnosticReason.INVALID_DEFINITION,
                            "invalid named-blade id");
                    continue;
                }
                String path = name.getPath();
                if (excluded(path)) continue;

                JsonElement renderValue = definition.json().get("render");
                if (renderValue == null || !renderValue.isJsonObject()) {
                    reject(foundDiagnostics, sourceName, DiagnosticReason.INVALID_DEFINITION,
                            "missing render object");
                    continue;
                }
                JsonObject render = renderValue.getAsJsonObject();
                ResourceLocation texture = primitiveLocation(render, "texture");
                if (texture == null) {
                    reject(foundDiagnostics, sourceName, DiagnosticReason.INVALID_DEFINITION,
                            "missing or invalid render.texture");
                    continue;
                }
                ResourceLocation model = render.has("model")
                        ? primitiveLocation(render, "model")
                        : ResourceLocation.tryParse("slashblade:model/blade.obj");
                if (model == null) {
                    reject(foundDiagnostics, sourceName, DiagnosticReason.INVALID_DEFINITION,
                            "invalid render.model");
                    continue;
                }
                if (!model.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".obj")) {
                    reject(foundDiagnostics, sourceName, DiagnosticReason.UNSUPPORTED_MODEL,
                            "non-OBJ/custom renderer; kept out of automatic fitting discovery");
                    continue;
                }

                JsonObject properties = definition.json().has("properties")
                        && definition.json().get("properties").isJsonObject()
                        ? definition.json().getAsJsonObject("properties")
                        : new JsonObject();
                double baseAttack = boundedNumber(properties, "attack_base", 0.0D,
                        -1024.0D, 1_000_000.0D);
                int maxDamage = (int) Math.round(boundedNumber(properties, "max_damage",
                        2.0D, 2.0D, 10_000_000.0D));
                String id = name.getNamespace().equals("slashblade") && path.startsWith("fox_")
                        ? path : name.getNamespace() + "/" + path;
                String feature = chooseMaterial(sourceName,
                        recipes.getOrDefault(sourceName, List.of()));
                ResourceLocation slashArt = resource(properties, "slash_art");
                List<ResourceLocation> specialEffects = resources(properties, "special_effects");

                // Only metadata/adapters are touched on the server. Geometry is resolved lazily
                // from SlashBlade's already-loaded client WavefrontObject.
                LegacyCalibrationProfile defaultProfile = LegacyModelAdapter.resolve(model)
                        .apply(LegacyCalibrationProfile.DEFAULT);
                accepted.put(id, new LegacyImprintKind(id, name, model, texture, feature,
                        defaultProfile, baseAttack, Math.max(2, maxDamage),
                        slashArt, specialEffects));
            } catch (RuntimeException exception) {
                reject(foundDiagnostics, sourceName, DiagnosticReason.INVALID_DEFINITION,
                        exception.getClass().getSimpleName() + ": "
                                + String.valueOf(exception.getMessage()));
                LOGGER.debug("Skipping unsupported named blade {} from {}",
                        sourceName, definition.source(), exception);
            }
        }

        entries = Map.copyOf(accepted);
        diagnostics = List.copyOf(foundDiagnostics);
        Map<DiagnosticReason, Integer> counts = diagnosticCountsNoLoad(foundDiagnostics);
        LOGGER.info("Blade Tetra discovered {} named blades; skipped {} optional definitions {}",
                entries.size(), diagnostics.size(), counts);
        if (LOGGER.isDebugEnabled()) {
            for (Diagnostic diagnostic : diagnostics) {
                LOGGER.debug("Named blade discovery {}: {} ({})",
                        diagnostic.reason(), diagnostic.subject(), diagnostic.detail());
            }
        }
    }

    private static void scanDefinitionNamespaces(Path dataRoot,
            Map<String, Definition> definitions,
            Set<String> ambiguous,
            Set<Path> providerNamespaces,
            List<Diagnostic> foundDiagnostics) {
        try (var namespaces = Files.list(dataRoot)) {
            namespaces.filter(Files::isDirectory).forEach(namespaceRoot -> {
                Path namedRoot = namespaceRoot.resolve("slashblade").resolve("named_blades");
                if (!Files.isDirectory(namedRoot)) return;
                try (var files = Files.walk(namedRoot)) {
                    files.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".json"))
                            .forEach(path -> readDefinition(path, namespaceRoot, definitions,
                                    ambiguous, providerNamespaces, foundDiagnostics));
                } catch (Exception exception) {
                    LOGGER.warn("Cannot scan named blade definitions in {}", namedRoot, exception);
                }
            });
        } catch (Exception exception) {
            LOGGER.warn("Cannot enumerate data namespaces in {}", dataRoot, exception);
        }
    }

    private static void readDefinition(Path path, Path namespaceRoot,
            Map<String, Definition> definitions,
            Set<String> ambiguous,
            Set<Path> providerNamespaces,
            List<Diagnostic> foundDiagnostics) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                reject(foundDiagnostics, path.toString(), DiagnosticReason.INVALID_DEFINITION,
                        "root is not an object");
                return;
            }
            JsonObject json = parsed.getAsJsonObject();
            if (!ICondition.shouldRegisterEntry(json)) {
                reject(foundDiagnostics, path.toString(), DiagnosticReason.CONDITION_DISABLED,
                        "forge:conditions disabled this entry");
                return;
            }
            if (!json.has("name") || !json.get("name").isJsonPrimitive()
                    || !json.has("render")) {
                reject(foundDiagnostics, path.toString(), DiagnosticReason.INVALID_DEFINITION,
                        "missing primitive name or render");
                return;
            }
            String name = json.get("name").getAsString();
            if (name.isBlank() || ResourceLocation.tryParse(name) == null) {
                reject(foundDiagnostics, path.toString(), DiagnosticReason.INVALID_DEFINITION,
                        "invalid name: " + name);
                return;
            }
            providerNamespaces.add(namespaceRoot);
            if (ambiguous.contains(name)) return;
            Definition candidate = new Definition(json, path, namespaceRoot);
            Definition previous = definitions.get(name);
            if (previous == null) {
                definitions.put(name, candidate);
                return;
            }
            if (previous.json().equals(json)) {
                return;
            }

            JsonObject selected = selectEquivalentRenderVariant(previous.json(), json);
            if (selected != null) {
                Definition preferred = selected == json ? candidate : previous;
                definitions.put(name, preferred);
                LOGGER.debug("Collapsing equivalent-render state variants for {} ({} vs {}); using {}",
                        name, previous.source(), path, preferred.source());
                return;
            }

            definitions.remove(name);
            ambiguous.add(name);
            reject(foundDiagnostics, name, DiagnosticReason.DUPLICATE_ID,
                    previous.source() + " conflicts with " + path);
        } catch (Exception exception) {
            reject(foundDiagnostics, path.toString(), DiagnosticReason.INVALID_DEFINITION,
                    exception.getClass().getSimpleName() + ": "
                            + String.valueOf(exception.getMessage()));
            LOGGER.debug("Ignoring unsupported optional SlashBlade definition {}",
                    path, exception);
        }
    }

    /**
     * SlashBlade may publish several state files for one named blade. Yamato is the
     * canonical example: the intact and broken/sealed definitions share the same
     * name, model and texture. They are one visual identity, not a provider conflict.
     *
     * <p>Equivalent render identities collapse to the least-degraded state. A true
     * model/texture disagreement still returns {@code null} so discovery quarantines
     * the duplicate instead of depending on scan order.</p>
     */
    static JsonObject selectEquivalentRenderVariant(JsonObject first, JsonObject second) {
        if (first == null || second == null || !sameRenderIdentity(first, second)) {
            return null;
        }
        int firstPenalty = degradedStatePenalty(first);
        int secondPenalty = degradedStatePenalty(second);
        if (firstPenalty != secondPenalty) {
            return firstPenalty < secondPenalty ? first : second;
        }
        // Equal-state duplicates are still equivalent for imprinting. Use a stable
        // content comparison so ModList iteration order cannot choose gameplay metadata.
        return first.toString().compareTo(second.toString()) <= 0 ? first : second;
    }

    static boolean sameRenderIdentity(JsonObject first, JsonObject second) {
        JsonObject firstRender = renderObject(first);
        JsonObject secondRender = renderObject(second);
        if (firstRender == null || secondRender == null) return false;

        ResourceLocation firstTexture = primitiveLocation(firstRender, "texture");
        ResourceLocation secondTexture = primitiveLocation(secondRender, "texture");
        if (firstTexture == null || secondTexture == null || !firstTexture.equals(secondTexture)) {
            return false;
        }

        ResourceLocation firstModel = firstRender.has("model")
                ? primitiveLocation(firstRender, "model")
                : ResourceLocation.tryParse("slashblade:model/blade.obj");
        ResourceLocation secondModel = secondRender.has("model")
                ? primitiveLocation(secondRender, "model")
                : ResourceLocation.tryParse("slashblade:model/blade.obj");
        return firstModel != null && firstModel.equals(secondModel);
    }

    static int degradedStatePenalty(JsonObject definition) {
        if (definition == null || !definition.has("properties")
                || !definition.get("properties").isJsonObject()) {
            return 0;
        }
        JsonObject properties = definition.getAsJsonObject("properties");
        JsonElement swordTypes = properties.get("sword_type");
        if (swordTypes == null) return 0;

        int penalty = 0;
        if (swordTypes.isJsonArray()) {
            for (JsonElement element : swordTypes.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    penalty += degradedSwordTypePenalty(element.getAsString());
                }
            }
        } else if (swordTypes.isJsonPrimitive()) {
            penalty += degradedSwordTypePenalty(swordTypes.getAsString());
        }
        return penalty;
    }

    private static int degradedSwordTypePenalty(String value) {
        if (value == null) return 0;
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "broken" -> 100;
            case "rust", "rusted" -> 80;
            case "sealed" -> 40;
            default -> 0;
        };
    }

    private static JsonObject renderObject(JsonObject definition) {
        if (definition == null || !definition.has("render")
                || !definition.get("render").isJsonObject()) {
            return null;
        }
        return definition.getAsJsonObject("render");
    }

    private static Map<String, List<JsonObject>> scanRelevantRecipes(
            Set<Path> providerNamespaces, Set<String> knownNames,
            List<Diagnostic> foundDiagnostics) {
        Map<String, List<JsonObject>> recipes = new HashMap<>();
        for (Path namespaceRoot : providerNamespaces) {
            Path recipeRoot = namespaceRoot.resolve("recipes");
            if (!Files.isDirectory(recipeRoot)) continue;
            try (var files = Files.walk(recipeRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            try (Reader reader = Files.newBufferedReader(path)) {
                                JsonElement parsed = JsonParser.parseReader(reader);
                                if (!parsed.isJsonObject()) return;
                                JsonObject json = parsed.getAsJsonObject();
                                if (!ICondition.shouldRegisterEntry(json)
                                        || !json.has("blade")
                                        || !json.get("blade").isJsonPrimitive()) return;
                                String blade = json.get("blade").getAsString();
                                if (!knownNames.contains(blade)) return;
                                recipes.computeIfAbsent(blade, unused -> new ArrayList<>()).add(json);
                            } catch (Exception exception) {
                                reject(foundDiagnostics, path.toString(),
                                        DiagnosticReason.RECIPE_IGNORED,
                                        exception.getClass().getSimpleName());
                                LOGGER.debug("Ignoring optional named-blade recipe {}",
                                        path, exception);
                            }
                        });
            } catch (Exception exception) {
                LOGGER.debug("Cannot scan optional named-blade recipes in {}",
                        recipeRoot, exception);
            }
        }
        return recipes;
    }

    private static Map<DiagnosticReason, Integer> diagnosticCountsNoLoad(
            List<Diagnostic> values) {
        EnumMap<DiagnosticReason, Integer> counts = new EnumMap<>(DiagnosticReason.class);
        for (Diagnostic value : values) counts.merge(value.reason(), 1, Integer::sum);
        return counts;
    }

    private static void reject(List<Diagnostic> output, String subject,
            DiagnosticReason reason, String detail) {
        output.add(new Diagnostic(subject, reason, detail == null ? "" : detail));
    }

    private static ResourceLocation primitiveLocation(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        return ResourceLocation.tryParse(object.get(key).getAsString());
    }

    private static double boundedNumber(JsonObject object, String key, double fallback,
            double min, double max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isNumber()) return fallback;
        try {
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) && value >= min && value <= max ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static ResourceLocation resource(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        return ResourceLocation.tryParse(object.get(key).getAsString());
    }

    private static List<ResourceLocation> resources(JsonObject object, String key) {
        if (!object.has(key)) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) result.add(id);
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) continue;
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id != null) result.add(id);
            }
        }
        return List.copyOf(result);
    }

    static boolean excluded(String path) {
        return path.startsWith("rodai_") || path.contains("broken") || path.endsWith("_rust")
                || Set.of("sabigatana", "slashblade", "slashblade_wood", "slashblade_white",
                        "slashblade_bamboo", "slashblade_silverbamboo").contains(path);
    }

    private static String chooseMaterial(String name, List<JsonObject> recipes) {
        TreeSet<String> candidates = new TreeSet<>();
        for (JsonObject recipe : recipes) {
            try {
                if (!recipe.has("blade") || !recipe.get("blade").isJsonPrimitive()
                        || !name.equals(recipe.get("blade").getAsString())) continue;
                if (recipe.has("key") && recipe.get("key").isJsonObject()) {
                    for (var ingredient : recipe.getAsJsonObject("key").entrySet()) {
                        collectMaterial(ingredient.getValue(), candidates, 0);
                    }
                }
                for (String key : List.of("addition", "template", "ingredients")) {
                    if (recipe.has(key)) collectMaterial(recipe.get(key), candidates, 0);
                }
            } catch (RuntimeException ignored) {
                // Recipe enrichment is never a compatibility gate.
            }
        }
        return candidates.stream().min(Comparator.comparingInt(NamedLegacyCatalog::materialRank)
                .thenComparing(String::compareTo)).orElse("slashblade:proudsoul_sphere");
    }

    private static void collectMaterial(JsonElement element, Set<String> output, int depth) {
        if (element == null || depth > 16) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(e -> collectMaterial(e, output, depth + 1));
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject ingredient = element.getAsJsonObject();
        if (ingredient.has("request") || !ingredient.has("item")
                || !ingredient.get("item").isJsonPrimitive()) return;
        String item = ingredient.get("item").getAsString();
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null) return;
        if ((item.startsWith("minecraft:") || item.startsWith("slashblade:proudsoul"))
                && !item.equals("minecraft:air")
                && !item.equals("slashblade:proudsoul_ingot")) {
            output.add(item);
        }
    }

    private static int materialRank(String item) {
        if (item.startsWith("slashblade:proudsoul")) return 0;
        if (item.contains("nether_star") || item.contains("dragon") || item.contains("netherite")) return 1;
        if (item.contains("diamond") || item.contains("blaze") || item.contains("ender")) return 2;
        return 3;
    }

    private NamedLegacyCatalog() {}
}
