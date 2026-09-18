package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the V2 rule that optional addon complexity cannot become a login dependency. */
class NamedLegacyIsolationGuardTest {
    @Test
    void serverDiscoveryNeverParsesProviderObjGeometry() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyCatalog.java"));
        assertFalse(source.contains("LegacyModelAnalysis"),
                "Server named-blade discovery must not parse addon OBJ geometry");
        assertTrue(source.contains("named_blades"),
                "Discovery should stay scoped to standard named-blade data");
        assertFalse(source.contains("Files.walk(root)"),
                "Do not return to walking every provider data file indiscriminately");
    }

    @Test
    void moduleVariantCountIsConstantAcrossAddonCounts() throws IOException {
        String pack = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyPack.java"));
        assertTrue(pack.contains("NamedLegacyImprintStorage.genericVariant(part)"),
                "Every provider must share the generic saya/hilt variants");
        assertFalse(pack.contains("\"legacy_\" + part + \"/\" + kind.id()"),
                "Provider ids must not expand Tetra's module variant table");
        assertTrue(pack.contains("Map<String, byte[]> staged = buildKindResources(kind)"),
                "Per-provider generated resources must be staged atomically");
    }

    @Test
    void sourceIdentityLivesOutsideTetraVariantKeys() throws IOException {
        String storage = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyImprintStorage.java"));
        assertTrue(storage.contains("blade_tetra_named_imprints"));
        assertTrue(storage.contains("/imprinted"));
        assertTrue(storage.contains("sourceFromSchematic"),
                "Existing per-blade schematic ids should feed the generic NBT identity bridge");
    }

    @Test
    void clientGeometryCannotLeakIntoGameplaySemantics() throws IOException {
        String clientResolver = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/LegacyClientModelAnalysis.java"));
        assertTrue(clientResolver.contains("!minecraft.isSameThread()"),
                "Integrated-server threads must fall back before touching client resources");
        assertTrue(clientResolver.contains("non-client thread metadata"),
                "The cross-thread fallback should stay explicitly metadata-only");
        assertTrue(clientResolver.contains("kind.defaultProfile()"));
        assertFalse(clientResolver.contains("kind.rawDefaultProfile()"),
                "Client fallback should use the record's pure metadata accessor directly");

        String kind = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/LegacyImprintKind.java"));
        assertFalse(kind.contains("public LegacyCalibrationProfile defaultProfile()"),
                "The record defaultProfile accessor must stay pure metadata");
        assertTrue(kind.contains("public LegacyCalibrationProfile visualProfile()"),
                "Dynamic geometry needs an explicitly visual API");
        assertFalse(kind.contains("rawDefaultProfile"),
                "Do not reintroduce an ambiguous semantic/profile alias");

        String calibration = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/LegacyCalibration.java"));
        assertTrue(calibration.contains("kind.defaultProfile()"));
        assertFalse(calibration.contains("kind.visualProfile()"));
        assertFalse(calibration.contains("kind.visualUsable()"));

        String parts = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyParts.java"));
        assertTrue(parts.contains("public static NamedLegacyParts visualFromStack"),
                "Rendering must have a separate presentation view");
        assertTrue(parts.contains("return NamedLegacyCatalog.get(id);"),
                "Semantic source resolution must use catalog identity directly");
        int semanticStart = parts.indexOf("public static NamedLegacyParts fromStack");
        int visualStart = parts.indexOf("public static NamedLegacyParts visualFromStack");
        assertTrue(semanticStart >= 0 && visualStart > semanticStart);
        String semanticPath = parts.substring(semanticStart, visualStart);
        assertFalse(semanticPath.contains("visualUsable"));
        assertFalse(semanticPath.contains("visualProfile"));
        assertFalse(semanticPath.contains("LegacyImprintProfileResolver"),
                "Gameplay identity must never consult client geometry");
    }

    @Test
    void legacyPerBladeVariantsHaveAnItemLoadMigrationPath() throws IOException {
        String item = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/item/ModularSlashBladeItem.java"));
        assertTrue(item.contains("LegacyCalibration.migrateStackTag(tag);"),
                "Existing 1.5.x stacks need migration when Minecraft verifies their tag");

        String calibration = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/LegacyCalibration.java"));
        assertTrue(calibration.contains("NamedLegacyImprintStorage.migrateStackTag(stackTag)"),
                "The existing item-load migration entry point must include V2 imprint migration");

        String storage = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedLegacyImprintStorage.java"));
        assertTrue(storage.contains("String prefix = \"legacy_\" + part + \"/\";"),
                "V1.5 per-source variants must remain recognizable by the migration path");
        assertTrue(storage.contains("material.startsWith(prefix)"),
                "Migration must detect old per-source Tetra variants");
        assertTrue(storage.contains("putSource(tag, part, source)"),
                "Migration must preserve the old source id as Blade Tetra soft-reference NBT");
        assertTrue(storage.contains("tag.putString(materialKey, genericVariant(part));"),
                "Old per-source Tetra variants must be rewritten to the generic V2 variant");
    }

    @Test
    void modelAdapterConflictsAreQuarantinedInsteadOfLastWriterWins() throws IOException {
        String adapters = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/LegacyModelAdapter.java"));
        assertTrue(adapters.contains("Set<ResourceLocation> ambiguous"));
        assertTrue(adapters.contains("output.remove(model)"),
                "A conflicting provider must remove the ambiguous adapter mapping");
        assertFalse(adapters.contains("if (location != null) output.put(location, adapter)"),
                "Adapter conflicts must not silently depend on ModList iteration order");
    }
}
