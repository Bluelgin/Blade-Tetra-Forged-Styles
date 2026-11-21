package dev.bladetetra.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bladetetra.forging.LegacyCalibrationProfile.PartTransform;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyModelAdapter;
import dev.bladetetra.forging.LegacyModelAdapter.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client resource-pack declaration for one model, optionally specialized by named blade. */
record LegacyModelRules(JsonObject json, LegacyModelAdapter legacy) {
    static final LegacyModelRules AUTOMATIC = new LegacyModelRules(new JsonObject(), LegacyModelAdapter.STANDARD);

    static LegacyModelRules load(ResourceManager resources, LegacyImprintKind kind) {
        var model = kind.model();
        var legacy = LegacyModelAdapter.resolve(model);
        String base = "bladetetra/legacy_models/" + model.getPath();
        JsonObject settings = read(resources, new ResourceLocation(model.getNamespace(), base + ".json"), legacy);
        JsonObject variant = read(resources, new ResourceLocation(model.getNamespace(), base + "/"
                + kind.name().getNamespace() + "/" + kind.name().getPath() + ".json"), legacy);
        variant.entrySet().forEach(entry -> settings.add(entry.getKey(), entry.getValue()));
        return new LegacyModelRules(settings, legacy);
    }

    private static JsonObject read(ResourceManager resources, ResourceLocation location, LegacyModelAdapter legacy) {
        var resource = resources.getResource(location);
        if (resource.isPresent()) {
            try (var reader = resource.get().open()) {
                byte[] bytes = reader.readNBytes(32769);
                if (bytes.length > 32768) throw new IllegalArgumentException("adapter exceeds 32 KiB");
                var json = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                new LegacyModelRules(json, legacy).validate();
                return json;
            } catch (Exception failure) {
                LogUtils.getLogger().warn("Ignoring named model settings {}: {}", location, failure.getMessage());
            }
        }
        return new JsonObject();
    }

    private void validate() {
        axis("axis", null);
        axis("width_axis", null);
        geometryModel(null);
        if (json.has("reversed") && !json.getAsJsonPrimitive("reversed").isBoolean()) {
            throw new IllegalArgumentException("reversed must be boolean");
        }
        if (json.has("split_hilt") && !json.getAsJsonPrimitive("split_hilt").isBoolean()) {
            throw new IllegalArgumentException("split_hilt must be boolean");
        }
        for (String part : List.of("blade", "hilt", "saya")) {
            point(part + "_anchor");
            point(part + "_direction");
            adjustment(part, null);
            preserveShape(part);
            groups(part + "_groups", List.of());
            groups(part + "_luminous_groups", List.of());
            for (String state : List.of("item_blade", "item_bladens", "item_damaged", "blade_damaged", "blade_fragment")) {
                groups(state + "_" + part + "_groups", List.of());
                groups(state + "_" + part + "_luminous_groups", List.of());
            }
        }
    }

    ResourceLocation geometryModel(ResourceLocation fallback) {
        if (!json.has("geometry_model")) return fallback;
        var location = ResourceLocation.tryParse(json.get("geometry_model").getAsString());
        if (location == null || !location.getPath().endsWith(".obj")) {
            throw new IllegalArgumentException("geometry_model must identify an OBJ");
        }
        return location;
    }

    Axis explicitAxis() { return axis("axis", legacy == LegacyModelAdapter.STANDARD ? null : legacy.axis()); }

    Axis axis(String key, Axis fallback) {
        return json.has(key) ? Axis.valueOf(json.get(key).getAsString().toUpperCase(Locale.ROOT)) : fallback;
    }

    Boolean reversed() {
        if (json.has("reversed")) return json.get("reversed").getAsBoolean();
        return legacy == LegacyModelAdapter.STANDARD ? null : legacy.reversed();
    }

    List<String> groups(String key, List<String> fallback) {
        if (!json.has(key)) return fallback;
        var list = json.getAsJsonArray(key);
        if (list.size() > 32) throw new IllegalArgumentException("too many groups: " + key);
        List<String> names = new ArrayList<>();
        for (var value : list) {
            String name = value.getAsString();
            if (name.isBlank() || name.length() > 128 || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("invalid group: " + key);
            }
            if (!names.contains(name)) names.add(name);
        }
        return List.copyOf(names);
    }

    boolean splitHilt() { return !json.has("split_hilt") || json.get("split_hilt").getAsBoolean(); }

    Vector3f point(String key) {
        if (!json.has(key)) return null;
        var values = json.getAsJsonArray(key);
        if (values.size() != 3) throw new IllegalArgumentException("expected three coordinates: " + key);
        return new Vector3f(finite(values.get(0).getAsFloat()), finite(values.get(1).getAsFloat()),
                finite(values.get(2).getAsFloat()));
    }

    boolean preserveShape(String part) {
        String key = part + "_fit";
        if (!json.has(key)) return part.equals("hilt");
        return switch (json.get(key).getAsString()) {
            case "preserve" -> true;
            case "proportional" -> false;
            default -> throw new IllegalArgumentException("unknown fit: " + key);
        };
    }

    PartTransform adjustment(String part, PartTransform fallback) {
        String key = part + "_transform";
        if (!json.has(key)) return fallback;
        var values = json.getAsJsonObject(key);
        return new PartTransform(number(values, "scale", 1), number(values, "offset_x", 0),
                number(values, "offset_y", 0), number(values, "rotation", 0),
                number(values, "length", 1), number(values, "width", 1)).normalized();
    }

    private static float number(JsonObject values, String key, float fallback) {
        return values.has(key) ? finite(values.get(key).getAsFloat()) : fallback;
    }

    private static float finite(float value) {
        if (!Float.isFinite(value) || Math.abs(value) > 100_000) throw new IllegalArgumentException("invalid number");
        return value;
    }
}
