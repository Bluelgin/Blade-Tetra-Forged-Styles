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
                850L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/BladeTechniqueMikageVfxRenderer.java",
                1_450L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java",
                1_400L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureStyleEngine.java",
                1_250L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialStyleCatalog.java",
                700L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/client/MaterialTextureComponentPainter.java",
                850L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java",
                450L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageDamageService.java",
                340L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEncounterSetup.java",
                180L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageEncounterPresentation.java",
                210L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageDuelDefense.java",
                150L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageGuardCounterExecution.java",
                110L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageThousandGatesExecution.java", 180L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateArrival.java", 90L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateCorridorExecution.java", 180L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageCorridorNativeCombo.java", 140L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageCorridorRoute.java", 90L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageCorridorPresentation.java", 90L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/vfx/MikageCorridorVfxClient.java", 160L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/MikageComboBAnimation.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/MikageComboBTracks.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageNativeCombat.java", 180L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageNativeAttacks.java", 130L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageSwordplayExecution.java", 140L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageMoonEchoExecution.java", 130L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageBoundaryExecution.java", 130L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageSkillSelection.java", 90L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageSpacingGoal.java", 50L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageArenaMovement.java", 120L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageCombatFootwork.java", 140L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageFootworkRoutes.java", 120L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/mikage/CombatSpacingState.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateBarrageExecution.java", 180L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateBarragePlacement.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateBarrageShots.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateCoreEntity.java", 150L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageGateSwordEntity.java", 150L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/mikage/GateBarrageSequence.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/MikageGateCoreRenderer.java", 160L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/vfx/render/MikageToriiMesh.java", 120L);
        assertLinesAtMost("src/main/java/dev/bladetetra/challenge/MikageThousandGatesPresentation.java", 100L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/vfx/MikageThousandGatesVfxClient.java", 150L);
        assertLinesAtMost("src/main/java/dev/bladetetra/client/vfx/MikageThousandGatesVfxRenderer.java", 120L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/MikageRuntimeCoordinator.java",
                180L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeManager.java",
                700L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/challenge/ChallengeSession.java",
                1_100L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java",
                220L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/IaidoStyleCombat.java",
                650L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/DangakuStyleCombat.java",
                160L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/VoidScatteringFusionHandler.java",
                800L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/combat/VoidScatteringReturnRuntime.java",
                300L);
        assertLinesAtMost(
                "src/main/java/dev/bladetetra/item/ModularSlashBladeItem.java",
                650L);
    }

    @Test
    void styleCombatFacadeKeepsIaidoStateMachineIsolated() throws IOException {
        String facade = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/StyleCombatHandler.java"));
        String iaido = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/IaidoStyleCombat.java"));

        assertTrue(facade.contains("IaidoStyleCombat.onLivingTick(event.getEntity())"),
                "The global Forge subscriber should only route Iaido ticking");
        assertFalse(facade.contains("blade_tetra_iaido_"),
                "Iaido transient NBT belongs in the style-owned runtime, not the event facade");

        assertTrue(iaido.contains("if (!modularBlade && !hasIaidoState)"),
                "Unrelated living entities must leave IaidoStyleCombat.onLivingTick early");
        assertTrue(iaido.contains("hasTickState(CompoundTag data)"),
                "Iaido transient-state detection must stay explicit and allocation-free");
        assertTrue(iaido.contains("data.contains(DRAW_POWER_UNTIL, Tag.TAG_LONG)"),
                "Missing transient tags must not trigger pointless cleanup writes every tick");
        assertTrue(iaido.contains("data.contains(SPACING_UNTIL, Tag.TAG_LONG)"),
                "Missing spacing state must not trigger pointless cleanup writes every tick");
        assertTrue(iaido.contains("data.contains(DEFLECT_UNTIL, Tag.TAG_LONG)"),
                "Missing deflect state must not trigger pointless cleanup writes every tick");
        assertTrue(iaido.contains("data.contains(DISRUPTED_UNTIL, Tag.TAG_LONG)"),
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
    void materialReloadClearsBothTextureAndLegacyModelCaches() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        assertTrue(source.contains("MaterialTextureCache.clear();"));
        assertTrue(source.contains("LegacyModelPartRenderer.clear();"),
                "Material reload must also invalidate legacy model-part caches");
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
    void forgedSuperSlashArtArmsTheSameNativeRuntime() throws IOException {
        String registry = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/registry/ModSlashBladeAbilities.java"));
        String entrypoint = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ForgedSlashArtEntrypoint.java"));
        assertTrue(registry.contains(
                ".setComboStateSuper(ForgedSlashArtEntrypoint::superCombo)"),
                "SuperSlashArts bypasses PerformSlashArtEvent and must use the direct forged entrypoint");
        assertTrue(entrypoint.contains("ForgedSlashArtHandler.beginCast("));
        assertTrue(entrypoint.contains("SlashArts.ArtsType.Super"));
    }

    @Test
    void forgedRuntimeOnlyOwnsNativeGraphRouting() throws IOException {
        String handler = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ForgedSlashArtHandler.java"));
        String flow = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ForgedNativeComboFlow.java"));
        String facade = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/LegacyFusionHandler.java"));

        assertTrue(handler.contains("ForgedNativeComboFlow.shouldSplice("));
        assertTrue(handler.contains("state.updateComboSeq(player, entry)"));
        assertTrue(handler.contains("blade != pending.sourceBlade"),
                "Switching to another identically-authored blade must cancel the pending route");
        assertTrue(flow.contains("signatureEntry("));
        assertTrue(flow.contains("ComboState.getElapsed(user)"));

        assertFalse(handler.contains("LivingAttackEvent"),
                "Forged runtime must not cancel SlashBlade's native damage");
        assertFalse(handler.contains("EntityJoinLevelEvent"),
                "Forged runtime must not sanitize native presentation entities");
        assertFalse(handler.contains("ProceduralSlashArtExecutor"),
                "Forged runtime must not maintain a second combat executor");
        assertFalse(handler.contains("FORGED_OUTPUT_DEPTH"),
                "Forged output suppression depth belonged to the removed custom-damage path");
        assertFalse(facade.contains("ForgedSlashArtHandler.onLivingAttack"),
                "The event facade must not route native damage through forged suppression");
        assertFalse(facade.contains("ForgedSlashArtHandler.onEntityJoin"),
                "The event facade must not rewrite native SlashBlade entities");
    }

    @Test
    void forgedSecondaryStartsAtSignatureInsteadOfSecondFullSlashArt() throws IOException {
        String flow = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/ForgedNativeComboFlow.java"));
        assertTrue(flow.contains("\"judgement_cut_slash\""));
        assertTrue(flow.contains("\"sakura_end_right\""));
        assertTrue(flow.contains("\"piercing_2\""));
        int secondaryStart = flow.indexOf("static ResourceLocation secondaryEntry");
        int signatureStart = flow.indexOf("static ResourceLocation signatureEntry", secondaryStart);
        assertTrue(secondaryStart >= 0 && signatureStart > secondaryStart);
        String secondaryBody = flow.substring(secondaryStart, signatureStart);
        assertTrue(secondaryBody.contains("signatureEntry("));
        assertFalse(secondaryBody.contains("doArts("),
                "Secondary routing should enter the signature node directly instead of releasing a second full SA");
    }

    @Test
    void clientVfxRegistryDoesNotDependOnNetworkTransport() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/vfx/TechniqueVfxRegistry.java"));
        assertFalse(source.contains("dev.bladetetra.network"),
                "The renderer registry should consume neutral VFX data, not network packets");
    }

    @Test
    void materialStyleResolutionStaysOutsidePixelEngine() throws IOException {
        String engine = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureStyleEngine.java"));
        String catalog = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/MaterialStyleCatalog.java"));

        assertTrue(engine.contains("MaterialStyleCatalog.styleFor(material)"));
        assertTrue(catalog.contains("static MaterialStyle externalMaterialStyle"));
        assertFalse(catalog.contains("NativeImage"),
                "Material classification must not acquire renderer/resource lifecycle ownership");
    }

    @Test
    void mikageSignatureDomainsStayDelegated() throws IOException {
        String entity = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java"));
        String selection = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/MikageSkillSelection.java"));
        String coordinator = Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/MikageRuntimeCoordinator.java"));
        assertTrue(selection.contains("new MikageSwordplayExecution("));
        assertTrue(selection.contains("new MikageMoonEchoExecution("));
        assertTrue(selection.contains("new MikageBoundaryExecution("));
        assertFalse(selection.contains("MikageLegacySkill"));
        assertFalse(coordinator.contains("legacyEffects"));
        assertFalse(coordinator.contains("flashStepAway"));
        assertFalse(entity.contains("MikageLegacySkill"));
        assertTrue(entity.contains("encounter.tick(server)"));
        assertFalse(entity.contains("preparedTicks"),
                "Windup state belongs to a release instance");
        assertFalse(entity.contains("void useTechnique("),
                "Reactive selection belongs outside the entity");
    }

    @Test
    void mikageEntityKeepsLifecycleAndSyncedDataOnly() throws IOException {
        String entity = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/challenge/MikageEntity.java"));
        assertFalse(entity.contains("GameplayConfig"),
                "Combat tuning and calibration belong in focused subsystems");
        assertFalse(entity.contains("playerDefenseProfiles"));
        assertFalse(entity.contains("registerJudgementCutCast"));
        assertFalse(entity.contains("deploySwordWheel"));
        assertFalse(entity.contains("ModNetwork"),
                "Packet distribution belongs in encounter presentation");
        assertTrue(entity.contains("super::hurt"),
                "Damage filtering must retain the vanilla damage hook without recursion");
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
