package dev.bladetetra.export;

import dev.bladetetra.client.StaticBladeArt;
import dev.bladetetra.client.SaltyFishExportArt;
import dev.bladetetra.client.NamedBladeExportAssembler;
import dev.bladetetra.forging.NamedBackplate;
import java.util.LinkedHashMap;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.visual.MaterialAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record BladeExportArt(ResourceLocation model, ResourceLocation texture,
                      List<ExportResourceEvidence> inputs, Map<ResourceLocation, byte[]> assets,
                      Map<String, List<ResourceLocation>> namedInputs) {
    BladeExportArt(ResourceLocation model, ResourceLocation texture,
                   List<ExportResourceEvidence> inputs, Map<ResourceLocation, byte[]> assets) {
        this(model, texture, inputs, assets, Map.of());
    }
    static BladeExportArt bake(String modId, BladeExportSnapshot snapshot,
            ItemStack stack, ResourceManager resources) throws IOException {
        return bake(modId, snapshot, stack, resources, CustomBackplateImage.Selection.original());
    }

    static BladeExportArt bake(String modId, BladeExportSnapshot snapshot,
            ItemStack stack, ResourceManager resources, CustomBackplateImage.Selection backplate) throws IOException {
        check(snapshot, stack);
        var sourceModel = snapshot.model();
        var tracked = new ExportArtResources(resources);
        resources = tracked;
        byte[] bakedModel, bakedTexture;
        Map<String, List<ResourceLocation>> namedInputs = Map.of();
        if (fishAssembly(stack)) {
            var fish = SaltyFishExportArt.bake(stack, sourceModel, resources);
            bakedModel = fish.model();
            bakedTexture = fish.texture();
            for (var input : fish.sources()) tracked.include(input);
            namedInputs = fish.namedInputs();
            for (var ids : namedInputs.values()) for (var id : ids) tracked.include(id);
        } else if (snapshot.namedParts().stream().anyMatch(part -> !part.slot().equals("backplate"))) {
            var assembled = NamedBladeExportAssembler.bake(stack, sourceModel, resources);
            bakedModel = assembled.model();
            bakedTexture = assembled.texture();
            namedInputs = assembled.selectedInputs();
            var verified = new java.util.LinkedHashSet<ResourceLocation>();
            for (var selected : namedInputs.values()) verified.addAll(selected);
            for (var id : verified) tracked.include(id);
        } else {
            var artwork = StaticBladeArt.bake(stack, sourceModel, resources);
            bakedModel = artwork.model();
            bakedTexture = artwork.texture();
        }
        var model = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_blade.obj");
        var texture = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_blade.png");
        var assets = new LinkedHashMap<ResourceLocation, byte[]>();
        assets.put(model, bakedModel);
        assets.put(texture, bakedTexture);
        var portrait = CustomBackplateImage.bake(modId, backplate, stack, resources);
        var fittingInputs = new LinkedHashMap<String, List<ResourceLocation>>(namedInputs);
        if (portrait != null) {
            if (backplate.mode() != CustomBackplateImage.Mode.REPLACE && NamedBackplate.sourceId(stack) == null) {
                var tintSource = ResourceLocation.fromNamespaceAndPath("slashblade", "model/rodai_netherite.png");
                if (resources.getResource(tintSource).isPresent()) tracked.include(tintSource);
            }
            var frame = ResourceLocation.parse("blade_tetra:model/util/durability_filled.obj");
            var picture = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_backplate.png");
            var frameOutput = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_backplate_frame.obj");
            try (var input = resources.getResourceOrThrow(frame).open()) {
                assets.put(frameOutput, input.readAllBytes());
            }
            assets.put(picture, portrait.png());
            var source = snapshot.namedParts().stream().filter(part -> "backplate".equals(part.slot()))
                    .findFirst().orElse(null);
            if (source != null) {
                fittingInputs.put("backplate", List.of(picture));
            }
        }
        var evidence = new ArrayList<>(tracked.inputs());
        if (portrait != null) evidence.add(portrait.evidence());
        return new BladeExportArt(model, texture, List.copyOf(evidence), Map.copyOf(assets),
                Map.copyOf(fittingInputs));
    }

    static void check(BladeExportSnapshot snapshot, ItemStack stack) {
        ResourceLocation sourceModel = snapshot.model();
        if (!"blade_tetra".equals(sourceModel.getNamespace())
                || !sourceModel.getPath().matches("model/modular/alpha9/[a-z0-9_]+\\.obj")) {
            throw new IllegalArgumentException("No static exporter for model " + sourceModel);
        }
        var appearance = MaterialAppearance.fromStack(stack);
        if (appearance.sayaSkin().patternCount() > 0 && (SaltyFishParts.saya(stack) != null
                || snapshot.namedParts().stream().anyMatch(part -> "saya".equals(part.slot())))) {
            throw new IllegalArgumentException("Banner patterns require a modular saya atlas in sealed blades");
        }
        for (var part : snapshot.namedParts()) {
            // A missing original portrait does not prevent the player replacing it with
            // their own image; original/overlay is validated on the client at bake time.
            if ("backplate".equals(part.slot())) continue;
            if (part.namedBlade() == null || part.declaredModel() == null || part.declaredTexture() == null)
                throw new IllegalArgumentException("Missing named source: " + part.sourceId());
        }
    }

    private static boolean fishAssembly(ItemStack stack) {
        return SaltyFishParts.blade(stack) != null || SaltyFishParts.saya(stack) != null
                || SaltyFishParts.salvagedGuard(stack) != null;
    }

    boolean requiresResourceNotice(BladeExportArchive.Metadata metadata) {
        return metadata.logo().length > 0 || inputs.stream().anyMatch(ExportResourceEvidence::externalEmbedding);
    }
}
