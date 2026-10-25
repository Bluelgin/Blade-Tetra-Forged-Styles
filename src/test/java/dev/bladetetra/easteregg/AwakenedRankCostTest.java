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
        // A native S player spending Akatsuki 360 becomes 1140 / rank B,
        // and must build Rank up again using normal SlashBlade attacks.
        long before = 1530;
        assertTrue(AwakenedRankCost.canPay(before, 300, 5, 5, 360));
        assertEquals(1170, before - 360);
        assertFalse(AwakenedRankCost.canPay(before - 360, 300, 3, 5, 360));
    }

    @Test void insufficientRankAndPriceAlwaysRejectWithoutPartialPayment() {
        assertFalse(AwakenedRankCost.canPay(1400, 300, 4, 5, 100));
        assertFalse(AwakenedRankCost.canPay(900, 300, 3, 3, 901));
        assertFalse(AwakenedRankCost.canPay(0, 300, 0, 3, 75));
        assertFalse(AwakenedRankCost.canPay(1000, 0, 5, 3, 75));
        assertFalse(AwakenedRankCost.canPay(-1, 300, 5, 3, 75));
        assertFalse(AwakenedRankCost.canPay(1500, 300, 5, 5, -1));
    }

    @Test void slashbladeGradeBoundsAreUsedRatherThanIndependentMeter() {
        assertEquals(5, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.AKATSUKI));
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.KYOUKA));
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.SAKURA_ARRAY));
        assertEquals(3, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.RAIKIRI_STEP));
        assertEquals(4, AwakenedRankCost.requiredLevel(AwakenedRankCost.Cast.RAIKIRI_FINISH));
    }
}
