package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DangakuChargeMathTest {
    @Test
    void fasterChargeHasOneReadablePeakAndThenDecays() {
        assertEquals(0.0D, DangakuChargeMath.chargeForHeldTicks(0));
        assertEquals(0.5D, DangakuChargeMath.chargeForHeldTicks(16));
        assertEquals(1.0D, DangakuChargeMath.chargeForHeldTicks(32));
        assertTrue(DangakuChargeMath.isPeakWindow(40));
        assertFalse(DangakuChargeMath.isPeakWindow(41));
        double previous = 1.0D;
        for (int tick = 41; tick <= 200; tick++) {
            double charge = DangakuChargeMath.chargeForHeldTicks(tick);
            assertTrue(charge <= previous);
            assertTrue(charge >= 0.25D);
            previous = charge;
        }
    }

    @Test
    void fasterPeakDoesNotIncreaseStandaloneSweepDamagePerChargeTime() {
        for (double panel : new double[] {7, 24, 54, 200}) {
            double oldPeakRatio = 0.58D + 0.10D * DangakuChargeMath.panelScale(panel);
            double newPeakRatio = DangakuChargeMath.sweepDamageRatio(panel, 1.0D);
            assertTrue(newPeakRatio / 32.0D <= oldPeakRatio / 44.0D,
                    "Shorter charge should not offset the intended damage reduction");
            assertTrue(DangakuChargeMath.sweepRange(panel, 1.0D) <= 12.0D);
        }
    }
}
