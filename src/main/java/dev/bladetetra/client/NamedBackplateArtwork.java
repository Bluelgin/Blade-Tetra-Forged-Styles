package dev.bladetetra.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedBackplate;
import dev.bladetetra.item.NamedBladeIngotItem;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
final class NamedBackplateArtwork {
    private static final int SIZE = 256;
    private static final Cache<String, Boolean> MISSING = CacheBuilder.newBuilder().maximumSize(128).build();

    private static final Cache<String, Boolean> INGOT_SAMPLES = CacheBuilder.newBuilder().maximumSize(128).build();

    static ResourceLocation texture(String sourceId) {
        if (sourceId == null || MISSING.getIfPresent(sourceId) != null) return null;
        String key = "named-backplate|" + sourceId;
        var cached = MaterialTextureCache.durability(key);
        if (cached != null) return cached;

        var minecraft = Minecraft.getInstance();
        var source = NamedBladeIngotItem.sourceFromId(sourceId);
        if (source == null) return null;
        try {
            var artwork = bake(minecraft.getResourceManager(), source);
            var texture = new DynamicTexture(artwork);
            var location = MaterialTextureManager.durabilityBaseLocation(key, 0);
            minecraft.getTextureManager().register(location, texture);
            MaterialTextureCache.putDurability(key, location, texture, minecraft);
            return location;
        } catch (IOException | RuntimeException exception) {
            MISSING.put(sourceId, true);
            return null;
        }
    }

    static NativeImage bake(ResourceManager resources, LegacyImprintKind source) throws IOException {
        var override = override(resources, source);
        if (override != null) return override;

        try (var modelStream = resources.getResourceOrThrow(source.model()).open()) {
            var model = new WavefrontObject(source.model().toString(), modelStream);
            var faces = model.groupObjects.stream()
                    .filter(group -> "item_back".equals(group.name) || "backplate".equals(group.name))
                    .flatMap(group -> group.faces.stream()).limit(NamedBackplateProjection.MAX_FACES + 1L).toList();
            if (faces.isEmpty() || faces.size() > NamedBackplateProjection.MAX_FACES) {
                throw new IOException("Missing or oversized named backplate group");
            }
            try (var textureStream = resources.getResourceOrThrow(source.texture()).open();
                    var atlas = NativeImage.read(textureStream)) {
                return NamedBackplateProjection.bake(faces, atlas, SIZE);
            }
        }
    }

    private static NativeImage override(ResourceManager resources, LegacyImprintKind source) {
        var metadata = ResourceLocation.fromNamespaceAndPath(BladeTetra.MOD_ID,
                "named_backplate_visuals/" + source.name().getNamespace() + "/" + source.name().getPath() + ".json");
        var resource = resources.getResource(metadata);
        if (resource.isEmpty()) return null;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var texture = json.has("texture") ? ResourceLocation.tryParse(json.get("texture").getAsString()) : source.texture();
            if (texture == null) return null;
            double left = 0, top = 0, right = 1, bottom = 1;
            if (json.has("region")) {
                var region = json.getAsJsonArray("region");
                if (region.size() != 4) return null;
                left = region.get(0).getAsDouble();
                top = region.get(1).getAsDouble();
                right = region.get(2).getAsDouble();
                bottom = region.get(3).getAsDouble();
                if (!(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)) {
                    return null;
                }
            } else if (!json.has("texture")) {
                return null;
            }
            try (var stream = resources.getResourceOrThrow(texture).open();
                    var atlas = NativeImage.read(stream)) {
                return compose(atlas, left, top, right, bottom);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static NativeImage compose(NativeImage atlas, double u0, double v0, double u1, double v1) {
        var result = new NativeImage(SIZE, SIZE, true);
        double width = (u1 - u0) * atlas.getWidth();
        double height = (v1 - v0) * atlas.getHeight();
        double scale = SIZE * .94 / (width + height);
        double left = (SIZE - width * scale) / 2;
        double top = (SIZE - height * scale) / 2;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            double u = (x + .5 - left) / (width * scale);
            double v = (y + .5 - top) / (height * scale);
            if (u >= 0 && v >= 0 && u < 1 && v < 1) {
                int tx = Math.min(atlas.getWidth() - 1, (int) ((u0 + u * (u1 - u0)) * atlas.getWidth()));
                int ty = Math.min(atlas.getHeight() - 1, (int) ((v0 + v * (v1 - v0)) * atlas.getHeight()));
                result.setPixelRGBA(x, y, atlas.getPixelRGBA(tx, ty));
            }
        }
        return result;
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        String sourceId = NamedBackplate.sourceId(stack);
        boolean ingot = stack.getItem() instanceof NamedBladeIngotItem;
        if (ingot) sourceId = NamedBladeIngotItem.sourceId(stack);
        if (sourceId == null) return;
        var source = NamedBladeIngotItem.sourceFromId(sourceId);
        if (source == null || (ingot ? !hasIngotSample(source) : texture(sourceId) == null)) {
            event.getToolTip().add(Component.translatable("tooltip.blade_tetra.named_backplate.missing")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!ingot) {
            event.getToolTip().add(Component.translatable("tooltip.blade_tetra.named_backplate.source",
                    Component.translatable(source.translationKey())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean hasIngotSample(LegacyImprintKind source) {
        var available = INGOT_SAMPLES.getIfPresent(source.id());
        if (available != null) return available;
        // The ingot tooltip follows the same sample availability as before.
        try (var sample = NamedIngotArtwork.backplateSample(Minecraft.getInstance().getResourceManager(), source)) {
            available = true;
        } catch (IOException | RuntimeException ignored) {
            available = false;
        }
        INGOT_SAMPLES.put(source.id(), available);
        return available;
    }

    static void clear() {
        MISSING.invalidateAll();
        INGOT_SAMPLES.invalidateAll();
    }

    private NamedBackplateArtwork() {}
}
