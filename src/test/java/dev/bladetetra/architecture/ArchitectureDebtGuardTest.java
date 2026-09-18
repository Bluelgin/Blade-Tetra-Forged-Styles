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
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/BladeTechniqueVfxClient.java",
                2_900L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java",
                4_100L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java",
                3_900L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeManager.java",
                1_800L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java",
                950L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/VoidScatteringFusionHandler.java",
                1_050L);
    }

    @Test
    void styleCombatLivingTickKeepsItsFastExit() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java"));
        assertTrue(source.contains(
                "if (!modularBlade && !hasIaidoState && !hasBrokenStanceState)"),
                "Unrelated living entities must leave StyleCombatHandler.onLivingTick early");
        assertTrue(source.contains("hasIaidoTickState(CompoundTag data)"),
                "Iaido transient-state detection must stay explicit and allocation-free");
        assertTrue(source.contains("data.contains(IAIDO_DRAW_POWER_UNTIL, Tag.TAG_LONG)"),
                "Missing transient tags must not trigger pointless cleanup writes every tick");
        assertTrue(source.contains("data.contains(IAIDO_SPACING_UNTIL, Tag.TAG_LONG)"),
                "Missing spacing state must not trigger pointless cleanup writes every tick");
        assertTrue(source.contains("data.contains(IAIDO_DEFLECT_UNTIL, Tag.TAG_LONG)"),
                "Missing deflect state must not trigger pointless cleanup writes every tick");
        assertTrue(source.contains("data.contains(IAIDO_DISRUPTED_UNTIL, Tag.TAG_LONG)"),
                "Missing disrupted state must not trigger pointless cleanup writes every tick");
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

    @Test
    void clientVfxRegistryDoesNotDependOnNetworkTransport() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/vfx/TechniqueVfxRegistry.java"));
        assertFalse(source.contains("dev.bladetetra.network"),
                "The renderer registry should consume neutral VFX data, not network packets");
    }

    private static void assertLinesAtMost(String path, long maximum) throws IOException {
        long lines;
        try (var sourceLines = Files.lines(Path.of(path))) {
            lines = sourceLines.count();
        }
        assertTrue(lines <= maximum,
                () -> path + " grew to " + lines + " lines (budget " + maximum
                        + "). Extract a focused collaborator instead of raising the budget.");
    }
}
