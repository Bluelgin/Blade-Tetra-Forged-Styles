package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

class AwakenedCombatMathTest {
    @Test void allSixAnglesAreReachableAndWrapCorrectly() {
        int mask = 0;
        for (int i = 0; i < 6; i++) {
            double a = (i + 0.5D) * Math.PI / 3.0D - Math.PI;
            mask |= 1 << AwakenedCombatMath.scarSector(Math.cos(a), Math.sin(a));
        }
        assertEquals(0b111111, mask);
        assertEquals(AwakenedCombatMath.scarSector(1, 0),
                AwakenedCombatMath.scarSector(100, 0));
    }

    @Test void minimumGatesAndHardDamageCaps() {
        assertEquals(0, AwakenedCombatMath.causalCut(10, 2));
        assertTrue(AwakenedCombatMath.causalCut(10, 3) > 0);
        assertTrue(AwakenedCombatMath.causalCut(Float.MAX_VALUE, 6) <= 30);
        assertEquals(0, AwakenedCombatMath.petalBudget(5, 2));
        assertTrue(AwakenedCombatMath.petalBudget(Float.MAX_VALUE, 6) <= 26);
        assertEquals(0, AwakenedCombatMath.thunderFinisher(5, 1));
        assertTrue(AwakenedCombatMath.thunderFinisher(Float.MAX_VALUE, 4) <= 20);
        assertTrue(AwakenedCombatMath.mirrorCut(Float.MAX_VALUE) <= 12);
    }

    @Test void causalCrossRequiresThreeSectorsWithAngularSeparation() {
        assertFalse(AwakenedCombatMath.hasCausalCross(0));
        assertFalse(AwakenedCombatMath.hasCausalCross(0b000011));
        assertFalse(AwakenedCombatMath.hasCausalCross(0b100001));
        assertTrue(AwakenedCombatMath.hasCausalCross(0b000111));
        assertTrue(AwakenedCombatMath.hasCausalCross(0b101010));
        assertTrue(AwakenedCombatMath.hasCausalCross(0b111111));
    }

    @Test void thunderChainRequiresConfirmedHitAndWindow() {
        assertEquals(1, AwakenedCombatMath.nextThunderChain(0, false, 1, 2));
        assertEquals(1, AwakenedCombatMath.nextThunderChain(1, false, 10, 14));
        assertEquals(2, AwakenedCombatMath.nextThunderChain(1, true, 10, 18));
        assertEquals(3, AwakenedCombatMath.nextThunderChain(2, true, 18, 25));
        assertEquals(1, AwakenedCombatMath.nextThunderChain(3, true, 25, 33));
        assertTrue(AwakenedCombatMath.isThunderFollowup(1, true, 10, 18));
        assertTrue(AwakenedCombatMath.isThunderFollowup(2, true, 18, 25));
        assertFalse(AwakenedCombatMath.isThunderFollowup(3, true, 25, 33));
        assertFalse(AwakenedCombatMath.isThunderFollowup(2, false, 18, 25));
        assertFalse(AwakenedCombatMath.isThunderFollowup(2, true, 18, 55));
        assertEquals(1, AwakenedCombatMath.nextThunderChain(3, true, 10, 47));
        assertEquals(1, AwakenedCombatMath.nextThunderChain(3, true, 20, 19));
    }

    @Test void lightningStepTimerIsIndependentOfPreviousHits() {
        // A player can begin a fresh step after a long idle period. The next
        // ten-tick HUD heartbeat must not erase that newly opened sequence.
        long lastDamage = 10L;
        long firstStep = 60000L;
        assertTrue(firstStep - lastDamage > 160L);
        assertFalse(AwakenedCombatMath.thunderChainExpired(firstStep, firstStep + 10L));
        assertTrue(AwakenedCombatMath.isThunderFollowup(1, true,
                firstStep, firstStep + 12L));
        assertFalse(AwakenedCombatMath.thunderChainExpired(firstStep, firstStep + 36L));
        assertTrue(AwakenedCombatMath.thunderChainExpired(firstStep, firstStep + 37L));
        assertTrue(AwakenedCombatMath.thunderChainExpired(firstStep, firstStep - 1L));
    }

    @Test void sakuraArraysDistinguishGuardFanAndRain() {
        assertEquals(0, AwakenedCombatMath.sakuraFormation(false, false));
        assertEquals(1, AwakenedCombatMath.sakuraFormation(false, true));
        assertEquals(2, AwakenedCombatMath.sakuraFormation(true, false));
        assertEquals(2, AwakenedCombatMath.sakuraFormation(true, true));
    }

    @Test void thunderFinisherRequiresRealPhysicalMeleeArrival() {
        AABB target = new AABB(6.5D, 64.0D, -0.4D, 7.5D, 66.0D, 0.4D);
        // A valid 8-block lock-on does not authorize remote damage by itself.
        assertFalse(AwakenedCombatMath.thunderFinisherCanReach(
                new Vec3(0.0D, 64.0D, 0.0D), target));
        // Collision-free dash ending beside the target is a real hit.
        assertTrue(AwakenedCombatMath.thunderFinisherCanReach(
                new Vec3(4.5D, 64.0D, 0.0D), target));
        // Even directly beneath a high airborne enemy cannot reach it.
        assertFalse(AwakenedCombatMath.thunderFinisherCanReach(
                new Vec3(7.0D, 50.0D, 0.0D), target));
        assertFalse(AwakenedCombatMath.thunderFinisherCanReach(
                new Vec3(Double.NaN, 64.0D, 0.0D), target));
        assertFalse(AwakenedCombatMath.thunderFinisherCanReach(null, target));
    }

    @Test void invalidDamageDoesNotBecomeBonusDamage() {
        for (float bad : new float[]{-1, 0, Float.NaN,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertEquals(0, AwakenedCombatMath.causalCut(bad, 6));
            assertEquals(0, AwakenedCombatMath.mirrorCut(bad));
            assertEquals(0, AwakenedCombatMath.petalBudget(bad, 6));
            assertEquals(0, AwakenedCombatMath.thunderFinisher(bad, 4));
        }
    }
}
