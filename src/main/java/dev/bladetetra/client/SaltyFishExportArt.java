package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.visual.MaterialAppearance;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.bladetetra.client.LegacyModelPartRenderer.*;

public final class SaltyFishExportArt {
    private static final List<String> TARGETS = List.of("blade", "blade_damaged", "blade_fragment", "sheath",
            "item_blade", "item_bladens", "item_damaged");

    public record Bake(byte[] model, byte[] texture, List<ResourceLocation> sources,
                       Map<String, List<ResourceLocation>> namedInputs) {}
    private record Fitting(SaltyFishParts.Length length, ResourceLocation modelId, ResourceLocation textureId,
                           WavefrontObject mesh) {}

    public static Bake bake(ItemStack stack, ResourceLocation baseModel, ResourceManager resources) throws IOException {
        var raw = read(resources, baseModel);
        var stockMesh = ForgedGuardModel.forProfile(raw, MaterialAppearance.fromStack(stack).tsubaProfile());
        var blade = fitting(resources, stack, SaltyFishParts.blade(stack), "blade");
        var saya = fitting(resources, stack, SaltyFishParts.saya(stack), "saya");
        LegacyImprintKind namedGuard = NamedLegacyParts.fromStack(stack).tsuba();
        LegacyImprintKind guard = namedGuard == null ? SaltyFishParts.salvagedGuard(stack) : namedGuard;
        var inheritedBlade = blade == null ? NamedLegacyParts.bladeFromStack(stack) : null;
        var inheritedSaya = saya == null ? NamedLegacyParts.fromStack(stack).saya() : null;
        var named = new LinkedHashMap<String, LegacyImprintKind>();
        if (guard != null) named.put("tsuba", guard);
        if (inheritedBlade != null) named.put("blade", inheritedBlade);
        if (inheritedSaya != null) named.put("saya", inheritedSaya);
        var inputs = new LinkedHashMap<String, List<ResourceLocation>>();
        for (var entry : named.entrySet()) {
            inputs.put(entry.getKey(), NamedBladeExportAssembler.sourceInputs(resources, entry.getValue()));
        }
        var sources = new ArrayList<ResourceLocation>();
        var textures = new LinkedHashMap<ResourceLocation, NativeImage>();
        try (var stock = NativeImage.read(new ByteArrayInputStream(MaterialTextureManager.bakeCombinedAtlas(stack, resources)));
             var bladeImage = texture(resources, blade, sources);
             var sayaImage = texture(resources, saya, sources)) {
            int bladeX = stock.getWidth();
            int sayaX = bladeX + (bladeImage == null ? 0 : bladeImage.getWidth());
            int width = sayaX + (sayaImage == null ? 0 : sayaImage.getWidth());
            int height = Math.max(stock.getHeight(), Math.max(
                    bladeImage == null ? 0 : bladeImage.getHeight(), sayaImage == null ? 0 : sayaImage.getHeight()));
            var tiles = new LinkedHashMap<ResourceLocation, NamedBladeExportAssembler.Tile>();
            for (var kind : named.values()) {
                var id = kind.texture();
                if (textures.containsKey(id)) continue;
                var image = texture(resources, id);
                textures.put(id, image);
                tiles.put(id, tile(image, width));
                width += image.getWidth();
                height = Math.max(height, image.getHeight());
            }
            if (width > 4096 || height > 4096 || (long) width * height > 8_388_608L) {
                throw new IOException("Salty Fish atlas exceeds budget");
            }

            try (var atlas = new NativeImage(width, height, true)) {
                NamedBladeExportAssembler.copy(atlas, stock, 0);
                if (bladeImage != null) NamedBladeExportAssembler.copy(atlas, bladeImage, bladeX);
                if (sayaImage != null) NamedBladeExportAssembler.copy(atlas, sayaImage, sayaX);
                textures.forEach((id, image) -> NamedBladeExportAssembler.copy(atlas, image, tiles.get(id).x()));
                var guardTile = guard == null ? null : tiles.get(guard.texture());
                var stockTile = new NamedBladeExportAssembler.Tile(stock.getWidth(), stock.getHeight() / 2, 0);
                var bladeTile = tile(bladeImage, bladeX);
                var sayaTile = tile(sayaImage, sayaX);
                var output = new LinkedHashMap<String, List<Face>>();
                for (String target : TARGETS) {
                    var pose = new Matrix4f();
                    var faces = new ArrayList<Face>();
                    var luminous = new ArrayList<Face>();
                    if (!"sheath".equals(target)) {
                        boolean icon = InventoryBladeModel.isIcon(target);
                        String group = "item_damaged".equals(target) ? "blade_damaged" : icon ? "blade" : target;
                        boolean cover = "item_blade".equals(target);
                        if (icon) {
                            var frame = fishIconFrame(raw, blade == null ? null : blade.length(),
                                    saya == null ? null : saya.length(), group, cover);
                            pose.translate(0, 0, 16).rotateZ((float) Math.toRadians(135))
                                    .scale(frame.scale()).translate(frame.offset(), -4, 0);
                        }
                        Float coveredTo = cover ? fishCover(raw, saya == null ? null : saya.length()) : null;
                        var fittedGuard = fishGuard(guard, group);
                        var guardFaces = fittedGuard == null ? List.<Face>of() : faces(fittedGuard, group);
                        if (guard != null && guardFaces.isEmpty() && !"blade_fragment".equals(group)) {
                            throw new IOException("Missing Salty Fish guard geometry for " + guard.id());
                        }
                        var mountedBlade = coveredTo == null
                                ? mount(inheritedBlade, LegacySourceMesh.Component.BLADE, raw, group) : null;
                        faces.addAll(NamedBladeExportAssembler.remap(
                                stockFishFaces(stockMesh, group, blade != null || mountedBlade != null,
                                        !guardFaces.isEmpty(), coveredTo), pose,
                                stockTile, width, height));
                        if (blade != null) {
                            faces.addAll(NamedBladeExportAssembler.remap(
                                    fishPartFaces(blade.mesh(), blade.length(), group, coveredTo), pose,
                                    bladeTile, width, height));
                        }
                        if (mountedBlade != null) appendNamed(faces, luminous, pose, mountedBlade,
                                tiles.get(inheritedBlade.texture()), width, height);
                        faces.addAll(NamedBladeExportAssembler.remap(guardFaces, pose, guardTile, width, height));
                    }
                    if ("sheath".equals(target) || "item_blade".equals(target)) {
                        var mountedSaya = mount(inheritedSaya, LegacySourceMesh.Component.SAYA, raw, "sheath");
                        if (mountedSaya != null) {
                            appendNamed(faces, luminous, pose, mountedSaya, tiles.get(inheritedSaya.texture()), width, height);
                        } else {
                            faces.addAll(NamedBladeExportAssembler.remap(
                                    faces(saya == null ? stockMesh : saya.mesh(), "sheath"), pose,
                                    saya == null ? stockTile : sayaTile, width, height));
                        }
                    }
                    if (!faces.isEmpty()) output.put(target, faces);
                    if (!luminous.isEmpty()) output.put(target + "_luminous", luminous);
                }
                for (var group : stockMesh.groupObjects) {
                    if (!TARGETS.contains(group.name) && !InventoryBladeModel.isIcon(group.name)
                            && !group.faces.isEmpty()) {
                        output.put(group.name, NamedBladeExportAssembler.stockFaces(group.faces, stock,
                                width, height, false));
                    }
                }
                if (!output.keySet().containsAll(List.of("blade", "blade_damaged", "sheath", "item_blade"))) {
                    throw new IOException("Incomplete Salty Fish model groups");
                }
                return new Bake(RuntimeWavefrontViewFactory.serialize(output).getBytes(StandardCharsets.UTF_8),
                        atlas.asByteArray(), List.copyOf(sources), Map.copyOf(inputs));
            }
        } finally {
            textures.values().forEach(NativeImage::close);
        }
    }

