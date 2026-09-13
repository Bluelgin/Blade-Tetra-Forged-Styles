package dev.bladetetra.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrails around known legacy hotspots.
 *
 * <p>These are not style rules for ordinary classes. They exist specifically
 * to stop already-large compatibility/rendering classes from silently becoming
 * the extension point for every future addon. If one of these budgets must be
 * raised, the preferred fix is to extract a focused collaborator instead.</p>
 */
class ArchitectureDebtGuardTest {
    @Test
    void legacyHotspotsDoNotKeepGrowing() throws IOException {
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/client/BladeTechniqueVfxClient.java",
                146_500L);
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java",
                157_500L);
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java",
                173_000L);
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeManager.java",
                77_000L);
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java",
                40_000L);
        assertSizeAtMost(
                "src/main/java/dev/bladetetra/combat/VoidScatteringFusionHandler.java",
                44_000L);
    }

    @Test
    void legacyIntegerTechniquePacketIsFrozenForNewVisualFamilies() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/network/BladeTechniqueVfxPacket.java"));
        long constants = source.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("public static final int "))
                .count();
        assertTrue(constants <= 43,
                "New technique VFX should use ModularTechniqueVfxPacket + TechniqueVfxRegistry "
                        + "instead of extending the legacy integer packet");
    }

    @Test
    void materialTextureCompositorDoesNotGainDirectAddonPresenceChecks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        assertFalse(source.contains("ModList.get()"),
                "Addon material compatibility belongs in the visual resolver/override registry, "
                        + "not MaterialTextureManager");
        assertFalse(source.contains("isLoaded("),
                "MaterialTextureManager must stay provider-agnostic");
    }

    private static void assertSizeAtMost(String path, long maximum) throws IOException {
        long size = Files.size(Path.of(path));
        assertTrue(size <= maximum,
                () -> path + " grew to " + size + " bytes (budget " + maximum
                        + "). Extract a focused collaborator instead of raising the budget.");
    }
}
