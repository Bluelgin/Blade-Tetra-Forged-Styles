package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.bladetetra.combat.StyleBranchRules.*;

class StyleBranchRulesTest {
    @Test void everyPhaseHasAFiniteWindowAndLocksUntilItsMinimum() {
        for (Phase phase : Phase.values()) {
            assertTrue(phase.minimumTick() > 0);
            assertTrue(phase.duration() > phase.minimumTick());
            for (Intent intent : Intent.values()) {
                for (int tick = 0; tick < phase.minimumTick(); tick++) {
                    assertEquals(phase, next(phase, tick, intent, !phase.aerial()), phase + " tick " + tick);
                }
            }
        }
    }
    @Test void unlockedNoClickDoesNotBlockNativeSlashArt() {
        for (Phase phase : Phase.values()) assertNull(next(phase, phase.minimumTick(), Intent.NONE, !phase.aerial()));
    }
    @Test void ordinaryRengekiIsShortAndFinite() {
        assertEquals(Phase.R_SECOND, next(Phase.R_FIRST, 3, Intent.ATTACK, true));
        assertEquals(Phase.R_FLURRY, next(Phase.R_SECOND, 3, Intent.ATTACK, true));
        assertEquals(Phase.R_FINISH, next(Phase.R_FLURRY, 16, Intent.ATTACK, true));
        assertEquals(Phase.R_RECOVERY, next(Phase.R_FINISH, 8, Intent.ATTACK, true));
        assertNull(next(Phase.R_RECOVERY, 6, Intent.ATTACK, true));
        assertEquals(Phase.R_RECOVERY, timeout(Phase.R_FLURRY));
        assertNull(timeout(Phase.R_RECOVERY));
    }
    @Test void commonDirectionsAreNativeAndRightClickOnly() {
        assertEquals(CommonMove.RAPID, commonMove(Intent.FORWARD, true, true));
        assertEquals(CommonMove.UPPER, commonMove(Intent.BACK, true, true));
        assertEquals(CommonMove.CLEAVE, commonMove(Intent.BACK, false, true));
        assertEquals(CommonMove.NONE, commonMove(Intent.FORWARD, false, true));
        for (Intent intent : Intent.values()) assertEquals(CommonMove.NONE, commonMove(intent, true, false));
        assertTrue(Phase.R_FLURRY.commonMinimumTick() < Phase.R_FLURRY.minimumTick());
        assertTrue(Phase.D_HEAVY.commonMinimumTick() > 14 + 1);
    }
    @Test void dangakuPauseIsAnInputWindowNotAutomaticDamage() {
        assertEquals(Phase.D_RETURN, next(Phase.D_SWEEP, 4, Intent.ATTACK, true));
        for (int tick = 4; tick < HEAVY_PAUSE_TICK; tick++)
            assertEquals(Phase.D_FINISH, next(Phase.D_RETURN, tick, Intent.ATTACK, true));
        for (int tick = HEAVY_PAUSE_TICK; tick <= Phase.D_RETURN.duration(); tick++)
            assertEquals(Phase.D_HEAVY, next(Phase.D_RETURN, tick, Intent.ATTACK, true));
        assertNull(next(Phase.D_RETURN, HEAVY_PAUSE_TICK, Intent.NONE, true));
        assertEquals(Phase.D_RECOVERY, timeout(Phase.D_RETURN));
    }
    @Test void heavyAttackCannotCancelIntoUpperSlashBeforeBothHits() {
        assertEquals(Phase.D_HEAVY, next(Phase.D_HEAVY, 18, Intent.BACK, true));
        assertEquals(Phase.D_RECOVERY, next(Phase.D_HEAVY, 19, Intent.BACK, true));
        assertTrue(Phase.D_HEAVY.minimumTick() > 14 + 1);
        assertTrue(Phase.R_FLURRY.minimumTick() > 14 + 1);
        assertTrue(Phase.R_AIR_DROP.minimumTick() > 7 + 1);
    }
    @Test void aerialRengekiUsesTheGroundChain() {
        assertEquals(Phase.R_AIR_SECOND, next(Phase.R_AIR_FIRST, 3, Intent.ATTACK, false));
        assertEquals(Phase.R_AIR_FLURRY, next(Phase.R_AIR_SECOND, 3, Intent.ATTACK, false));
        assertEquals(Phase.R_AIR_FINISH, next(Phase.R_AIR_FLURRY, 16, Intent.ATTACK, false));
        assertEquals(Phase.R_AIR_FINISH, next(Phase.R_AIR_FLURRY, 16, Intent.FORWARD, false));
        assertNull(next(Phase.R_AIR_FLURRY, 16, Intent.NONE, false));
        assertEquals(Phase.R_AIR_RECOVERY, next(Phase.R_AIR_FINISH, 8, Intent.ATTACK, false));
        assertEquals(Phase.R_AIR_DROP, next(Phase.R_AIR_RISE, 8, Intent.ATTACK, false));
        assertEquals(Phase.R_RECOVERY, next(Phase.R_AIR_DROP, 10, Intent.ATTACK, false));
    }
    @Test void aerialDangakuUsesTheSamePauseAsGround() {
        assertEquals(Phase.D_AIR_SECOND, next(Phase.D_AIR_FIRST, 4, Intent.ATTACK, false));
        assertEquals(Phase.D_AIR_FINISH, next(Phase.D_AIR_SECOND, 4, Intent.ATTACK, false));
        assertEquals(Phase.D_AIR_HEAVY, next(Phase.D_AIR_SECOND, 8, Intent.ATTACK, false));
        assertEquals(Phase.D_AIR_RECOVERY, next(Phase.D_AIR_HEAVY, 19, Intent.ATTACK, false));
        assertNull(next(Phase.D_AIR_FIRST, 4, Intent.NONE, false));
    }
    @Test void aerialAndGroundCommitmentWindowsMatch() {
        Phase[][] pairs = {{Phase.R_FIRST, Phase.R_AIR_FIRST}, {Phase.R_SECOND, Phase.R_AIR_SECOND},
                {Phase.R_FLURRY, Phase.R_AIR_FLURRY}, {Phase.R_FINISH, Phase.R_AIR_FINISH},
                {Phase.D_SWEEP, Phase.D_AIR_FIRST}, {Phase.D_RETURN, Phase.D_AIR_SECOND},
                {Phase.D_FINISH, Phase.D_AIR_FINISH}, {Phase.D_HEAVY, Phase.D_AIR_HEAVY}};
        for (Phase[] pair : pairs) {
            assertEquals(pair[0].minimumTick(), pair[1].minimumTick());
            assertEquals(pair[0].duration(), pair[1].duration());
            assertEquals(pair[0].commonMinimumTick(), pair[1].commonMinimumTick());
        }
    }
    @Test void landingOverridesEvenTheFirstWindupTick() {
        for (Phase phase : Phase.values()) if (phase.airAttack()) {
            for (Intent intent : Intent.values())
                assertEquals(groundedRecovery(phase), next(phase, 0, intent, true));
        }
    }
    @Test void circleFinisherHasItsOwnHarmlessRecoveryInGroundAndAir() {
        assertEquals(Phase.D_CIRCLE_RECOVERY, next(Phase.D_FINISH, 14, Intent.ATTACK, true));
        assertEquals(Phase.D_AIR_CIRCLE_RECOVERY, next(Phase.D_AIR_FINISH, 14, Intent.ATTACK, false));
        assertEquals(Phase.D_CIRCLE_RECOVERY, timeout(Phase.D_FINISH));
        assertEquals(Phase.D_AIR_CIRCLE_RECOVERY, timeout(Phase.D_AIR_FINISH));
        assertEquals(Phase.D_CIRCLE_RECOVERY, next(Phase.D_AIR_FINISH, 0, Intent.NONE, true));
        assertEquals(Phase.D_CIRCLE_RECOVERY, next(Phase.D_AIR_CIRCLE_RECOVERY, 0, Intent.NONE, true));
        assertNull(timeout(Phase.D_CIRCLE_RECOVERY));
        assertNull(timeout(Phase.D_AIR_CIRCLE_RECOVERY));
        assertEquals(Phase.D_RECOVERY, timeout(Phase.D_HEAVY));
    }
    @Test void landingFollowUpIsManualAndDivingCannotSpam() {
        for (Phase dive : new Phase[]{Phase.D_DIVE, Phase.R_DIVE}) {
            assertEquals(dive, next(dive, 25, Intent.BACK, false));
            assertNull(next(dive, 25, Intent.NONE, false));
        }
        assertEquals(Phase.D_SWEEP, next(Phase.D_LAND, 6, Intent.ATTACK, true));
        assertNull(next(Phase.D_LAND, 6, Intent.NONE, true));
        assertEquals(Phase.D_RECOVERY, timeout(Phase.D_LAND));
    }
    @Test void groundChainsDoNotRestartAfterWalkingOffAnEdge() {
        assertNull(next(Phase.R_SECOND, 3, Intent.FORWARD, false));
        assertNull(next(Phase.D_RETURN, 8, Intent.ATTACK, false));
        assertEquals(Phase.D_RECOVERY, next(Phase.D_AIR_SECOND, 5, Intent.ATTACK, true));
    }
}
