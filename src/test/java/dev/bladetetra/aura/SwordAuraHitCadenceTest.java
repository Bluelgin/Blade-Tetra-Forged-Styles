package dev.bladetetra.aura;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordAuraHitCadenceTest {
    @Test void differentProjectilesShareOneVictimBudget() {
        var cadence = new SwordAuraHitCadence<Object>();
        Object victim = new Object();
        assertTrue(cadence.tryHit(victim, 100, () -> true));
        for (int tick = 100; tick < 104; tick++)
            assertFalse(cadence.tryHit(victim, tick, () -> { fail("Cooldown must prevent hurt itself"); return true; }));
        assertTrue(cadence.tryHit(victim, 104, () -> true));
    }
    @Test void cancellationDoesNotRecordAHit() {
        var cadence = new SwordAuraHitCadence<Object>(); Object victim = new Object();
        assertFalse(cadence.tryHit(victim, 20, () -> false));
        assertTrue(cadence.tryHit(victim, 20, () -> true));
    }
    @Test void differentVictimsAndNestedDamageStayIsolated() {
        var cadence = new SwordAuraHitCadence<Object>(); Object first = new Object(), second = new Object();
        assertTrue(cadence.tryHit(first, 30, () -> {
            assertFalse(cadence.tryHit(first, 30, () -> true));
            assertTrue(cadence.tryHit(second, 30, () -> true));
            return true;
        }));
    }
    @Test void exceptionsReleaseTheReservationAndClockRewindsCannotSpam() {
        var cadence = new SwordAuraHitCadence<Object>(); Object victim = new Object();
        assertThrows(IllegalStateException.class,
                () -> cadence.tryHit(victim, 30, () -> { throw new IllegalStateException(); }));
        assertTrue(cadence.tryHit(victim, 30, () -> true));
        assertFalse(cadence.tryHit(victim, 29, () -> true));
    }
}
