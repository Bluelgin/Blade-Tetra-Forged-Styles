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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small provider-owned correction layer over standard SlashBlade OBJ groups.
 * Exact model entries win; models without an entry use the conservative
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
        float value = switch (axis) { case X -> x; case Y -> y; case Z -> z; };
        return reversed ? -value : value;
    }

    private static float value(Float configured, float fallback) {
        return configured != null && Float.isFinite(configured) ? configured : fallback;
    }

    private static void load() {
        Map<ResourceLocation, LegacyModelAdapter> result = new LinkedHashMap<>();
        for (var info : ModList.get().getModFiles()) {
            Path root = info.getFile().findResource("data");
            if (!Files.isDirectory(root)) continue;
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".json"))
                        .filter(path -> path.toString().replace('\\', '/')
                                .contains("/blade_tetra/legacy_adapters/"))
                        .forEach(path -> read(path, result));
            } catch (Exception exception) {
                LogUtils.getLogger().warn("Cannot scan legacy adapters in {}", root, exception);
            }
        }
        adapters = Map.copyOf(result);
        LogUtils.getLogger().info("Blade Tetra loaded {} named-model adapter entries", adapters.size());
    }

    private static void read(Path path, Map<ResourceLocation, LegacyModelAdapter> output) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
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
                output.put(new ResourceLocation(model), adapter);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Ignoring malformed legacy model adapter {}", path, exception);
        }
    }

    private static List<String> strings(JsonObject json, String key, List<String> fallback) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        List<String> result = new ArrayList<>();
        if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> result.add(e.getAsString()));
        else result.add(value.getAsString());
        return result;
    }

    private static Axis parseAxis(JsonObject json) {
        if (!json.has("axis")) return Axis.X;
        try { return Axis.valueOf(json.get("axis").getAsString().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Axis.X; }
    }

    private static boolean bool(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    private static Float number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsFloat() : null;
    }

    private static LegacyCalibrationProfile.PartTransform transform(JsonObject root, String key) {
        if (!root.has(key)) return null;
        JsonObject json = root.getAsJsonObject(key);
        return new LegacyCalibrationProfile.PartTransform(
                json.has("scale") ? json.get("scale").getAsFloat() : 1,
                json.has("offset_x") ? json.get("offset_x").getAsFloat() : 0,
                json.has("offset_y") ? json.get("offset_y").getAsFloat() : 0,
                json.has("rotation") ? json.get("rotation").getAsFloat() : 0,
                json.has("length") ? json.get("length").getAsFloat() : 1,
                json.has("width") ? json.get("width").getAsFloat() : 1).normalized();
    }
}
