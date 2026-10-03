package dev.bladetetra.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Optional;

/** Resource-only opt-in: a story mod supplies this descriptor; core never bundles one. */
public final class MikageDialoguePortraits {
    public static final ResourceLocation DESCRIPTOR = new ResourceLocation("blade_tetra", "dialogue/mikage_portraits.json");
    public record Portrait(ResourceLocation texture, int width, int height) {}

    public static Optional<Portrait> resolve(ResourceManager resources, String expression) {
        return resources.getResource(DESCRIPTOR).flatMap(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                int width = data.get("width").getAsInt();
                int height = data.get("height").getAsInt();
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return Optional.empty();
                JsonObject expressions = data.getAsJsonObject("expressions");
                String selected = expressions.has(expression) ? expression : "neutral";
                if (!expressions.has(selected)) return Optional.empty();
                ResourceLocation texture = ResourceLocation.tryParse(expressions.get(selected).getAsString());
                if (texture == null || resources.getResource(texture).isEmpty()) return Optional.empty();
                return Optional.of(new Portrait(texture, width, height));
            } catch (IOException | RuntimeException malformed) {
                // Optional content can fail without breaking the visitor conversation.
                return Optional.empty();
            }
        });
    }
    private MikageDialoguePortraits() {}
}
