package dev.bladetetra.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChallengeSlotAllocatorTest {
    @Test
    void allocatesDistinctSlotsForConcurrentChallenges() {
        ChallengeSlotAllocator slots = new ChallengeSlotAllocator();

        assertEquals(0, slots.acquire());
        assertEquals(1, slots.acquire());
        assertEquals(2, slots.acquire());
        assertEquals(3, slots.activeCount());
        assertEquals(3, slots.allocatedSlotCount());
    }

    @Test
    void reusesTheLowestReleasedSlot() {
        ChallengeSlotAllocator slots = new ChallengeSlotAllocator();
        int first = slots.acquire();
        int second = slots.acquire();
        int third = slots.acquire();

        assertTrue(slots.release(second));
        assertTrue(slots.release(first));
        assertEquals(first, slots.acquire());
        assertEquals(second, slots.acquire());
        assertEquals(3, slots.allocatedSlotCount());
        assertEquals(3, slots.activeCount());
        assertEquals(2, third);
    }

    @Test
    void sequentialChallengesStayInOnePhysicalSlot() {
        ChallengeSlotAllocator slots = new ChallengeSlotAllocator();

        for (int challenge = 0; challenge < 10_000; challenge++) {
            int slot = slots.acquire();
            assertEquals(0, slot);
            assertTrue(slots.release(slot));
        }

        assertEquals(1, slots.allocatedSlotCount());
        assertEquals(0, slots.activeCount());
    }

    @Test
    void duplicateReleaseCannotDuplicateAFreeSlot() {
        ChallengeSlotAllocator slots = new ChallengeSlotAllocator();
        int slot = slots.acquire();

        assertTrue(slots.release(slot));
        assertFalse(slots.release(slot));
        assertEquals(slot, slots.acquire());
        assertEquals(1, slots.acquire());
    }

    @Test
    void serverResetStartsFromTheFirstSlotAgain() {
        ChallengeSlotAllocator slots = new ChallengeSlotAllocator();
        slots.acquire();
        slots.acquire();

        slots.reset();

        assertEquals(0, slots.acquire());
        assertEquals(1, slots.activeCount());
        assertEquals(1, slots.allocatedSlotCount());
    }
}
