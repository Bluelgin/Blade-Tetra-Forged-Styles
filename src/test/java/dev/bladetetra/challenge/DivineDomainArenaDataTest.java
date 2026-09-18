package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivineDomainArenaDataTest {
    @Test
    void ritualCompositionKeepsStandUnderCentralTorii() {
        assertEquals(0, DivineDomainArenaData.RACK_X);
        assertEquals(-1, DivineDomainArenaData.RACK_Z);
        assertTrue(DivineDomainArenaData.ENTRY_Z > DivineDomainArenaData.FIRE_Z);
        assertTrue(DivineDomainArenaData.FIRE_X > DivineDomainArenaData.RACK_X,
                "ritual fire should remain to the player's right of the central stand");
    }

    @Test
    void everyEncounterSpawnStaysOnGuaranteedCombatFloor() {
        for (int directions = 2; directions <= 8; directions++) {
            for (int index = 0; index < 64; index++) {
                BlockPos spawn = DivineDomainArenaData.spawnPoint(0, 0, index, directions);
                assertTrue(DivineDomainArenaData.isGuaranteedCombatFloor(
                        spawn.getX(), spawn.getZ()),
                        () -> "spawn escaped connected combat floor: " + spawn);
            }
        }
    }

    @Test
    void hardBoundaryLeavesRoomForBrokenSceneryRing() {
        assertTrue(DivineDomainArenaData.HARD_BOUNDARY_RADIUS
                > DivineDomainArenaData.MAIN_ISLAND_RADIUS);
        assertTrue(DivineDomainArenaData.OUTER_RADIUS
                < DivineDomainArenaData.HARD_BOUNDARY_RADIUS);
    }
}
