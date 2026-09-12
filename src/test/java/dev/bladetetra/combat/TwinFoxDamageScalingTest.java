package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinFoxDamageScalingTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void effectiveAttackMatchesReferenceValuesAndCapsAtSixtyFour() {
        assertEquals(10.0D, TwinFoxDamageScaling.effectiveAttack(10.0D), EPSILON);
        assertEquals(20.0D, TwinFoxDamageScaling.effectiveAttack(20.0D), EPSILON);
        assertEquals(26.4D, TwinFoxDamageScaling.effectiveAttack(30.0D), EPSILON);
        assertEquals(34.4D, TwinFoxDamageScaling.effectiveAttack(50.0D), EPSILON);
        assertEquals(54.4D, TwinFoxDamageScaling.effectiveAttack(100.0D), EPSILON);
        assertEquals(64.0D, TwinFoxDamageScaling.effectiveAttack(10_000.0D), EPSILON);
    }

    @Test
    void moonhuntSingleDoubleAndFinisherTotalsMatchTheNewFormula() {
        assertMoonhunt(10.0D, 4.0D, 8.0D, 8.5D, 16.5D);
        assertMoonhunt(20.0D, 8.0D, 16.0D, 17.0D, 33.0D);
        assertMoonhunt(30.0D, 10.56D, 21.12D, 22.44D, 43.56D);
        assertMoonhunt(50.0D, 13.76D, 27.52D, 29.24D, 56.76D);
        assertMoonhunt(100.0D, 21.76D, 43.52D, 46.24D, 89.76D);
        assertMoonhunt(10_000.0D, 25.6D, 51.2D, 54.4D, 105.6D);
    }

    @Test
    void pursuitDamageMatchesTheNewFormula() {
        assertEquals(6.5D, TwinFoxDamageScaling.snapshot(10.0D).pursuitDamage(), EPSILON);
        assertEquals(13.0D, TwinFoxDamageScaling.snapshot(20.0D).pursuitDamage(), EPSILON);
        assertEquals(17.16D, TwinFoxDamageScaling.snapshot(30.0D).pursuitDamage(), EPSILON);
        assertEquals(22.36D, TwinFoxDamageScaling.snapshot(50.0D).pursuitDamage(), EPSILON);
        assertEquals(35.36D, TwinFoxDamageScaling.snapshot(100.0D).pursuitDamage(), EPSILON);
        assertEquals(41.6D, TwinFoxDamageScaling.snapshot(10_000.0D).pursuitDamage(), EPSILON);
    }

    @Test
    void highAttackStillGrowsBeforeTheSoftCapAndStopsAtTheCap() {
        var attack50 = TwinFoxDamageScaling.snapshot(50.0D);
        var attack100 = TwinFoxDamageScaling.snapshot(100.0D);
        var attack124 = TwinFoxDamageScaling.snapshot(124.0D);
        var extreme = TwinFoxDamageScaling.snapshot(10_000.0D);

        assertTrue(attack100.effectiveAttack() > attack50.effectiveAttack());
        assertTrue(TwinFoxDamageScaling.moonhuntTotal(attack100, 2)
                > TwinFoxDamageScaling.moonhuntTotal(attack50, 2));
        assertEquals(64.0D, attack124.effectiveAttack(), EPSILON);
        assertEquals(attack124.effectiveAttack(), extreme.effectiveAttack(), EPSILON);
        assertEquals(TwinFoxDamageScaling.moonhuntTotal(attack124, 2),
                TwinFoxDamageScaling.moonhuntTotal(extreme, 2), EPSILON);
        assertEquals(attack124.pursuitDamage(), extreme.pursuitDamage(), EPSILON);
    }

    @Test
    void queuedDamageSnapshotDoesNotChangeAfterAWeaponSwap() {
        var castSnapshot = TwinFoxDamageScaling.snapshot(20.0D);
        var laterWeapon = TwinFoxDamageScaling.snapshot(100.0D);

        assertEquals(8.0D, castSnapshot.perFoxDamage(), EPSILON);
        assertEquals(17.0D, castSnapshot.finisherDamage(), EPSILON);
        assertEquals(13.0D, castSnapshot.pursuitDamage(), EPSILON);
        assertEquals(33.0D, TwinFoxDamageScaling.moonhuntTotal(castSnapshot, 2), EPSILON);

        assertFalse(castSnapshot.equals(laterWeapon));
        assertEquals(8.0D, castSnapshot.perFoxDamage(), EPSILON);
        assertEquals(17.0D, castSnapshot.finisherDamage(), EPSILON);
        assertEquals(13.0D, castSnapshot.pursuitDamage(), EPSILON);
    }

    @Test
    void cosmeticSlashPolicyCannotAddASecondDamagePass() {
        assertEquals(0.0D, LegacyFusionCombatSupport.VISUAL_SLASH_DAMAGE, EPSILON);
        assertTrue(LegacyFusionCombatSupport.VISUAL_SLASH_DETACH_SHOOTER);
    }

    private static void assertMoonhunt(double attack, double perFox,
            double twoFoxSubtotal, double finisher, double completeTotal) {
        var snapshot = TwinFoxDamageScaling.snapshot(attack);
        assertEquals(perFox, snapshot.perFoxDamage(), EPSILON);
        assertEquals(twoFoxSubtotal, snapshot.perFoxDamage() * 2.0D, EPSILON);
        assertEquals(finisher, snapshot.finisherDamage(), EPSILON);
        assertEquals(perFox, TwinFoxDamageScaling.moonhuntTotal(snapshot, 1), EPSILON);
        assertEquals(completeTotal, TwinFoxDamageScaling.moonhuntTotal(snapshot, 2), EPSILON);
    }
}
