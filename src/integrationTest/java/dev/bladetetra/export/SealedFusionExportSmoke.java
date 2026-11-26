package dev.bladetetra.export;

import com.mojang.logging.LogUtils;
import dev.bladetetra.combat.ProgrammaticFusionAttunement;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyImprintStorage;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

/** Seals confirmed Tetra fittings with real native arts and their independently baked art. */
final class SealedFusionExportSmoke {
    static void verify(Path directory, ResourceManager resources) throws Exception {
        var horizontal = SlashArtsRegistry.DRIVE_HORIZONTAL.getId();
        var vertical = SlashArtsRegistry.DRIVE_VERTICAL.getId();
        var judgement = SlashArtsRegistry.JUDGEMENT_CUT.getId();
        write(directory, resources, "bt_export_fusion", horizontal, vertical);
        write(directory, resources, "bt_export_fusion_super", judgement, vertical);

        var blocked = blade(horizontal, ResourceLocation.parse("recasting:time_beyond"));
        var snapshot = BladeExportSnapshot.captureOwnedStack(blocked);
        var report = BladeExportPreflight.inspect(snapshot, blocked);
        require(!report.ready() && report.summary().contains("recasting:time_beyond"),
                "Preflight skipped the unsupported charge-dependent source: " + report.summary());
        LogUtils.getLogger().info("BLADE_STUDIO_FUSION_EXPORT_PASS");
    }

    private static void write(Path directory, ResourceManager resources, String modId,
                              ResourceLocation release, ResourceLocation response) throws Exception {
        var source = blade(release, response);
        var snapshot = BladeExportSnapshot.captureOwnedStack(source);
        var report = BladeExportPreflight.inspect(snapshot, source);
        require(report.ready(), "Native fusion export preflight: " + report.summary());
        require(snapshot.equals(BladeSealPackets.decodeSnapshot(BladeSealPackets.encodeSnapshot(snapshot))),
                "Fusion pair changed over the Studio network protocol");
        require(snapshot.fusion() != null && release.equals(snapshot.fusion().releaseAbility())
                && response.equals(snapshot.fusion().responseAbility()), "Export lost the confirmed order");
        var meta = new BladeExportArchive.Metadata(modId, "1.0.0", "Sealed Fusion", "Blade Tetra",
                "Native fusion integration fixture");
        var art = BladeExportArt.bake(modId, snapshot, source, resources);
        try (var zip = new ZipFile(StandaloneBladeExport.write(directory, resources, snapshot, meta, art).toFile())) {
            var certificate = zip.getEntry("blade-export.json");
            try (var contents = zip.getInputStream(certificate)) {
                String json = new String(contents.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                require(json.contains("bt_export_runtime:ordered_native_fusion_v1"), "Missing fusion capability");
            }
        }
    }

    private static ItemStack blade(ResourceLocation release, ResourceLocation response) {
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        fit(blade, "saya", LegacyImprintKind.WOODEN_PUPPET, release);
        fit(blade, "tsuba", LegacyImprintKind.BLACK_FOX, response);
        require(ProgrammaticFusionAttunement.confirm(blade), "Could not confirm fixture pair");
        ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
        return blade;
    }

    private static void fit(ItemStack stack, String slot, LegacyImprintKind visual, ResourceLocation art) {
        var tag = stack.getOrCreateTag();
        var module = tag.getString("slashblade/" + slot);
        var id = "fusion_fixture_" + slot;
        var source = new LegacyImprintKind(id, visual.name(), visual.model(), visual.texture(), visual.material(),
                visual.defaultProfile(), visual.baseAttack(), visual.maxDamage(), art, List.of());
        tag.putString(module + "_material", NamedLegacyImprintStorage.genericVariant(slot));
        NamedLegacyImprintStorage.putSource(tag, slot, id);
        NamedLegacyImprintStorage.putSnapshot(stack, slot, source);
    }

    private static void require(boolean truth, String message) {
        if (!truth) throw new IllegalStateException(message);
    }

    private SealedFusionExportSmoke() {}
}
