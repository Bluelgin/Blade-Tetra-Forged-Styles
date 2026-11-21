package dev.bladetetra.export;

import dev.bladetetra.client.StaticBladeArt;
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
                      List<ExportResourceEvidence> inputs, Map<ResourceLocation, byte[]> assets) {
    private static final ResourceLocation TEMPLATE = ResourceLocation.parse("blade_tetra:model/modular/standard_256.png");

    static BladeExportArt bake(String modId, BladeExportSnapshot snapshot,
            ItemStack stack, ResourceManager resources) throws IOException {
        return bake(modId, snapshot, stack, resources, CustomBackplateImage.Selection.original());
    }

    static BladeExportArt bake(String modId, BladeExportSnapshot snapshot,
            ItemStack stack, ResourceManager resources, CustomBackplateImage.Selection backplate) throws IOException {
        check(snapshot, stack);
        var sourceModel = snapshot.model();
        var appearance = MaterialAppearance.fromStack(stack);
        var evidence = new ArrayList<ExportResourceEvidence>();
        evidence.add(projectInput(resources, sourceModel));
        evidence.add(projectInput(resources, TEMPLATE));
        if (appearance.sayaPreset().present()) {
            evidence.add(projectInput(resources, appearance.sayaPreset().textureLocation()));
        }
        var artwork = StaticBladeArt.bake(stack, sourceModel, resources);
        var model = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_blade.obj");
        var texture = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_blade.png");
        var assets = new LinkedHashMap<ResourceLocation, byte[]>();
        assets.put(model, artwork.model());
        assets.put(texture, artwork.texture());
        var portrait = CustomBackplateImage.bake(modId, backplate, stack, resources);
        if (portrait != null) {
            var frame = ResourceLocation.parse("blade_tetra:model/util/durability_filled.obj");
            evidence.add(projectInput(resources, frame));
            var picture = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_backplate.png");
            var frameOutput = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_backplate_frame.obj");
            try (var input = resources.getResourceOrThrow(frame).open()) {
                assets.put(frameOutput, input.readAllBytes());
            }
            assets.put(picture, portrait.png());
            evidence.add(portrait.evidence());
        }
        return new BladeExportArt(model, texture, List.copyOf(evidence), Map.copyOf(assets));
    }

    static void check(BladeExportSnapshot snapshot, ItemStack stack) {
        ResourceLocation sourceModel = snapshot.model();
        if (!"blade_tetra".equals(sourceModel.getNamespace())
                || !sourceModel.getPath().matches("model/modular/alpha9/[a-z0-9_]+\\.obj")) {
            throw new IllegalArgumentException("No static exporter for model " + sourceModel);
        }
        var appearance = MaterialAppearance.fromStack(stack);
        if (appearance.sayaSkin().patternCount() > 0) {
            throw new IllegalArgumentException("Banner-pattern saya is not supported by the static exporter");
        }
        if (snapshot.namedParts().stream().anyMatch(part -> !part.slot().equals("backplate"))) {
            throw new IllegalArgumentException("Named fitting artwork requires a portable model and redistribution permission: "
                    + String.join(", ", snapshot.namedParts().stream().map(part -> part.slot() + " " + part.sourceId()).toList()));
        }
        if (SaltyFishParts.blade(stack) != null || SaltyFishParts.saya(stack) != null
                || SaltyFishParts.salvagedGuard(stack) != null) {
            throw new IllegalArgumentException("Salty Fish geometry is not supported by the static exporter");
        }
    }

    private static ExportResourceEvidence projectInput(ResourceManager resources, ResourceLocation id) throws IOException {
        var verified = ExportResourceEvidence.reference(resources, id, "blade_tetra");
        return new ExportResourceEvidence(id, verified.packId(), verified.ownerModId(), verified.ownerModVersion(),
                verified.sha256(),
                "Blade Tetra authored model/atlas input; consult bundled project license and asset notices; private use only",
                true);
    }
}
