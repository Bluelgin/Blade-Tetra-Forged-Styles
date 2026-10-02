package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static dev.bladetetra.combat.SpeedEfficiencyRules.Timing.*;
import static org.junit.jupiter.api.Assertions.*;

class SpeedEfficiencyRulesTest {
    private double reduction(double speed, SpeedEfficiencyRules.Timing timing) {
        return SpeedEfficiencyRules.reduction(speed, 1.6, timing, timing.maximumReduction);
    }

    @Test void slowInvalidAndDisabledValuesKeepNativeTiming() {
        for (double speed : new double[]{-1, 0, 1.6, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(0, reduction(speed, GROUND_DODGE));
        assertEquals(0, SpeedEfficiencyRules.reduction(4, 0, GROUND_DODGE, .2));
        assertEquals(0, SpeedEfficiencyRules.reduction(4, 1.6, GROUND_DODGE, 0));
        assertEquals(9, SpeedEfficiencyRules.ticks(9, Double.NaN));
        assertEquals(0, SpeedEfficiencyRules.ticks(0, .2));
    }
    @Test void allThreeDirectionsAndSuperSaReachTheirBoundedMinimum() {
        assertEquals(16, SpeedEfficiencyRules.ticks(20, reduction(3.2, GROUND_DODGE)));
        assertEquals(6, SpeedEfficiencyRules.ticks(10, reduction(3.2, SPECIAL_SWORDS)));
        assertEquals(6, SpeedEfficiencyRules.ticks(9, reduction(3.2, NORMAL_SA)));
        assertEquals(14, SpeedEfficiencyRules.ticks(20, reduction(3.2, SUPER_SA)));
        assertEquals(7, SpeedEfficiencyRules.ticks(9, reduction(2.4, NORMAL_SA)));
        assertEquals(1, SpeedEfficiencyRules.ticks(1, .4));
    }
    @Test void higherSpeedNeverLengthensPreparation() {
        double previous = 0;
        int ticks = 20;
        for (double speed=1.6; speed<100; speed+=.1) {
            double reduction = reduction(speed, GROUND_DODGE);
            assertTrue(reduction >= previous - 1e-9 && reduction <= .2);
            int next = SpeedEfficiencyRules.ticks(20, reduction);
            assertTrue(next <= ticks && next >= 16);
            previous = reduction; ticks = next;
        }
    }
    @Test void progressIsLinearAndCappedAtDoubleReference() {
        for (var timing : SpeedEfficiencyRules.Timing.values()) {
            assertEquals(timing.maximumReduction / 2, reduction(2.4, timing), 1e-9);
            assertEquals(timing.maximumReduction / 4, reduction(2.0, timing), 1e-9);
            assertEquals(timing.maximumReduction, reduction(3.2, timing), 1e-9);
            assertEquals(timing.maximumReduction, reduction(128, timing), 1e-9);
        }
    }
    @Test void onlyNativePreparationCallbacksAreRescheduled() {
        for (String name : new String[]{"SpiralSwords", "StormSwords", "BlisteringSwords", "HeavyRainSwords"})
            assertEquals(6, SpeedEfficiencyRules.scheduledDelay(name, 10, reduction(3.2, SPECIAL_SWORDS)));
        assertEquals(14, SpeedEfficiencyRules.scheduledDelay("chargeSuperSA", 20, reduction(3.2, SUPER_SA)));
        assertEquals(4, SpeedEfficiencyRules.scheduledDelay("sendPartical", 5, reduction(3.2, SUPER_SA)));
        assertEquals(10, SpeedEfficiencyRules.scheduledDelay("unrelated", 10, .2));
        assertEquals(12, SpeedEfficiencyRules.scheduledDelay("SpiralSwords", 12, .2));
        assertEquals(-1, SpeedEfficiencyRules.scheduledDelay("SpiralSwords", -1, .2));
    }
    @Test void configurationCanOnlyReduceTheBonusNotRaiseItsHardCap() {
        for (var timing : SpeedEfficiencyRules.Timing.values()) {
            assertEquals(.1, SpeedEfficiencyRules.reduction(100, 1.6, timing, .1), 1e-9);
            assertEquals(timing.maximumReduction, SpeedEfficiencyRules.reduction(100, 1.6, timing, 2), 1e-9);
            assertEquals(0, SpeedEfficiencyRules.reduction(100, 1.6, timing, -1));
        }
    }
}
