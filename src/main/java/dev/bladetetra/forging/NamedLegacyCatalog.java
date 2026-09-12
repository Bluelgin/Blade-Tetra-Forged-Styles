package dev.bladetetra.forging;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Reads standard Resharped definitions from installed mods, without copying their assets. */
public final class NamedLegacyCatalog {
    private static Map<String, LegacyImprintKind> entries;
    private static final Map<String, JsonObject> languages = new HashMap<>();
    public static synchronized Collection<LegacyImprintKind> values() {
        if (entries == null) load();
        return List.copyOf(entries.values());
    }
    public static LegacyImprintKind get(String id) {
        values();
        return entries.get(id);
    }
    public static synchronized String localizedName(LegacyImprintKind kind, String locale) {
        String key = kind.name().getNamespace() + "/" + locale;
        JsonObject language = languages.computeIfAbsent(key, unused -> {
            JsonObject result = new JsonObject();
            for (var info : ModList.get().getModFiles()) {
                Path path = info.getFile().findResource("assets", kind.name().getNamespace(), "lang", locale + ".json");
                if (!Files.isRegularFile(path)) continue;
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                            .forEach(e -> result.add(e.getKey(), e.getValue()));
                } catch (Exception ignored) {}
            }
            return result;
        });
        return language.has(kind.translationKey()) ? language.get(kind.translationKey()).getAsString()
                : kind.name().getPath().replace('_', ' ');
    }
    private static void load() {
        Map<String, JsonObject> definitions = new TreeMap<>();
        List<JsonObject> recipes = new ArrayList<>();
        for (var info : ModList.get().getModFiles()) {
            Path root = info.getFile().findResource("data");
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .filter(p -> p.toString().replace('\\', '/').contains("/slashblade/named_blades/")
                                || p.toString().replace('\\', '/').contains("/recipes/"))
                        .forEach(path -> {
                            try (Reader reader = Files.newBufferedReader(path)) {
                                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                                if (path.toString().replace('\\', '/').contains("/slashblade/named_blades/")) {
                                    if (json.has("name") && json.has("render"))
                                        definitions.put(json.get("name").getAsString(), json);
                                } else if (json.has("blade")) recipes.add(json);
                            } catch (Exception ignored) { /* Optional malformed provider recipes are not ours to repair. */ }
                        });
            } catch (IOException exception) {
                LogUtils.getLogger().warn("Cannot scan named blade data in {}", root, exception);
            }
        }
        entries = new LinkedHashMap<>();
        Map<ResourceLocation, LegacyModelAnalysis.Result> analyzedModels = new HashMap<>();
        for (var entry : definitions.entrySet()) {
            try {
                ResourceLocation name = new ResourceLocation(entry.getKey());
                String path = name.getPath();
                if (excluded(path)) continue;
                JsonObject render = entry.getValue().getAsJsonObject("render");
                if (!render.has("texture")) continue;
                ResourceLocation model = new ResourceLocation(render.has("model")
                        ? render.get("model").getAsString() : "slashblade:model/blade.obj");
                if (!model.getPath().endsWith(".obj")) continue;
                ResourceLocation texture = new ResourceLocation(render.get("texture").getAsString());
                JsonObject properties = entry.getValue().has("properties")
                        && entry.getValue().get("properties").isJsonObject()
                        ? entry.getValue().getAsJsonObject("properties")
                        : new JsonObject();
                double baseAttack = number(properties, "attack_base", 0.0D);
                int maxDamage = Math.max(2, (int) Math.round(
                        number(properties, "max_damage", 2.0D)));
                String id = name.getNamespace().equals("slashblade") && path.startsWith("fox_")
                        ? path : name.getNamespace() + "/" + path;
                String feature = chooseMaterial(name.toString(), recipes);
                ResourceLocation slashArt = resource(properties, "slash_art");
                List<ResourceLocation> specialEffects = resources(properties, "special_effects");
                LegacyModelAdapter adapter = LegacyModelAdapter.resolve(model);
                if (!analyzedModels.containsKey(model))
                    analyzedModels.put(model, analyze(model, adapter));
                LegacyModelAnalysis.Result analysis = analyzedModels.get(model);
                if (analysis == null || !analysis.usable() || !analysis.saya()) {
                    String reason = analysis == null ? "missing model"
                            : !analysis.saya() ? "no independent saya group"
                            : "no safe complete-hilt boundary";
                    LogUtils.getLogger().info("Named blade {} is not imitable: {} (adapter {})",
                            name, reason, adapter.id());
                    continue;
                }
                entries.put(id, new LegacyImprintKind(id, name, model, texture, feature,
                        path.startsWith("fox_") ? LegacyCalibrationProfile.DEFAULT : analysis.profile(),
                        baseAttack, maxDamage, slashArt, specialEffects));
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Skipping unsupported named blade {}", entry.getKey());
            }
        }
        LogUtils.getLogger().info("Blade Tetra discovered {} imitable named blades: {}",
                entries.size(), entries.keySet());
    }
    private static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.getAsJsonPrimitive(key).isNumber()) return fallback;
        return object.get(key).getAsDouble();
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
    private static LegacyModelAnalysis.Result analyze(ResourceLocation model,
            LegacyModelAdapter adapter) {
        for (var info : ModList.get().getModFiles()) {
            Path path = info.getFile().findResource("assets", model.getNamespace(), model.getPath());
            if (!Files.isRegularFile(path)) continue;
            try (Reader reader = Files.newBufferedReader(path)) {
                return LegacyModelAnalysis.analyze(reader, adapter);
            } catch (Exception exception) {
                LogUtils.getLogger().warn("Cannot partition named model {}", model, exception);
                return null;
            }
        }
        return null;
    }
    private static String chooseMaterial(String name, List<JsonObject> recipes) {
        TreeSet<String> candidates = new TreeSet<>();
        for (JsonObject recipe : recipes) {
            if (!recipe.get("blade").isJsonPrimitive()
                    || !name.equals(recipe.get("blade").getAsString())) continue;
            if (recipe.has("key")) {
                for (var ingredient : recipe.getAsJsonObject("key").entrySet())
                    collectMaterial(ingredient.getValue(), candidates);
            }
            for (String key : List.of("addition", "template", "ingredients"))
                if (recipe.has(key)) collectMaterial(recipe.get(key), candidates);
        }
        return candidates.stream().min(Comparator.comparingInt(NamedLegacyCatalog::materialRank)
                .thenComparing(String::compareTo)).orElse("slashblade:proudsoul_sphere");
    }
    private static void collectMaterial(JsonElement element, Set<String> output) {
        if (element.isJsonArray()) { element.getAsJsonArray().forEach(e -> collectMaterial(e, output)); return; }
        if (!element.isJsonObject()) return;
        JsonObject ingredient = element.getAsJsonObject();
        if (ingredient.has("request") || !ingredient.has("item")) return;
        String item = ingredient.get("item").getAsString();
        if ((item.startsWith("minecraft:") || item.startsWith("slashblade:proudsoul"))
                && !item.equals("minecraft:air") && !item.equals("slashblade:proudsoul_ingot")) output.add(item);
    }
    private static int materialRank(String item) {
        if (item.startsWith("slashblade:proudsoul")) return 0;
        if (item.contains("nether_star") || item.contains("dragon") || item.contains("netherite")) return 1;
        if (item.contains("diamond") || item.contains("blaze") || item.contains("ender")) return 2;
        return 3;
    }
    private NamedLegacyCatalog() {}
}
