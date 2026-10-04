package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static dev.bladetetra.combat.StyleBranchRules.Phase;

class DangakuCircleSlashRulesTest {
    @Test void fourSlashesKeepTheOldFinisherBudgetIncludingNativeCriticalScaling() {
        for (boolean critical : new boolean[]{true, false}) {
            double damage = .325D * DangakuCircleSlashRules.damageScale(critical)
                    * DangakuCircleSlashRules.SEGMENTS * (critical ? 1.1F : 1.0D);
            assertEquals(2 * .44D * .85D, damage, 1e-9);
            assertTrue(DangakuCircleSlashRules.damageScale(critical) > 0);
            assertTrue(DangakuCircleSlashRules.damageScale(critical) < 1);
        }
    }

    @Test void onlyTheOrdinaryFinisherBorrowsCircleSlash() {
        for (Phase phase : Phase.values()) {
            assertEquals(phase == Phase.D_FINISH || phase == Phase.D_AIR_FINISH,
                    DangakuCircleSlashRules.matches(phase));
        }
        assertFalse(DangakuCircleSlashRules.matches(null));
    }

    @Test void groundAndAirWaitForAllFourNativeSlashEventsBeforeCancel() {
        for (Phase phase : new Phase[]{Phase.D_FINISH, Phase.D_AIR_FINISH}) {
            // Timeline offset +1: the last Circle Slash event fires at tick 8.
            assertTrue(phase.minimumTick() > 8);
            assertTrue(phase.commonMinimumTick() > 8);
        }
    }
}
