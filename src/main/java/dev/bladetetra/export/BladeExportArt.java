package dev.bladetetra.export;

import dev.bladetetra.client.StaticBladeArt;
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
        byte[] bakedModel, bakedTexture;
        if (snapshot.namedParts().stream().anyMatch(part -> !part.slot().equals("backplate"))) {
            var assembled = NamedBladeExportAssembler.bake(stack, sourceModel, resources);
            bakedModel = assembled.model();
            bakedTexture = assembled.texture();
            for (var id : assembled.sourceTextures()) evidence.add(projectNamedInput(resources, id));
            for (var part : snapshot.namedParts()) if (!part.slot().equals("backplate")) {
                evidence.add(projectNamedInput(resources, part.declaredModel()));
            }
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
        for (var part : snapshot.namedParts()) {
            if (part.namedBlade() == null || part.declaredModel() == null || part.declaredTexture() == null)
                throw new IllegalArgumentException("Missing named source: " + part.sourceId());
            if (!part.slot().equals("backplate")
                    && (!java.util.Set.of("slashblade", "blade_tetra").contains(part.declaredModel().getNamespace())
                    || !java.util.Set.of("slashblade", "blade_tetra").contains(part.declaredTexture().getNamespace())))
                throw new IllegalArgumentException("Named source needs an audited embedding license: " + part.sourceId());
        }
        if (SaltyFishParts.blade(stack) != null || SaltyFishParts.saya(stack) != null
                || SaltyFishParts.salvagedGuard(stack) != null) {
            throw new IllegalArgumentException("Salty Fish geometry is not supported by the static exporter");
        }
    }

    private static ExportResourceEvidence projectNamedInput(ResourceManager resources, ResourceLocation id) throws IOException {
        var provider = id.getNamespace();
        if (!java.util.Set.of("slashblade", "blade_tetra").contains(provider))
            throw new IOException("External source embedding is not yet approved: " + id);
        var verified = ExportResourceEvidence.reference(resources, id, provider);
        return new ExportResourceEvidence(id, verified.packId(), verified.ownerModId(),
                verified.ownerModVersion(), verified.sha256(),
                "Native/project source embedded in private export; original license applies", true);
    }

    private static ExportResourceEvidence projectInput(ResourceManager resources, ResourceLocation id) throws IOException {
        var verified = ExportResourceEvidence.reference(resources, id, "blade_tetra");
        return new ExportResourceEvidence(id, verified.packId(), verified.ownerModId(), verified.ownerModVersion(),
                verified.sha256(),
                "Blade Tetra authored model/atlas input; consult bundled project license and asset notices; private use only",
                true);
    }
}
