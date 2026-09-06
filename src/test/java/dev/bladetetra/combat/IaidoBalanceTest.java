package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IaidoBalanceTest {
    @Test
    void spacingSeparatesPressedCloseAndOptimalRanges() {
        assertEquals(IaidoBalance.Spacing.PRESSED, IaidoBalance.spacing(1.24D));
        assertEquals(IaidoBalance.Spacing.CLOSE, IaidoBalance.spacing(1.25D));
        assertEquals(IaidoBalance.Spacing.OPTIMAL, IaidoBalance.spacing(2.0D));
    }

    @Test
    void closeCombatCannotKeepPreparedDamage() {
        assertEquals(1.05D, IaidoBalance.effectiveMultiplier(
                IaidoBalance.PREPARED_MULTIPLIER, IaidoBalance.Spacing.CLOSE), 0.0001D);
        assertEquals(0.75D, IaidoBalance.effectiveMultiplier(
                IaidoBalance.APPROACH_MULTIPLIER, IaidoBalance.Spacing.PRESSED), 0.0001D);
        assertEquals(0.75D, IaidoBalance.effectiveMultiplier(
                IaidoBalance.UNPREPARED_MULTIPLIER, IaidoBalance.Spacing.PRESSED), 0.0001D);
    }

    @Test
    void poorSpacingDoesNotNerfAnOtherwiseUnpreparedOptimalDraw() {
        assertEquals(1.15D, IaidoBalance.effectiveMultiplier(
                IaidoBalance.UNPREPARED_MULTIPLIER, IaidoBalance.Spacing.OPTIMAL), 0.0001D);
    }

    @Test
    void nativeDrawSlashesShareOneDamageBudget() {
        assertEquals(0.65D, IaidoBalance.drawSlashShare(0), 0.0001D);
        assertEquals(0.35D, IaidoBalance.drawSlashShare(1), 0.0001D);
        assertEquals(1.0D,
                IaidoBalance.drawSlashShare(0) + IaidoBalance.drawSlashShare(1),
                0.0001D);
        assertEquals(0.0D, IaidoBalance.drawSlashShare(2), 0.0001D);
    }

    @Test
    void perfectBonusScalesButRemainsCapped() {
        assertEquals(8.0F, IaidoBalance.perfectBonus(500.0F), 0.0001F);
        assertEquals(10.0F, IaidoBalance.perfectBonus(5000.0F), 0.0001F);
    }
}
