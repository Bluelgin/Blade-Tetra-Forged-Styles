package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AkatsukiExecutionMathTest {
    @Test
    void trimsSingleHighAndLowOutlier() {
        assertEquals(100.0D, AkatsukiExecutionMath.representativeDamage(
                List.of(1.0F, 100.0F, 100.0F, 100.0F, 900.0F)), 0.001D);
    }

    @Test
    void thresholdScalesBetweenSixAndTwelvePercent() {
        assertEquals(300.0F, AkatsukiExecutionMath.threshold(
                5000.0F, 20.0D, 4.0D, 0.06D, 0.12D), 0.001F);
        assertEquals(400.0F, AkatsukiExecutionMath.threshold(
                5000.0F, 100.0D, 4.0D, 0.06D, 0.12D), 0.001F);
        assertEquals(600.0F, AkatsukiExecutionMath.threshold(
                5000.0F, 300.0D, 4.0D, 0.06D, 0.12D), 0.001F);
    }

    @Test
    void pulseKeepsAtLeastTwelveCutsAtExecutionStart() {
        assertEquals(12.0F, AkatsukiExecutionMath.pulseDamage(
                100.0D, 400.0F, 0.12D, 12), 0.001F);
        assertEquals(2.0F, AkatsukiExecutionMath.pulseDamage(
                100.0D, 24.0F, 0.12D, 12), 0.001F);
    }
}
