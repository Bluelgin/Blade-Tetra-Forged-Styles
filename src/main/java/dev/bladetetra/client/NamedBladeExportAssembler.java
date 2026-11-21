package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.visual.MaterialAppearance;
import mods.flammpfeil.slashblade.client.renderer.model.obj.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Flatten named parts through the same socket pipeline as the live client. */
public final class NamedBladeExportAssembler {
    public record Bake(byte[] model, byte[] texture, List<ResourceLocation> sourceTextures) {}
    private record Tile(NativeImage image, int x) {}
    private static final List<String> STATES = List.of("blade", "blade_damaged", "blade_fragment",
            "sheath", "item_blade", "item_bladens", "item_damaged");
    public static Bake bake(ItemStack stack, ResourceLocation modelId, ResourceManager resources) throws IOException {
        var parts = NamedLegacyParts.fromStack(stack);
        var named = new EnumMap<LegacySourceMesh.Component, LegacyImprintKind>(LegacySourceMesh.Component.class);
        var blade = NamedLegacyParts.bladeFromStack(stack);
        if (blade != null) named.put(LegacySourceMesh.Component.BLADE, blade);
        if (parts.tsuba() != null) named.put(LegacySourceMesh.Component.HILT, parts.tsuba());
        if (parts.saya() != null) named.put(LegacySourceMesh.Component.SAYA, parts.saya());
        if (named.isEmpty()) throw new IOException("Missing named model sources");
        for (var kind : named.values()) if (!permitted(kind.model()) || !permitted(kind.texture()))
            throw new IOException("Third-party OBJ requires documented embedding rights: " + kind.id());

        WavefrontObject raw;
        try (var stream = resources.getResourceOrThrow(modelId).open()) {
            raw = new WavefrontObject(modelId.toString(), stream);
        }
        var profile = MaterialAppearance.fromStack(stack).tsubaProfile();
        var world = ForgedGuardModel.forProfile(raw, profile);
        var icon = ForgedGuardModel.forProfile(InventoryBladeModel.forTarget(raw, "item_blade"), profile);
        var textures = new LinkedHashMap<ResourceLocation, NativeImage>();
        try (var stock = NativeImage.read(new ByteArrayInputStream(MaterialTextureManager.bakeCombinedAtlas(stack, resources)))) {
            int width = stock.getWidth(), height = stock.getHeight();
            for (var source : named.values()) if (!textures.containsKey(source.texture())) {
                try (var stream = resources.getResourceOrThrow(source.texture()).open()) {
                    var image = NativeImage.read(stream);
                    if (image.getWidth() > 2048 || image.getHeight() > 2048) {
                        image.close();
                        throw new IOException("Source atlas exceeds 2048 pixels");
                    }
                    textures.put(source.texture(), image);
                    width += image.getWidth();
                    height = Math.max(height, image.getHeight());
                }
            }
            if (width > 4096 || height > 4096 || (long)width * height > 8_388_608L)
                throw new IOException("Sealed model atlas exceeds budget");
            var locations = new LinkedHashMap<ResourceLocation, Tile>();
            int x = stock.getWidth();
            for (var entry : textures.entrySet()) {
                locations.put(entry.getKey(), new Tile(entry.getValue(), x));
                x += entry.getValue().getWidth();
            }
            try (var atlas = new NativeImage(width, height, true)) {
                copy(atlas, stock, 0);
                for (var tile : locations.values()) copy(atlas, tile.image(), tile.x());
                var output = new LinkedHashMap<String, List<Face>>();
                for (String state : STATES) {
                    boolean inventory = state.startsWith("item_");
                    var target = inventory ? icon : world;
                    for (var component : components(state)) {
                        var kind = named.get(component);
                        var mounted = kind == null ? null : NamedBladeRenderPlan.mounted(
                                LegacyRenderSources.resolve(kind), component, target, state);
                        if (mounted == null) {
                            var fallback = LegacyModelPartRenderer.modularFaces(target, state, stockPart(component));
                            if (fallback.isEmpty() && kind != null && !state.equals("blade_fragment"))
                                throw new IOException("Missing model fallback for " + kind.id() + " / " + state);
                            append(output, state, stockFaces(fallback, stock, width, height, inventory));
                        } else {
                            var tile = locations.get(kind.texture());
                            append(output, state, assembled(mounted.views().base(), mounted.mount(), tile, width, height));
                            append(output, state + "_luminous",
                                    assembled(mounted.views().luminous(), mounted.mount(), tile, width, height));
                        }
                    }
                }
                if (!output.containsKey("blade") || !output.containsKey("sheath")
                        || !output.containsKey("item_blade")) throw new IOException("Incomplete sealed model groups");
                return new Bake(RuntimeWavefrontViewFactory.serialize(output).getBytes(StandardCharsets.UTF_8),
                        atlas.asByteArray(), List.copyOf(textures.keySet()));
            }
        } finally {
            textures.values().forEach(NativeImage::close);
        }
    }
    private static boolean permitted(ResourceLocation id) {
        return Set.of("slashblade", "blade_tetra").contains(id.getNamespace());
    }
    private static List<LegacySourceMesh.Component> components(String state) {
        return switch (state) {
            case "sheath" -> List.of(LegacySourceMesh.Component.SAYA);
            case "blade_fragment" -> List.of(LegacySourceMesh.Component.BLADE);
            case "item_blade" -> List.of(LegacySourceMesh.Component.BLADE,
                    LegacySourceMesh.Component.HILT, LegacySourceMesh.Component.SAYA);
            default -> List.of(LegacySourceMesh.Component.BLADE, LegacySourceMesh.Component.HILT);
        };
    }
    private static LegacyModelPartRenderer.Part stockPart(LegacySourceMesh.Component part) {
        return switch (part) {
            case BLADE -> LegacyModelPartRenderer.Part.BLADE;
            case HILT -> LegacyModelPartRenderer.Part.HILT;
            case SAYA -> LegacyModelPartRenderer.Part.SAYA;
        };
    }
    private static List<Face> assembled(WavefrontObject view, LegacyMeshFrame.Mount mount,
            Tile tile, int width, int height) throws IOException {
        if (view == null) return List.of();
        var out = new ArrayList<Face>();
        var matrix = mount.matrix();
        for (var group : view.groupObjects) for (var input : group.faces) {
            var face = new Face();
            face.vertices = new Vertex[input.vertices.length];
            face.textureCoordinates = new TextureCoordinate[input.vertices.length];
            for (int i = 0; i < input.vertices.length; i++) {
                var v = input.vertices[i];
                var point = matrix.transformPosition(new Vector3f(v.x, v.y, v.z));
                face.vertices[i] = new Vertex(point.x, point.y, point.z);
                if (input.textureCoordinates == null || i >= input.textureCoordinates.length
                        || input.textureCoordinates[i] == null)
                    throw new IOException("Named source has an untextured face");
                var uv = input.textureCoordinates[i];
                face.textureCoordinates[i] = new TextureCoordinate(
                        (tile.x() + uv.u * tile.image().getWidth()) / width,
                        uv.v * tile.image().getHeight() / height);
            }
            out.add(face);
        }
        return out;
    }
    private static List<Face> stockFaces(List<Face> faces, NativeImage stock,
            int width, int height, boolean inventory) {
        var out = new ArrayList<Face>();
        for (var input : faces) {
            var face = new Face();
            face.vertices = input.vertices;
            face.vertexNormals = input.vertexNormals;
            face.faceNormal = input.faceNormal;
            face.textureCoordinates = new TextureCoordinate[input.vertices.length];
            for (int i = 0; i < input.vertices.length; i++) {
                var uv = input.textureCoordinates == null ? null : input.textureCoordinates[i];
                face.textureCoordinates[i] = new TextureCoordinate(
                        (uv == null ? 0 : uv.u * stock.getWidth()) / width,
                        (uv == null ? 0 : uv.v * stock.getHeight() / 2f
                                + (inventory ? stock.getHeight() / 2f : 0)) / height);
            }
            out.add(face);
        }
        return out;
    }
    private static void append(Map<String, List<Face>> all, String name, List<Face> faces) {
        if (!faces.isEmpty()) all.computeIfAbsent(name, k -> new ArrayList<>()).addAll(faces);
    }
    private static void copy(NativeImage dst, NativeImage src, int dx) {
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++)
            dst.setPixelRGBA(dx + x, y, src.getPixelRGBA(x, y));
    }
    private NamedBladeExportAssembler() {}
}
