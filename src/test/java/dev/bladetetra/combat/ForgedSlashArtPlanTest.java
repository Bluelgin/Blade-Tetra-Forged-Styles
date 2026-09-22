package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgedSlashArtPlanTest {
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
                    assertTrue(plan.secondaryDelayTicks() > plan.primaryDelayTicks());
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
    void hasteMovesBothAttackAndHandoffEarlier() {
        ForgedSlashArtPlan balanced = plan(ForgedSlashArtPlan.Modifier.BALANCED);
        ForgedSlashArtPlan haste = plan(ForgedSlashArtPlan.Modifier.HASTE);
        assertTrue(haste.primaryDelayTicks() < balanced.primaryDelayTicks());
        assertTrue(haste.secondaryDelayTicks() < balanced.secondaryDelayTicks());
        assertTrue(haste.modifier().hasteAnimation());
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
