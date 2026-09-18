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
    void clientGeometryCannotLeakIntoIntegratedServerSemantics() throws IOException {
        String clientResolver = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/LegacyClientModelAnalysis.java"));
        assertTrue(clientResolver.contains("!minecraft.isSameThread()"),
                "Integrated-server threads must fall back before touching client resources");
        assertTrue(clientResolver.contains("non-client thread metadata"),
                "The cross-thread fallback should stay explicitly metadata-only");

        String calibration = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/LegacyCalibration.java"));
        assertFalse(calibration.contains("kind.defaultProfile()"),
                "Persistent/gameplay calibration fallbacks must never invoke client geometry");
        assertTrue(calibration.contains("kind.rawDefaultProfile()"));
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
