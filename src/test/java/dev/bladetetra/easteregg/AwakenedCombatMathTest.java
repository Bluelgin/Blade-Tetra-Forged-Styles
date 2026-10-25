package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
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

    @Test void sakuraArraysDistinguishGuardFanAndRain() {
        assertEquals(0, AwakenedCombatMath.sakuraFormation(false, false));
        assertEquals(1, AwakenedCombatMath.sakuraFormation(false, true));
        assertEquals(2, AwakenedCombatMath.sakuraFormation(true, false));
        assertEquals(2, AwakenedCombatMath.sakuraFormation(true, true));
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
