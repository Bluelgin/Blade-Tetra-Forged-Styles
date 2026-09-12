package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RustReleaseBalanceTest {
    private static final double EPSILON = 1.0E-5D;

    @Test
    void comboPhasesFollowTheAuthoredDamageCurve() {
        assertEquals(1.0D, RustReleaseBalance.damageMultiplier(
                RustReleaseBalance.OPENING), EPSILON);
        assertEquals(1.0D, RustReleaseBalance.damageMultiplier(
                RustReleaseBalance.MIDDLE), EPSILON);
        assertEquals(1.10D, RustReleaseBalance.damageMultiplier(
                RustReleaseBalance.LATE), EPSILON);
        assertEquals(1.25D, RustReleaseBalance.damageMultiplier(
                RustReleaseBalance.FINISHER), EPSILON);
        assertEquals(0.12D, RustReleaseBalance.armorPenetration(
                RustReleaseBalance.MIDDLE), EPSILON);
        assertEquals(0.30D, RustReleaseBalance.armorPenetration(
                RustReleaseBalance.FINISHER), EPSILON);
    }

    @Test
    void armorCompensationOnlyHelpsAgainstArmor() {
        float raw = 20.0F;
        assertEquals(raw * 1.25F, RustReleaseBalance.adjustedIncomingDamage(
                raw, 0.0D, 0.0D, RustReleaseBalance.FINISHER), EPSILON);
        assertTrue(RustReleaseBalance.adjustedIncomingDamage(
                raw, 20.0D, 8.0D, RustReleaseBalance.FINISHER) > raw * 1.25F);
    }

    @Test
    void shortEdgeScalesSoftlyAndHasACap() {
        assertEquals(5.5D, RustReleaseBalance.edgeDamage(10.0D), EPSILON);
        assertEquals(18.0D, RustReleaseBalance.edgeDamage(100.0D), EPSILON);
    }
}
