package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression-level state predicates that do not require a running Minecraft world. */
class DeadThoughtSoulStateTest {
    @Test
    void onlyZeroOrNegativeIntegrityCountsAsBroken() {
        assertFalse(DeadThoughtErosionMath.isSoulBroken(0.0D));
        assertFalse(DeadThoughtErosionMath.isSoulBroken(0.999D));
        assertTrue(DeadThoughtErosionMath.isSoulBroken(1.0D));
        assertTrue(DeadThoughtErosionMath.isSoulBroken(2.0D));
    }
}
