package dev.bladetetra.challenge;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Reuses physical arena locations while challenge ids remain unique for the
 * current server session. The highest allocated slot therefore follows peak
 * concurrent challenges instead of the total number of challenges ever opened.
 */
final class ChallengeSlotAllocator {
    private final PriorityQueue<Integer> available = new PriorityQueue<>();
    private final Set<Integer> active = new HashSet<>();
    private int nextSlot;

    int acquire() {
        int slot = available.isEmpty() ? nextSlot++ : available.remove();
        if (!active.add(slot)) {
            throw new IllegalStateException("Challenge arena slot is already active: " + slot);
        }
        return slot;
    }

    boolean release(int slot) {
        if (!active.remove(slot)) {
            return false;
        }
        available.add(slot);
        return true;
    }

    void reset() {
        available.clear();
        active.clear();
        nextSlot = 0;
    }

    int activeCount() {
        return active.size();
    }

    int allocatedSlotCount() {
        return nextSlot;
    }
}
