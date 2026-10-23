package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MikageChallengeAccessTest {
    @Test void unfinishedCombatCannotBeOpened() {
        assertFalse(MikageChallengeAccess.combatOpen());
        assertFalse(MikageChallengeAccess.allowsGate(false));
    }
    @Test void nonCombatVisitsRemainAvailable() {
        assertTrue(MikageChallengeAccess.allowsGate(true));
        assertFalse(MikageChallengeAccess.shouldSuspend(true, false));
    }
    @Test void onlyUnfinishedCombatIsSuspended() {
        assertTrue(MikageChallengeAccess.shouldSuspend(false, false));
        assertFalse(MikageChallengeAccess.shouldSuspend(false, true));
        assertFalse(MikageChallengeAccess.shouldSuspend(true, true));
    }
}
