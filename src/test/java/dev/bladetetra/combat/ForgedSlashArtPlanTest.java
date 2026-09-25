package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgedSlashArtPlanTest {
    @Test
    void nativeFlowPreservesFailAndJustWhileNormalizingSuper() {
        assertEquals(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Fail,
                ForgedNativeComboFlow.sourceType(
                        mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Fail));
        assertEquals(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Success,
                ForgedNativeComboFlow.sourceType(
                        mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Success));
        assertEquals(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Jackpot,
                ForgedNativeComboFlow.sourceType(
                        mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Jackpot));
        assertEquals(mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Success,
                ForgedNativeComboFlow.sourceType(
                        mods.flammpfeil.slashblade.slasharts.SlashArts.ArtsType.Super));
    }

    @Test
    void everyTechniqueAndModifierCombinationCompilesToBoundedDamage() {
        for (ForgedSlashArtPlan.Technique primary : ForgedSlashArtPlan.Technique.values()) {
            for (ForgedSlashArtPlan.Technique secondary : ForgedSlashArtPlan.Technique.values()) {
                for (ForgedSlashArtPlan.Modifier modifier : ForgedSlashArtPlan.Modifier.values()) {
                    ForgedSlashArtPlan plan = ForgedSlashArtPlan.compose(
                            "sa_core/diamond", primary, secondary, modifier);
                    assertNotNull(plan);
                    assertTrue(plan.primaryCount() >= 1 && plan.primaryCount() <= 8);
                    assertTrue(plan.secondaryCount() >= 1 && plan.secondaryCount() <= 8);
                    assertTrue(plan.primaryDamagePerHit() > 0.0D);
                    assertTrue(plan.secondaryDamagePerHit() > 0.0D);
                    assertTrue(plan.secondaryDelayTicks() >= 1);
                    assertTrue(plan.effectiveDamageBudget() <= plan.powerBudget() * 1.061D);
                }
            }
        }
    }

    @Test
    void orderedPrimaryAndSecondaryRemainDifferentArts() {
        ForgedSlashArtPlan forward = ForgedSlashArtPlan.compose(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan reverse = ForgedSlashArtPlan.compose(
                "sa_core/iron",
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Technique.PIERCING,
                ForgedSlashArtPlan.Modifier.BALANCED);
        assertNotEquals(forward.key(), reverse.key());
        assertNotEquals(forward.primary(), reverse.primary());
        assertNotEquals(forward.secondary(), reverse.secondary());
    }

    @Test
    void modifiersChangeTopologyWithoutCreatingDamageFromHitCount() {
        ForgedSlashArtPlan balanced = plan(ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan shatter = plan(ForgedSlashArtPlan.Modifier.SHATTER);
        ForgedSlashArtPlan condensed = plan(ForgedSlashArtPlan.Modifier.CONDENSED);
        ForgedSlashArtPlan echo = plan(ForgedSlashArtPlan.Modifier.ECHO);

        assertTrue(shatter.totalHits() > balanced.totalHits());
        assertTrue(shatter.effectiveDamageBudget() < balanced.effectiveDamageBudget());
        assertTrue(condensed.totalHits() < balanced.totalHits());
        assertEquals(2, echo.secondaryCycles());
        assertTrue(echo.secondaryDamagePerHit() < balanced.secondaryDamagePerHit());
    }

    @Test
    void hasteMovesAuthoredDamageEarlierWithoutOwningNativeAnimationSpeed() {
        ForgedSlashArtPlan balanced = plan(ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan haste = plan(ForgedSlashArtPlan.Modifier.HASTE);
        assertTrue(haste.primaryDelayTicks() < balanced.primaryDelayTicks());
        assertTrue(haste.secondaryDelayTicks() < balanced.secondaryDelayTicks());
    }

    @Test
    void techniquesCompileToMultipleAttackPrimitiveFamilies() {
        long primitiveFamilies = java.util.Arrays.stream(
                        ForgedSlashArtPlan.Technique.values())
                .map(ForgedSlashArtPlan.Technique::primitive)
                .distinct()
                .count();
        assertTrue(primitiveFamilies >= 5);
        assertEquals(ForgedSlashArtPlan.Primitive.TARGETED_SWORDS,
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT.primitive());
        assertEquals(ForgedSlashArtPlan.Primitive.NATIVE_SAKURA,
                ForgedSlashArtPlan.Technique.SAKURA_END.primitive());
        assertEquals(ForgedSlashArtPlan.Primitive.NATIVE_VOID,
                ForgedSlashArtPlan.Technique.VOID_SLASH.primitive());
        assertEquals(ForgedSlashArtPlan.Primitive.NATIVE_CIRCLE,
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH.primitive());
        assertEquals(ForgedSlashArtPlan.Primitive.NATIVE_PIERCING,
                ForgedSlashArtPlan.Technique.PIERCING.primitive());
    }

    @Test
    void judgementKeepsBoundedPhantomSwordBudget() {
        ForgedSlashArtPlan balanced = ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.SAKURA_END,
                ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan shatter = ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.SAKURA_END,
                ForgedSlashArtPlan.Modifier.SHATTER);
        assertEquals(5, balanced.primaryCount());
        assertEquals(8, shatter.primaryCount());
        assertTrue(balanced.effectiveDamageBudget() <= balanced.powerBudget() * 1.001D);
    }

    @Test
    void judgementCenterHitIsTakenFromItsSwordBudget() {
        ForgedSlashArtPlan plan = ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.SAKURA_END,
                ForgedSlashArtPlan.Modifier.BALANCED);
        double center = ProceduralSlashArtExecutor.judgementCenterDamage(
                plan.primaryDamagePerHit(), plan.primaryCount());
        double swords = ProceduralSlashArtExecutor.judgementSwordDamage(
                plan.primaryDamagePerHit()) * plan.primaryCount();
        assertTrue(center > 0.0D);
        assertEquals(plan.primaryDamagePerHit() * plan.primaryCount(),
                center + swords, 1.0E-9D);
    }

    @Test
    void mineralCoreProgressionHasUsefulVanillaAnchors() {
        assertTrue(ForgedSlashArtPlan.corePowerFor("sa_core/stone")
                < ForgedSlashArtPlan.corePowerFor("sa_core/iron"));
        assertTrue(ForgedSlashArtPlan.corePowerFor("sa_core/iron")
                < ForgedSlashArtPlan.corePowerFor("sa_core/diamond"));
        assertTrue(ForgedSlashArtPlan.corePowerFor("sa_core/diamond")
                < ForgedSlashArtPlan.corePowerFor("sa_core/netherite"));
    }

    @Test
    void castSnapshotsWieldedAttackWithoutChangingHitTopology() {
        ForgedSlashArtPlan authored = ForgedSlashArtPlan.compose(
                "sa_core/iron", ForgedSlashArtPlan.Technique.JUDGEMENT_CUT,
                ForgedSlashArtPlan.Technique.VOID_SLASH,
                ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan ironBlade = authored.snapshotForAttack(7.0D);
        ForgedSlashArtPlan strongerBlade = authored.snapshotForAttack(32.0D);

        assertEquals(7.0D * 1.10D * 0.97D,
                ironBlade.effectiveDamageBudget(), 1.0E-9D);
        assertEquals(32.0D * 1.10D * 0.97D,
                strongerBlade.effectiveDamageBudget(), 1.0E-9D);
        assertEquals(authored.key(), strongerBlade.key());
        assertEquals(authored.totalHits(), strongerBlade.totalHits());
        assertEquals(32.0D / 7.0D,
                strongerBlade.primaryDamagePerHit()
                        / ironBlade.primaryDamagePerHit(), 1.0E-9D);
    }

    @Test
    void unknownMaterialUsesNeutralCoreModifier() {
        assertEquals(1.0D, ForgedSlashArtPlan.corePowerFor(
                "sa_core/mmt_unknown_alloy"));
    }

    @Test
    void techniqueNamesUseSlashBladesOwnTranslationKeys() {
        for (ForgedSlashArtPlan.Technique technique
                : ForgedSlashArtPlan.Technique.values()) {
            assertEquals("slash_art.slashblade." + technique.id(),
                    technique.translationKey());
        }
    }

    @Test
    void generalizedGeometryPreservesLegacyBaseShapes() {
        assertEquals(-10.0D, ProceduralSlashArtExecutor.responseYaw(
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0, 2, 1.0D), 0.0001D);
        assertEquals(10.0D, ProceduralSlashArtExecutor.responseYaw(
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 1, 2, 1.0D), 0.0001D);
        assertEquals(90.0D, ProceduralSlashArtExecutor.responseYaw(
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 1, 4, 1.0D), 0.0001D);
        assertEquals(-18.0D, ProceduralSlashArtExecutor.responseYaw(
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0, 2, 1.8D), 0.0001D);
    }

    private static ForgedSlashArtPlan plan(ForgedSlashArtPlan.Modifier modifier) {
        return ForgedSlashArtPlan.compose(
                "sa_core/diamond",
                ForgedSlashArtPlan.Technique.CIRCLE_SLASH,
                ForgedSlashArtPlan.Technique.WAVE_EDGE,
                modifier);
    }
}
