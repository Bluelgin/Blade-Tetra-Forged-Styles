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
    public record Bake(byte[] model, byte[] texture, List<ResourceLocation> sourceTextures,
                       Map<String, List<ResourceLocation>> selectedInputs) {}
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
        // The semantic source can be a descriptor. Freeze the OBJ actually selected by
        // Model Assembly 2.0 (including any installed JSON adapter) into the certificate.
        var selectedInputs = new LinkedHashMap<String, List<ResourceLocation>>();
        for (var entry : named.entrySet()) {
            var kind = entry.getValue();
            var rules = LegacyModelRules.load(resources, kind);
            ResourceLocation actualGeometry = rules.geometryModel(kind.model());
            if (!permitted(actualGeometry) || !permitted(kind.texture()))
                throw new IOException("Named OBJ/texture needs audited embedding rights: " + kind.id());

            var paths = new ArrayList<ResourceLocation>();
            paths.add(actualGeometry);
            paths.add(kind.texture());
            String prefix = "bladetetra/legacy_models/" + kind.model().getPath();
            var defaultRules = ResourceLocation.fromNamespaceAndPath(kind.model().getNamespace(),
                    prefix + ".json");
            var namedRules = ResourceLocation.fromNamespaceAndPath(kind.model().getNamespace(),
                    prefix + "/" + kind.name().getNamespace() + "/" + kind.name().getPath() + ".json");
            for (var adapter : List.of(defaultRules, namedRules))
                if (resources.getResource(adapter).isPresent()) paths.add(adapter);
            String slot = switch (entry.getKey()) {
                case BLADE -> "blade";
                case HILT -> "tsuba";
                case SAYA -> "saya";
            };
            selectedInputs.put(slot, List.copyOf(paths));
        }

        WavefrontObject raw;
        try (var stream = resources.getResourceOrThrow(modelId).open()) {
            raw = new WavefrontObject(modelId.toString(), stream);
        }
        var profile = MaterialAppearance.fromStack(stack).tsubaProfile();
        var world = ForgedGuardModel.forProfile(raw, profile);
        var icon = ForgedGuardModel.forProfile(InventoryBladeModel.forTarget(raw, "item_blade"), profile);
        var calibratedSheath = NamedSheathRender.resolve(blade, parts.saya(), world);
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
                    if (state.equals("sheath") && calibratedSheath != null) {
                        var views = calibratedSheath.views();
                        if (calibratedSheath.stockTexture()) {
                            append(output, state, stockFaces(faces(views.base()), stock, width, height, false));
                            append(output, state + "_luminous",
                                    stockFaces(faces(views.luminous()), stock, width, height, false));
                        } else {
                            var tile = locations.get(parts.saya().texture());
                            append(output, state, assembled(views.base(), null, tile, width, height));
                            append(output, state + "_luminous",
                                    assembled(views.luminous(), null, tile, width, height));
                        }
                        continue;
                    }
                    var target = inventory ? icon : world;
                    for (var component : components(state)) {
                        var kind = named.get(component);
                        var mounted = kind == null ? null : NamedBladeRenderPlan.mounted(
                                LegacyRenderSources.resolve(kind), component, target, state);
                        if (mounted == null) {
                            var fallback = LegacyModelPartRenderer.modularFaces(target, state, stockPart(component));
                            if (kind != null && List.of("blade", "sheath", "item_blade").contains(state)) {
                                throw new IOException("Cannot embed a named source without its required "
                                        + state + " geometry: " + kind.id());
                            }
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
                // Keep every native non-replaced group (in particular 'effect') rather
                // than silently deleting original SlashBlade rendering geometry.
                for (var group : world.groupObjects) {
                    if (group == null || group.faces == null || group.faces.isEmpty()
                            || InventoryBladeModel.isIcon(group.name) || output.containsKey(group.name)
                            || STATES.contains(group.name)) continue;
                    append(output, group.name, stockFaces(group.faces, stock, width, height, false));
                }
                for (var group : icon.groupObjects) {
                    if (group == null || group.faces == null || group.faces.isEmpty()
                            || !InventoryBladeModel.isIcon(group.name) || output.containsKey(group.name)
                            || STATES.contains(group.name)) continue;
                    append(output, group.name, stockFaces(group.faces, stock, width, height, true));
                }
                if (!output.containsKey("blade") || !output.containsKey("sheath")
                        || !output.containsKey("item_blade")) throw new IOException("Incomplete sealed model groups");
                byte[] model = RuntimeWavefrontViewFactory.serialize(output).getBytes(StandardCharsets.UTF_8);
                if (model.length > 8 * 1024 * 1024) throw new IOException("Sealed OBJ exceeds archive asset limit");
                return new Bake(model, atlas.asByteArray(), List.copyOf(textures.keySet()),
                        Map.copyOf(selectedInputs));
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
        var matrix = mount == null ? new org.joml.Matrix4f() : mount.matrix();
        // Nonuniform source fitting requires an inverse-transpose normal matrix.
        // Recomputing all OBJ normals from triangles changes authored smooth shading.
        var normalMatrix = matrix.normal(new org.joml.Matrix3f());
        for (var group : view.groupObjects) for (var input : group.faces) {
            var face = new Face();
            face.vertices = new Vertex[input.vertices.length];
            face.vertexNormals = input.vertexNormals == null ? null : new Vertex[input.vertices.length];
            face.textureCoordinates = new TextureCoordinate[input.vertices.length];
            for (int i = 0; i < input.vertices.length; i++) {
                var v = input.vertices[i];
                var point = matrix.transformPosition(new Vector3f(v.x, v.y, v.z));
                if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z))
                    throw new IOException("Named source has a non-finite mounted vertex");
                face.vertices[i] = new Vertex(point.x, point.y, point.z);
                if (face.vertexNormals != null && i < input.vertexNormals.length
                        && input.vertexNormals[i] != null) {
                    var nativeNormal = input.vertexNormals[i];
                    var normal = normalMatrix.transform(new Vector3f(
                            nativeNormal.x, nativeNormal.y, nativeNormal.z)).normalize();
                    if (!Float.isFinite(normal.x) || !Float.isFinite(normal.y)
                            || !Float.isFinite(normal.z)) throw new IOException("Invalid source normal");
                    face.vertexNormals[i] = new Vertex(normal.x, normal.y, normal.z);
                }
                if (input.textureCoordinates == null || i >= input.textureCoordinates.length
                        || input.textureCoordinates[i] == null)
                    throw new IOException("Named source has an untextured face");
                var uv = input.textureCoordinates[i];
                // A packed atlas cannot preserve source tile-repeat semantics without
                // splitting every triangle crossing a UV tile boundary.
                if (!Float.isFinite(uv.u) || !Float.isFinite(uv.v)
                        || uv.u < 0 || uv.u > 1 || uv.v < 0 || uv.v > 1)
                    throw new IOException("Named source UV repeat/overflow is not portable yet");
                face.textureCoordinates[i] = new TextureCoordinate(
                        (tile.x() + uv.u * tile.image().getWidth()) / width,
                        uv.v * tile.image().getHeight() / height);
            }
            out.add(face);
        }
        return out;
    }
    private static List<Face> faces(WavefrontObject view) {
        if (view == null) return List.of();
        return view.groupObjects.stream().flatMap(group -> group.faces.stream()).toList();
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
                if (uv == null || !Float.isFinite(uv.u) || !Float.isFinite(uv.v))
                    throw new IllegalArgumentException("Stock model contains an untextured face");
                face.textureCoordinates[i] = new TextureCoordinate(
                        (uv.u * stock.getWidth()) / width,
                        (uv.v * stock.getHeight() / 2f
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
