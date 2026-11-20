package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.visual.MaterialAppearance;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.TextureCoordinate;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Freezes the live world and inventory projections into one native SlashBlade model and atlas. */
public final class StaticBladeArt {
    public record Bake(byte[] model, byte[] texture) {}

    public static Bake bake(ItemStack stack, ResourceLocation modelId, ResourceManager resources) throws IOException {
        var appearance = MaterialAppearance.fromStack(stack);
        WavefrontObject base;
        try (var input = resources.getResourceOrThrow(modelId).open()) {
            base = new WavefrontObject(modelId.toString(), input);
        }
        var world = ForgedGuardModel.forProfile(base, appearance.tsubaProfile());
        var items = ForgedGuardModel.forProfile(InventoryBladeModel.forTarget(base, "item_blade"),
                appearance.tsubaProfile());
        var groups = new LinkedHashMap<String, List<Face>>();
        for (var group : world.groupObjects) {
            if (!InventoryBladeModel.isIcon(group.name) && !group.faces.isEmpty()) {
                groups.put(group.name, half(group.faces, 0));
            }
        }
        for (var group : items.groupObjects) {
            if (InventoryBladeModel.isIcon(group.name) && !group.faces.isEmpty()) {
                groups.put(group.name, half(group.faces, .5F));
            }
        }
        if (!groups.containsKey("blade") || !groups.containsKey("sheath") || !groups.containsKey("item_blade")) {
            throw new IOException("Blade model does not provide the required native render groups: " + modelId);
        }
        byte[] obj = RuntimeWavefrontViewFactory.serialize(groups).getBytes(StandardCharsets.UTF_8);
        return new Bake(obj, MaterialTextureManager.bakeCombinedAtlas(stack, resources));
    }

    private static List<Face> half(List<Face> faces, float offset) {
        var result = new ArrayList<Face>(faces.size());
        for (Face source : faces) {
            Face copy = new Face();
            copy.vertices = source.vertices;
            copy.vertexNormals = source.vertexNormals;
            copy.faceNormal = source.faceNormal;
            if (source.textureCoordinates != null) {
                copy.textureCoordinates = new TextureCoordinate[source.textureCoordinates.length];
                for (int i = 0; i < source.textureCoordinates.length; i++) {
                    var uv = source.textureCoordinates[i];
                    if (uv != null) copy.textureCoordinates[i] = new TextureCoordinate(uv.u, uv.v * .5F + offset);
                }
            }
            result.add(copy);
        }
        return result;
    }

    private StaticBladeArt() {}
}
