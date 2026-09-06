package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImprintAffinityTest {
    @Test
    void strongerTetraBladeStillReceivesMinimumAffinity() {
        ImprintAffinity affinity = ImprintAffinity.calculate(32.0D, 1000, 12.0D, 70);
        assertEquals(3.2D, affinity.attackBonus(), 0.0001D);
        assertEquals(0.10D, affinity.attackRatio(), 0.0001D);
        assertEquals(150, affinity.durabilityBonus());
    }

    @Test
    void inheritsHalfOfAReasonableSourceGap() {
        ImprintAffinity affinity = ImprintAffinity.calculate(12.0D, 100, 16.0D, 140);
        assertEquals(2.0D, affinity.attackBonus(), 0.0001D);
        assertEquals(20, affinity.durabilityBonus());
    }

    @Test
    void extremeAddonValuesCannotExceedRelativeCaps() {
        ImprintAffinity affinity = ImprintAffinity.calculate(32.0D, 1000, 80.0D, 10000);
        assertEquals(9.6D, affinity.attackBonus(), 0.0001D);
        assertEquals(0.30D, affinity.attackRatio(), 0.0001D);
        assertEquals(350, affinity.durabilityBonus());
        assertEquals("lifelike", affinity.grade());
    }
}
