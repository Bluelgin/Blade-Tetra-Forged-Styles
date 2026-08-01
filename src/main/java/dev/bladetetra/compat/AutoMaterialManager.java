package dev.bladetetra.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.data.MaterialStore;
import se.mickelus.tetra.module.data.MaterialData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Supplies a conservative Tetra material for populated Forge ingot tags which
 * would otherwise be unusable.
 *
 * <p>The listener is registered at the lowest priority so Tetra and datapacks
 * always get first refusal. Existing material keys and material tags are never
 * replaced. The generated entries are also added to Tetra's raw data map so
 * dedicated servers send exactly the same definitions to connecting clients.</p>
 */
@Mod.EventBusSubscriber(
        modid = BladeTetra.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoMaterialManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INGOT_PREFIX = "ingots/";
    private static final String GENERATED_PATH = "metal/blade_tetra_auto/";
    private static final Set<String> NON_METAL_INGOT_NAMES =
            Set.of("brick", "nether_brick");

    private AutoMaterialManager() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            reload();
        }
    }

    private static void reload() {
        if (!AutoMaterialConfig.ENABLED.get()) {
            LOGGER.info("Blade Tetra automatic material discovery is disabled");
            return;
        }

        DataManager manager = DataManager.instance;
        if (manager == null || manager.materialData == null) {
            LOGGER.warn("Tetra material store was unavailable during automatic discovery");
            return;
        }

        MaterialStore store = manager.materialData;
        Map<ResourceLocation, JsonElement> rawData = store.getRawData();
        Map<ResourceLocation, MaterialData> materialData = store.getData();
        if (rawData == null || materialData == null) {
            LOGGER.warn("Tetra material data was not ready during automatic discovery");
            return;
        }

        rawData.keySet().removeIf(AutoMaterialManager::isGeneratedLocation);
        materialData.keySet().removeIf(AutoMaterialManager::isGeneratedLocation);

        Set<String> existingKeys = new HashSet<>();
        materialData.values().stream()
                .map(data -> data.key)
                .filter(key -> key != null && !key.isBlank())
                .map(AutoMaterialManager::canonicalName)
                .forEach(existingKeys::add);

        Set<ResourceLocation> existingTags = new HashSet<>();
        rawData.values().forEach(element -> collectMaterialTags(element, existingTags));

        Set<String> blacklist = new HashSet<>();
        AutoMaterialConfig.BLACKLIST.get().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(blacklist::add);

        List<ResourceLocation> discoveredTags = BuiltInRegistries.ITEM
                .getTagNames()
                .map(TagKey::location)
                .filter(location -> "forge".equals(location.getNamespace()))
                .filter(location -> location.getPath().startsWith(INGOT_PREFIX))
                .filter(location -> location.getPath().length() > INGOT_PREFIX.length())
                .filter(AutoMaterialManager::isPopulated)
                .sorted()
                .toList();

        List<ResourceLocation> addedTags = new ArrayList<>();
        for (ResourceLocation tag : discoveredTags) {
            String rawName = tag.getPath().substring(INGOT_PREFIX.length());
            String materialName = canonicalName(rawName);
            if (materialName.isEmpty()
                    || NON_METAL_INGOT_NAMES.contains(materialName)
                    || existingKeys.contains(materialName)
                    || existingTags.contains(tag)
                    || isBlacklisted(blacklist, rawName, materialName, tag)) {
                continue;
            }

            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    "tetra", GENERATED_PATH + materialName);
            JsonArray sources = new JsonArray();
            sources.add(createDefinition(materialName, tag));

            try {
                MaterialData[] parsed = DataManager.gson.fromJson(
                        sources, MaterialData[].class);
                if (parsed == null
                        || parsed.length == 0
                        || parsed[0] == null
                        || parsed[0].material == null
                        || !parsed[0].material.isValid()) {
                    LOGGER.warn("Unable to parse generated Tetra material for {}", tag);
                    continue;
                }

                rawData.put(location, sources);
                materialData.put(location, parsed[0]);
                existingKeys.add(materialName);
                existingTags.add(tag);
                addedTags.add(tag);
            } catch (RuntimeException exception) {
                LOGGER.warn("Unable to generate Tetra material for {}", tag, exception);
            }
        }

        if (!addedTags.isEmpty()) {
            syncReloadToConnectedPlayers(manager, rawData);
        }
        LOGGER.info(
                "Blade Tetra automatic material discovery added {} material(s) from {} populated Forge ingot tag(s): {}",
                addedTags.size(),
                discoveredTags.size(),
                addedTags);
    }

    private static boolean isPopulated(ResourceLocation location) {
        TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(), location);
        return BuiltInRegistries.ITEM.getTag(tag)
                .map(entries -> entries.size() > 0)
                .orElse(false);
    }

    private static boolean isBlacklisted(
            Set<String> blacklist,
            String rawName,
            String materialName,
            ResourceLocation tag) {
        return blacklist.contains(rawName.toLowerCase(Locale.ROOT))
                || blacklist.contains(materialName)
                || blacklist.contains(tag.toString().toLowerCase(Locale.ROOT));
    }

    private static boolean isGeneratedLocation(ResourceLocation location) {
        return "tetra".equals(location.getNamespace())
                && location.getPath().startsWith(GENERATED_PATH);
    }

    private static String canonicalName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace('\\', '_')
                .replace('/', '_')
                .replace('-', '_')
                .replace("aluminium", "aluminum")
                .replaceAll("[^a-z0-9_.]", "_")
                .replaceAll("_+", "_");
        while (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void collectMaterialTags(
            JsonElement element,
            Set<ResourceLocation> tags) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectMaterialTags(child, tags));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("material") && object.get("material").isJsonObject()) {
            JsonObject material = object.getAsJsonObject("material");
            if (material.has("tag") && material.get("tag").isJsonPrimitive()) {
                ResourceLocation location =
                        ResourceLocation.tryParse(material.get("tag").getAsString());
                if (location != null) {
                    tags.add(location);
                }
            }
        }
    }

    private static JsonObject createDefinition(
            String materialName,
            ResourceLocation tag) {
        int color = generatedColor(materialName);

        JsonObject definition = new JsonObject();
        definition.addProperty("key", materialName);
        definition.addProperty("category", "metal");
        definition.addProperty("primary", 5.5F);
        definition.addProperty("secondary", 3.6F);
        definition.addProperty("tertiary", 3.2F);
        definition.addProperty("durability", 400);
        definition.addProperty("integrityCost", 2);
        definition.addProperty("integrityGain", 5);
        definition.addProperty("magicCapacity", 90);
        definition.addProperty("toolLevel", "minecraft:iron");
        definition.addProperty("toolEfficiency", 6.5F);

        JsonObject tints = new JsonObject();
        tints.addProperty("glyph", hex(lighten(color, 0.28F)));
        tints.addProperty("texture", hex(color));
        definition.add("tints", tints);

        JsonArray textures = new JsonArray();
        textures.add("metal");
        textures.add("default");
        definition.add("textures", textures);

        JsonObject material = new JsonObject();
        material.addProperty("tag", tag.toString());
        definition.add("material", material);

        JsonObject requiredTools = new JsonObject();
        requiredTools.addProperty("hammer_dig", "minecraft:iron");
        definition.add("requiredTools", requiredTools);
        return definition;
    }

    private static int generatedColor(String materialName) {
        int hash = materialName.hashCode();
        float hue = (hash & 0xffff) / 65535.0F;
        float saturation = 0.22F + ((hash >>> 16) & 0xff) / 255.0F * 0.22F;
        float brightness = 0.58F + ((hash >>> 24) & 0xff) / 255.0F * 0.16F;
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
    }

    private static int lighten(int color, float amount) {
        int red = color >>> 16 & 0xff;
        int green = color >>> 8 & 0xff;
        int blue = color & 0xff;
        red += Math.round((255 - red) * amount);
        green += Math.round((255 - green) * amount);
        blue += Math.round((255 - blue) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xffffff);
    }

    private static void syncReloadToConnectedPlayers(
            DataManager manager,
            Map<ResourceLocation, JsonElement> rawData) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.getPlayerList().getPlayerCount() > 0) {
            manager.sendToAll(manager.materialData.getDirectory(), rawData);
        }
    }
}
