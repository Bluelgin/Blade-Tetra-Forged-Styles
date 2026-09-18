package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DivineGuardStateTest {
    @Test void twoPlayersCannotSpendTheSameRitualRescueTwice() {
        var state=new DivineGuardState();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        assertTrue(state.tryUse(a,100));
        assertFalse(state.tryUse(b,100));
        assertTrue(state.protects(a,100));
        assertFalse(state.protects(b,100));
        assertTrue(state.protects(a,159));
        assertFalse(state.protects(a,160));
        assertFalse(state.tryUse(a,10000));
    }
    @Test void cleanupCannotRefundTheRescueButTheNextRitualGetsItsOwnBudget() {
        var old=new DivineGuardState(); UUID player=UUID.randomUUID();
        assertTrue(old.tryUse(player,10));
        old.revokeProtection();
        assertFalse(old.protects(player,11));
        assertTrue(old.used());
        assertFalse(old.tryUse(player,11));
        assertTrue(new DivineGuardState().tryUse(player,11));
    }
}
