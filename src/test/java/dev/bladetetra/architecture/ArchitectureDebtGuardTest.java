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
                900L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java",
                1_500L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java",
                3_900L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeManager.java",
                750L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeSession.java",
                1_150L);
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
                "if (!modularBlade && !hasIaidoState)"),
                "Unrelated living entities must leave StyleCombatHandler.onLivingTick early");
        assertFalse(source.contains("hasBrokenStanceState"),
                "Removed Dangaku broken-stance state must not keep unrelated entities in the living-tick path");
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
    void challengeRealmMaintenanceDoesNotRegressToRecurringFullScans() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/challenge/ChallengeManager.java"));
        assertTrue(source.contains("cleanupOrphanedRealmEntities(mirror);"),
                "Old challenge-session entities should be cleaned once when the realms become available");
        assertTrue(source.contains("if (entity instanceof MikageEntity)"),
                "Startup cleanup must discard Mikage entities from process-local sessions that no longer exist");
        assertTrue(source.contains("blade_tetra_mikage_attack"),
                "Startup cleanup must retain the authored Mikage-attack marker fallback");
        long fullRealmScans = source.lines()
                .filter(line -> line.contains("mirror.getAllEntities()"))
                .count();
        assertTrue(fullRealmScans <= 1,
                "ChallengeManager must not scan every entity in the mirror realm on a recurring tick");
        assertFalse(source.contains("List<MikageEntity> orphaned = new ArrayList<>()"),
                "Recurring orphan lists indicate the old once-per-second full-dimension scan returned");

        String session = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/challenge/ChallengeSession.java"));
        long challengeClosures = session.lines()
                .filter(line -> line.contains("closed = true;"))
                .count();
        long arenaAttackCleanups = session.lines()
                .filter(line -> line.contains("cleanupChallengeAttacks(mirror, this);"))
                .count();
        assertTrue(arenaAttackCleanups >= challengeClosures,
                "Every challenge closure path must clean arena-scoped summoned attacks now that the recurring realm scan is gone");
    }

    @Test
    void materialTextureTemplateIsDecodedOncePerResourceCycle() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        String cache = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureCache.java"));
        assertTrue(source.contains("MaterialTextureCache.copyAtlas("),
                "Atlas generation should consume the dedicated cache service");
        assertTrue(cache.contains("atlasTemplate"),
                "The normalized material atlas should be cached for one resource cycle");
        assertTrue(cache.contains("copy.copyFrom(atlasTemplate);"),
                "Each generated signature still needs an isolated mutable image");
        assertTrue(cache.contains("atlasTemplate.close();"),
                "The native template image must be released on resource reload");
        long directTemplateLoads = cache.lines()
                .filter(line -> line.contains("getResourceOrThrow(TEMPLATE)"))
                .count();
        assertTrue(directTemplateLoads <= 1,
                "Template decode/resample belongs in the resource-cycle cache helper only");
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
    void forgedNativeSuppressionDoesNotCaptureGenericPlayerProjectiles()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ForgedSlashArtHandler.java"));
        assertFalse(source.contains("instanceof Projectile"),
                "Forged native suppression must never treat arbitrary player projectiles as SlashBlade output");
        assertTrue(source.contains("direct == player"),
                "Only direct player melee should use the player-owned suppression path");
        assertTrue(source.contains("entity instanceof EntityAbstractSummonedSword"));
        assertTrue(source.contains("entity instanceof EntitySlashEffect"));
        assertTrue(source.contains("entity instanceof EntityJudgementCut"));
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