    private static NamedBladeRenderPlan.Mounted mount(LegacyImprintKind kind, LegacySourceMesh.Component part,
            WavefrontObject raw, String group) throws IOException {
        if (kind == null) return null;
        var mounted = NamedBladeRenderPlan.mounted(LegacyRenderSources.resolve(kind), part, raw, group);
        if (mounted == null && ("blade".equals(group) || "sheath".equals(group))) {
            throw new IOException("Cannot mount " + kind.id() + " on the Salty Fish " + group + " fitting");
        }
        return mounted;
    }

    private static void appendNamed(List<Face> base, List<Face> luminous, Matrix4f pose,
            NamedBladeRenderPlan.Mounted mounted, NamedBladeExportAssembler.Tile tile, int width, int height)
            throws IOException {
        var matrix = new Matrix4f(pose).mul(mounted.mount().matrix());
        for (var view : List.of(false, true)) {
            var mesh = view ? mounted.views().luminous() : mounted.views().base();
            if (mesh == null) continue;
            var output = view ? luminous : base;
            output.addAll(NamedBladeExportAssembler.remap(
                    mesh.groupObjects.stream().flatMap(group -> group.faces.stream()).toList(),
                    matrix, tile, width, height));
        }
    }

    private static Fitting fitting(ResourceManager resources, ItemStack stack, SaltyFishParts.Length length,
                                   String part) throws IOException {
        if (length == null) return null;
        var id = fishModel(length, part);
        return new Fitting(length, id, fishTexture(stack, length, part), read(resources, id));
    }

    private static WavefrontObject read(ResourceManager resources, ResourceLocation id) throws IOException {
        try (var input = resources.getResourceOrThrow(id).open()) {
            return new WavefrontObject(id.toString(), input);
        }
    }

    private static NativeImage texture(ResourceManager resources, Fitting fitting, List<ResourceLocation> sources)
            throws IOException {
        if (fitting == null) return null;
        sources.add(fitting.modelId());
        sources.add(fitting.textureId());
        return texture(resources, fitting.textureId());
    }

    private static NativeImage texture(ResourceManager resources, ResourceLocation id) throws IOException {
        try (var input = resources.getResourceOrThrow(id).open()) {
            return NativeImage.read(input);
        }
    }

    private static NamedBladeExportAssembler.Tile tile(NativeImage image, int x) {
        return image == null ? null : new NamedBladeExportAssembler.Tile(image.getWidth(), image.getHeight(), x);
    }

    private SaltyFishExportArt() {}
}
