package dev.bladetetra.forging;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small provider-owned correction layer over standard SlashBlade OBJ groups.
 * Exact model entries win; models without an unambiguous entry use the conservative
 * blade/handle/sheath convention.
 */
public record LegacyModelAdapter(String id, List<String> bladeGroups,
        List<String> hiltGroups, List<String> sayaGroups, Axis axis,
        boolean reversed, Float tsubaCenter, Float tsubaRadius,
        Float tsukaCenter, Float tsukaRadius,
        LegacyCalibrationProfile.PartTransform hiltTransform,
        LegacyCalibrationProfile.PartTransform sayaTransform) {
    public enum Axis { X, Y, Z }

    public static final LegacyModelAdapter STANDARD = new LegacyModelAdapter(
            "standard", List.of("blade"), List.of("handle"), List.of("sheath"),
            Axis.X, false, null, null, null, null, null, null);

    private static Map<ResourceLocation, LegacyModelAdapter> adapters;

    public LegacyModelAdapter {
        bladeGroups = List.copyOf(bladeGroups == null || bladeGroups.isEmpty()
                ? STANDARD.bladeGroups : bladeGroups);
        hiltGroups = List.copyOf(hiltGroups == null ? List.of() : hiltGroups);
        sayaGroups = List.copyOf(sayaGroups == null || sayaGroups.isEmpty()
                ? STANDARD.sayaGroups : sayaGroups);
        axis = axis == null ? Axis.X : axis;
    }

    public static synchronized LegacyModelAdapter resolve(ResourceLocation model) {
        if (adapters == null) load();
        return adapters.getOrDefault(model, STANDARD);
    }

    public LegacyCalibrationProfile apply(LegacyCalibrationProfile base) {
        base = base == null ? LegacyCalibrationProfile.DEFAULT : base;
        LegacyCalibrationProfile.PartTransform hilt = hiltTransform == null
                ? base.tsubaTransform() : hiltTransform;
        LegacyCalibrationProfile.PartTransform saya = sayaTransform == null
                ? base.sayaTransform() : sayaTransform;
        return new LegacyCalibrationProfile(
                value(tsubaCenter, base.tsubaCenter()), value(tsubaRadius, base.tsubaRadius()),
                value(tsukaCenter, base.tsukaCenter()), value(tsukaRadius, base.tsukaRadius()),
                hilt, hilt, saya, base.bladeTransform(), reversed || base.flipped(),
                base.revision()).normalized();
    }

    public float coordinate(float x, float y, float z) {
        float value = switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return reversed ? -value : value;
    }

    private static float value(Float configured, float fallback) {
        return configured != null && Float.isFinite(configured) ? configured : fallback;
    }

    private static void load() {
        Map<ResourceLocation, LegacyModelAdapter> result = new LinkedHashMap<>();
        Set<ResourceLocation> ambiguous = new HashSet<>();
        for (var info : ModList.get().getModFiles()) {
            // Providers intentionally publish adapters into the blade_tetra namespace.
            // Do not walk every mod's entire data tree just to discover this tiny opt-in folder.
            Path root = info.getFile().findResource("data", "blade_tetra", "legacy_adapters");
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> read(path, result, ambiguous));
            } catch (Exception exception) {
                LogUtils.getLogger().warn("Cannot scan legacy adapters in {}", root, exception);
            }
        }
        adapters = Map.copyOf(result);
        LogUtils.getLogger().info(
                "Blade Tetra loaded {} named-model adapter entries; quarantined {} conflicts",
                adapters.size(), ambiguous.size());
    }

    private static void read(Path path, Map<ResourceLocation, LegacyModelAdapter> output,
            Set<ResourceLocation> ambiguous) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject json = parsed.getAsJsonObject();
            String id = path.getFileName().toString().replaceFirst("\\.json$", "");
            LegacyModelAdapter adapter = new LegacyModelAdapter(id,
                    strings(json, "blade_groups", List.of("blade")),
                    strings(json, "hilt_groups", List.of("handle")),
                    strings(json, "saya_groups", List.of("sheath")),
                    parseAxis(json), bool(json, "reversed"),
                    number(json, "tsuba_center"), number(json, "tsuba_radius"),
                    number(json, "tsuka_center"), number(json, "tsuka_radius"),
                    transform(json, "hilt_transform"), transform(json, "saya_transform"));
            for (String model : strings(json, "models", List.of())) {
                ResourceLocation location = ResourceLocation.tryParse(model);
                if (location != null) register(location, adapter, path, output, ambiguous);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Ignoring malformed legacy model adapter {}", path, exception);
        }
    }

    private static void register(ResourceLocation model, LegacyModelAdapter adapter, Path source,
            Map<ResourceLocation, LegacyModelAdapter> output, Set<ResourceLocation> ambiguous) {
        if (ambiguous.contains(model)) return;
        LegacyModelAdapter previous = output.get(model);
        if (previous == null) {
            output.put(model, adapter);
            return;
        }
        if (equivalent(previous, adapter)) return;

        output.remove(model);
        ambiguous.add(model);
        LogUtils.getLogger().warn(
                "Conflicting Blade Tetra legacy model adapters for {} ({} vs {} from {}); using standard fallback",
                model, previous.id(), adapter.id(), source);
    }

    private static boolean equivalent(LegacyModelAdapter first, LegacyModelAdapter second) {
        return first.bladeGroups().equals(second.bladeGroups())
                && first.hiltGroups().equals(second.hiltGroups())
                && first.sayaGroups().equals(second.sayaGroups())
                && first.axis() == second.axis()
                && first.reversed() == second.reversed()
                && java.util.Objects.equals(first.tsubaCenter(), second.tsubaCenter())
                && java.util.Objects.equals(first.tsubaRadius(), second.tsubaRadius())
                && java.util.Objects.equals(first.tsukaCenter(), second.tsukaCenter())
                && java.util.Objects.equals(first.tsukaRadius(), second.tsukaRadius())
                && java.util.Objects.equals(first.hiltTransform(), second.hiltTransform())
                && java.util.Objects.equals(first.sayaTransform(), second.sayaTransform());
    }

    private static List<String> strings(JsonObject json, String key, List<String> fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        List<String> result = new ArrayList<>();
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> {
                if (element.isJsonPrimitive()) result.add(element.getAsString());
            });
        } else if (value.isJsonPrimitive()) {
            result.add(value.getAsString());
        }
        return result.isEmpty() ? fallback : result;
    }

    private static Axis parseAxis(JsonObject json) {
        if (!json.has("axis") || !json.get("axis").isJsonPrimitive()) return Axis.X;
        try {
            return Axis.valueOf(json.get("axis").getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Axis.X;
        }
    }

    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                && json.get(key).getAsBoolean();
    }

    private static Float number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        float value = json.get(key).getAsFloat();
        return Float.isFinite(value) ? value : null;
    }

    private static LegacyCalibrationProfile.PartTransform transform(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return null;
        JsonObject json = root.getAsJsonObject(key);
        return new LegacyCalibrationProfile.PartTransform(
                finite(json, "scale", 1), finite(json, "offset_x", 0),
                finite(json, "offset_y", 0), finite(json, "rotation", 0),
                finite(json, "length", 1), finite(json, "width", 1)).normalized();
    }

    private static float finite(JsonObject json, String key, float fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return fallback;
        try {
            float value = json.get(key).getAsFloat();
            return Float.isFinite(value) ? value : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
