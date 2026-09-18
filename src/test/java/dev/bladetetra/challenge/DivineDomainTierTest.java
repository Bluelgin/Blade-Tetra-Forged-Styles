package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivineDomainTierTest {
    @Test
    void thresholdsMatchRitualDesign() {
        assertEquals(DivineDomainTier.ECHO, DivineDomainTier.fromKills(0));
        assertEquals(DivineDomainTier.ECHO, DivineDomainTier.fromKills(50));
        assertEquals(DivineDomainTier.GRUDGE, DivineDomainTier.fromKills(51));
        assertEquals(DivineDomainTier.HUNDRED_GHOSTS, DivineDomainTier.fromKills(201));
        assertEquals(DivineDomainTier.ASURA, DivineDomainTier.fromKills(501));
        assertEquals(DivineDomainTier.AVICI, DivineDomainTier.fromKills(1001));
    }

    @Test
    void scalingChangesEncounterShape() {
        assertTrue(DivineDomainTier.AVICI.waves() > DivineDomainTier.ECHO.waves());
        assertTrue(DivineDomainTier.AVICI.concurrentForWave(4)
                > DivineDomainTier.ECHO.concurrentForWave(1));
        assertTrue(DivineDomainTier.AVICI.spawnDirections()
                > DivineDomainTier.ECHO.spawnDirections());
        assertTrue(DivineDomainTier.AVICI.pressureIntervalTicks() > 0);
        assertEquals(0, DivineDomainTier.ECHO.pressureIntervalTicks());
    }

    @Test
    void deadThoughtSealIsHighTierOnly() {
        assertFalse(DivineDomainTier.HUNDRED_GHOSTS.grantsDeadThoughtSeal());
        assertTrue(DivineDomainTier.ASURA.grantsDeadThoughtSeal());
        assertTrue(DivineDomainTier.AVICI.grantsDeadThoughtSeal());
    }
}
