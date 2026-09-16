package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadThoughtErosionMathTest {
    @Test
    void erosionIsIntentionallyUnboundedAndSoulIntegrityMayGoNegative() {
        assertEquals(1.03D, DeadThoughtErosionMath.add(0.98D, 0.05D), 1.0E-9D);
        assertEquals(5.25D, DeadThoughtErosionMath.add(5.0D, 0.25D), 1.0E-9D);
        assertEquals(-0.03D, DeadThoughtErosionMath.soulIntegrity(1.03D), 1.0E-9D);
        assertEquals(-4.25D, DeadThoughtErosionMath.soulIntegrity(5.25D), 1.0E-9D);
        assertEquals(0.0D, DeadThoughtErosionMath.remainingFraction(1.03D), 1.0E-9D);
    }

    @Test
    void soulBreakOccursExactlyAtZeroIntegrity() {
        assertFalse(DeadThoughtErosionMath.isSoulBroken(0.999999D));
        assertTrue(DeadThoughtErosionMath.isSoulBroken(1.0D));
        assertTrue(DeadThoughtErosionMath.isSoulBroken(3.5D));
    }

    @Test
    void effectiveCapTracksChangingRealMaximumHealth() {
        assertEquals(8000.0F,
                DeadThoughtErosionMath.effectiveCap(10000.0F, 0.20D, 0.01F),
                0.001F);
        assertEquals(12800.0F,
                DeadThoughtErosionMath.effectiveCap(16000.0F, 0.20D, 0.01F),
                0.001F);
    }

    @Test
    void engineFloorIsOnlyAHealthSafetyFloorNotAnErosionCap() {
        assertEquals(0.01F,
                DeadThoughtErosionMath.effectiveCap(10000.0F, 4.0D, 0.01F),
                0.0001F);
        assertEquals(4.25D, DeadThoughtErosionMath.add(4.0D, 0.25D), 1.0E-9D);
    }
}
