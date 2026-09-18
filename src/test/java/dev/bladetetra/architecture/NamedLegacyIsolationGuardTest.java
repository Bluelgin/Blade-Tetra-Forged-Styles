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
}
