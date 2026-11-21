package dev.bladetetra.export;

import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Projects supported combat into the standalone format and validates the appearance inputs. */
final class StandaloneBladeExport {
    static void checkCombat(BladeExportSnapshot snapshot) {
        if (snapshot.broken()) throw new IllegalArgumentException("Repair the blade before exporting");
        var missing = new ArrayList<String>();
        snapshot.inscriptions().forEach(value -> missing.add("soul " + value.name()));
        if (snapshot.forgedArt() != null) missing.add("forged Slash Art " + snapshot.forgedArt().core());
        if (snapshot.traditions().shoshin()) missing.add("Shoshin");
        if (snapshot.traditions().bairen()) missing.add("Bairen");
        if (snapshot.traditions().bansho()) missing.add("Bansho");
        if (snapshot.traditions().nbtSage()) missing.add("NBT Sage");
        if (snapshot.componentEffects().unsupported()) missing.add("boundary forging technique");
        if ("STANDARD".equals(snapshot.style()) && isSourceOnly(snapshot.combo())) {
            missing.add("source-only combo root " + snapshot.combo());
        }
        if (snapshot.slashArt() != null && isSourceOnly(snapshot.slashArt())) missing.add(snapshot.slashArt().toString());
        snapshot.specialEffects().stream().filter(StandaloneBladeExport::isSourceOnly)
                .forEach(effect -> missing.add(effect.toString()));
        snapshot.enchantments().stream().map(BladeExportSnapshot.EnchantmentValue::id)
                .filter(StandaloneBladeExport::isSourceOnly).forEach(enchantment -> missing.add(enchantment.toString()));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unsupported export abilities: " + String.join(", ", missing));
        }
        if (!Vec3.ZERO.equals(snapshot.adjust())) {
            throw new IllegalArgumentException("This blade uses a custom model position");
        }
        if (snapshot.slashArt() == null) throw new IllegalArgumentException("Missing Slash Art identifier");
        ExportBattleProfile.from(snapshot).capabilities(combo(snapshot));
    }

    private static boolean isSourceOnly(ResourceLocation id) {
        return Set.of("blade_tetra", "tetra").contains(id.getNamespace());
    }

    static Path write(Path gameDirectory, ResourceManager resources, BladeExportSnapshot snapshot,
            BladeExportArchive.Metadata metadata, BladeExportRecipe recipe) throws IOException {
        var model = DefaultResources.resourceDefaultModel;
        var texture = DefaultResources.resourceDefaultTexture;
        var appearance = List.of(ExportResourceEvidence.reference(resources, model, "slashblade"),
                ExportResourceEvidence.reference(resources, texture, "slashblade"));
        return write(gameDirectory, resources, snapshot, metadata, recipe,
                new BladeExportArt(model, texture, appearance, Map.of()));
    }

    static Path write(Path gameDirectory, ResourceManager resources, BladeExportSnapshot snapshot,
            BladeExportArchive.Metadata metadata, BladeExportRecipe recipe, BladeExportArt artwork) throws IOException {
        checkCombat(snapshot);

        List<BladeExportArchive.Fitting> fittings = new ArrayList<>();
        for (var source : snapshot.namedParts()) {
            if (source.namedBlade() == null || source.declaredModel() == null || source.declaredTexture() == null) {
                throw new IOException("Missing named blade source for " + source.slot() + ": " + source.sourceId());
            }
            // A semantic declared model may redirect to another OBJ through an
            // audited model adapter. Do not demand nonexistent semantic OBJ bytes.
            var selected = artwork.namedInputs().get(source.slot());
            if (selected == null) {
                selected = List.of(source.declaredModel(), source.declaredTexture());
            }
            var fittingEvidence = new ArrayList<ExportResourceEvidence>();
            for (var id : selected) {
                var verified = artwork.inputs().stream().filter(input -> input.id().equals(id)).findFirst();
                fittingEvidence.add(verified.orElseGet(() -> {
                    try {
                        return reference(resources, source, id);
                    } catch (IOException exception) {
                        throw new IllegalArgumentException("Missing source proof for " + id, exception);
                    }
                }));
            }
            fittings.add(new BladeExportArchive.Fitting(source, fittingEvidence));
        }

        Set<String> required = new TreeSet<>(recipe.namespaces());
        required.add("slashblade");
        required.add(snapshot.slashArt().getNamespace());
        snapshot.specialEffects().forEach(id -> required.add(id.getNamespace()));
        snapshot.enchantments().forEach(value -> required.add(value.id().getNamespace()));
        snapshot.attributes().forEach(value -> required.add(value.id().getNamespace()));
        required.removeAll(Set.of("minecraft", "forge"));
        var dependencies = required.stream().map(id -> new BladeExportArchive.Dependency(id, version(id))).toList();
        String slashbladeVersion = version("slashblade");
        if (!Set.of("1.9.63", "1.9.65").contains(slashbladeVersion)) {
            throw new IllegalArgumentException(
                    "Standalone export has not been validated against SlashBlade " + slashbladeVersion
                            + "; supported targets: 1.9.63, 1.9.65");
        }

        var definition = new NativeBladeExport(snapshot.attack(), snapshot.durability(), combo(snapshot),
                snapshot.slashArt(), snapshot.specialEffects(), snapshot.enchantments(), artwork.model(), artwork.texture(),
                snapshot.color(), snapshot.colorInverse(), CarryType.valueOf(snapshot.carryType()),
                snapshot.bewitched(), snapshot.sealed(), snapshot.unbreakable());
        return BladeExportArchive.write(gameDirectory, metadata, version("blade_tetra"), definition,
                recipe, dependencies, fittings, artwork.inputs(), artwork.assets(),
                ExportBattleProfile.from(snapshot));
    }

    private static ResourceLocation combo(BladeExportSnapshot snapshot) {
        return switch (snapshot.style()) {
            case "STANDARD" -> snapshot.combo();
            case "IAIDO" -> ExportBattleProfile.IAIDO_ROOT;
            case "RENGEKI" -> ExportBattleProfile.RENGEKI_ROOT;
            case "DANGAKU" -> ExportBattleProfile.DANGAKU_ROOT;
            case "CUSTOM" -> ExportBattleProfile.CUSTOM_ROOT;
            default -> throw new IllegalArgumentException("This sword style is still being ported: " + snapshot.style());
        };
    }

    private static ExportResourceEvidence reference(ResourceManager resources, NamedPartProvenance fitting,
            ResourceLocation resource) throws IOException {
        String owner = fitting.namedBlade().getNamespace();
        try {
            return ExportResourceEvidence.reference(resources, resource, owner);
        } catch (IOException unavailable) {
            if (owner.equals(resource.getNamespace())) throw unavailable;
            return ExportResourceEvidence.reference(resources, resource, resource.getNamespace());
        }
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .orElseThrow(() -> new IllegalArgumentException("Missing dependency: " + modId))
                .getModInfo().getVersion().toString();
    }

    private StandaloneBladeExport() {}
}
