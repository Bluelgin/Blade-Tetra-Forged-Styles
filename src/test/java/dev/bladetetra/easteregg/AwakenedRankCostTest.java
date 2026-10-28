package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwakenedRankCostTest {
    @Test void fullTierGateUsesNative300PointRankThresholds() {
        assertFalse(AwakenedRankCost.canPay(1499, 300, 4, 5, 360));
        assertTrue(AwakenedRankCost.canPay(1500, 300, 5, 5, 360));
        assertTrue(AwakenedRankCost.canPay(1799, 300, 7, 5, 480));
        assertFalse(AwakenedRankCost.canPay(1199, 300, 3, 4, 300));
        assertTrue(AwakenedRankCost.canPay(1200, 300, 4, 4, 300));
    }

    @Test void spendingDemotesRankRatherThanInventingSeparateCharges() {
        // Kyouka's A-rank SA uses native Rank points rather than a new meter.
        long before = 1260;
        assertTrue(AwakenedRankCost.canPay(before, 300, 4, 4, 300));
        assertEquals(960, before - 300);
        assertFalse(AwakenedRankCost.canPay(before - 300, 300, 3, 4, 300));
    }

    @Test void insufficientRankAndPriceAlwaysRejectWithoutPartialPayment() {
        assertFalse(AwakenedRankCost.canPay(1400, 300, 4, 5, 100));
        assertFalse(AwakenedRankCost.canPay(900, 300, 3, 3, 901));
        assertFalse(AwakenedRankCost.canPay(0, 300, 0, 3, 75));
        assertFalse(AwakenedRankCost.canPay(1000, 0, 5, 3, 75));
        assertFalse(AwakenedRankCost.canPay(-1, 300, 5, 3, 75));
        assertFalse(AwakenedRankCost.canPay(1500, 300, 5, 5, -1));
    }

    @Test void basicBStepRemainsChainableAfterRankFallsBelowB() {
        // Only the initial step in a three-step sequence is a paid cast.
        assertTrue(AwakenedRankCost.canPay(900, 300, 3, 3, 75));
        assertFalse(AwakenedRankCost.canPay(825, 300, 2, 3, 75));
        assertTrue(AwakenedCombatMath.isThunderFollowup(1, true, 10, 15));
        assertTrue(AwakenedCombatMath.isThunderFollowup(2, true, 15, 20));
        assertFalse(AwakenedCombatMath.isThunderFollowup(3, true, 20, 25));
        assertFalse(AwakenedCombatMath.isThunderFollowup(2, false, 15, 20));
    }

    @Test void slashbladeGradeBoundsAreUsedRatherThanIndependentMeter() {
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.KYOUKA));
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.SAKURA_ARRAY));
        assertEquals(3, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.RAIKIRI_STEP));
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.RAIKIRI_FINISH));
    }
}
