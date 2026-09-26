package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.BladeTetra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Owns generated texture LRUs and the decoded atlas template lifecycle. */
final class MaterialTextureCache {
    private static final int MAX_CACHE_SIZE = 48;
    private static final int MAX_DURABILITY_CACHE_SIZE = 64;
    private static final int GENERATED_ATLAS_SIZE = 256;
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    BladeTetra.MOD_ID, "model/modular/standard_256.png");

    private static final Map<String, RegisteredTexture> MATERIALS =
            new LinkedHashMap<>(32, 0.75F, true);
    private static final Map<String, RegisteredTexture> EMISSIVE =
            new LinkedHashMap<>(24, 0.75F, true);
    private static final Set<String> NO_EMISSIVE =
            new LinkedHashSet<>(24);
    private static final Map<String, RegisteredTexture> DURABILITY =
            new LinkedHashMap<>(16, 0.75F, true);
    private static NativeImage atlasTemplate;

    static ResourceLocation material(String key) {
        RegisteredTexture value = MATERIALS.get(key);
        return value == null ? null : value.location();
    }

    static boolean materialsEmpty() {
        return MATERIALS.isEmpty();
    }

    static void putMaterial(String key, ResourceLocation location,
            DynamicTexture texture, Minecraft minecraft) {
        MATERIALS.put(key, new RegisteredTexture(location, texture));
        trim(MATERIALS, MAX_CACHE_SIZE, minecraft);
    }

    static ResourceLocation emissive(String key) {
        RegisteredTexture value = EMISSIVE.get(key);
        return value == null ? null : value.location();
    }

    static void putEmissive(String key, ResourceLocation location,
            DynamicTexture texture, Minecraft minecraft) {
        EMISSIVE.put(key, new RegisteredTexture(location, texture));
        trim(EMISSIVE, MAX_CACHE_SIZE, minecraft);
    }

    static boolean noEmissive(String key) {
        return NO_EMISSIVE.contains(key);
    }

    static void markNoEmissive(String key) {
        NO_EMISSIVE.add(key);
        while (NO_EMISSIVE.size() > MAX_CACHE_SIZE) {
            Iterator<String> iterator = NO_EMISSIVE.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    static ResourceLocation durability(String key) {
        RegisteredTexture value = DURABILITY.get(key);
        return value == null ? null : value.location();
    }

    static void putDurability(String key, ResourceLocation location,
            DynamicTexture texture, Minecraft minecraft) {
        DURABILITY.put(key, new RegisteredTexture(location, texture));
        trim(DURABILITY, MAX_DURABILITY_CACHE_SIZE, minecraft);
    }

    static synchronized NativeImage copyAtlas(ResourceManager resources) throws IOException {
        if (atlasTemplate == null) {
            Resource resource = resources.getResourceOrThrow(TEMPLATE);
            atlasTemplate = loadAtlas(resource);
        }
        NativeImage copy = new NativeImage(
                GENERATED_ATLAS_SIZE, GENERATED_ATLAS_SIZE, true);
        copy.copyFrom(atlasTemplate);
        return copy;
    }

    static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        closeAll(MATERIALS, minecraft);
        closeAll(EMISSIVE, minecraft);
        closeAll(DURABILITY, minecraft);
        NO_EMISSIVE.clear();
        if (atlasTemplate != null) {
            atlasTemplate.close();
            atlasTemplate = null;
        }
    }

    private static void trim(
            Map<String, RegisteredTexture> values, int maximum,
            Minecraft minecraft) {
        while (values.size() > maximum) {
            Iterator<RegisteredTexture> iterator = values.values().iterator();
            RegisteredTexture oldest = iterator.next();
            iterator.remove();
            minecraft.getTextureManager().release(oldest.location());
        }
    }

    private static void closeAll(
            Map<String, RegisteredTexture> values, Minecraft minecraft) {
        for (RegisteredTexture texture : values.values()) {
            minecraft.getTextureManager().release(texture.location());
        }
        values.clear();
    }

    private static NativeImage loadAtlas(Resource resource) throws IOException {
        NativeImage source;
        try (InputStream stream = resource.open()) {
            source = NativeImage.read(stream);
        }
        if (source.getWidth() == GENERATED_ATLAS_SIZE
                && source.getHeight() == GENERATED_ATLAS_SIZE) {
            return source;
        }

        NativeImage target = new NativeImage(
                GENERATED_ATLAS_SIZE, GENERATED_ATLAS_SIZE, true);
        try {
            for (int y = 0; y < GENERATED_ATLAS_SIZE; y++) {
                int sourceY = Math.min(
                        source.getHeight() - 1,
                        Math.round((y + 0.5F) * source.getHeight()
                                / GENERATED_ATLAS_SIZE - 0.5F));
                for (int x = 0; x < GENERATED_ATLAS_SIZE; x++) {
                    int sourceX = Math.min(
                            source.getWidth() - 1,
                            Math.round((x + 0.5F) * source.getWidth()
                                    / GENERATED_ATLAS_SIZE - 0.5F));
                    target.setPixelRGBA(x, y, source.getPixelRGBA(sourceX, sourceY));
                }
            }
        } finally {
            source.close();
        }
        return target;
    }

    private record RegisteredTexture(
            ResourceLocation location,
            DynamicTexture texture) {
    }

    private MaterialTextureCache() {
    }
}
