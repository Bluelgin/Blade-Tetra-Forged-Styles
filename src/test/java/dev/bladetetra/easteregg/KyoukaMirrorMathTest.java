package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KyoukaMirrorMathTest {
    @Test
    void reflectedCutUsesRecordedPostMitigationDamage() {
        assertEquals(8.0F,
                KyoukaMirrorMath.reflectedDamage(20.0D, 0.40D, 12.0D), 0.001F);
    }

    @Test
    void reflectedCutRespectsItsCap() {
        assertEquals(12.0F,
                KyoukaMirrorMath.reflectedDamage(100.0D, 0.40D, 12.0D), 0.001F);
    }

    @Test
    void waterMoonBreakCombinesRealAndRecordedCuts() {
        assertEquals(14.0F,
                KyoukaMirrorMath.breakDamage(24.0D, 16.0D,
                        0.35D, 0.35D, 18.0D), 0.001F);
    }

    @Test
    void waterMoonBreakRespectsItsTotalCap() {
        assertEquals(18.0F,
                KyoukaMirrorMath.breakDamage(100.0D, 100.0D,
                        0.35D, 0.35D, 18.0D), 0.001F);
    }
}
