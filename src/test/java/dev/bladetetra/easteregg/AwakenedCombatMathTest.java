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
