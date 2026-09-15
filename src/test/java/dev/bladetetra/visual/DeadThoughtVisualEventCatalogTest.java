package dev.bladetetra.visual;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadThoughtVisualEventCatalogTest {
    @Test
    void soulBreakAndCollapseEventsAreRegisteredExactlyOnce() {
        Set<?> unique = Set.of(DeadThoughtVisualEvents.ALL);
        assertEquals(DeadThoughtVisualEvents.ALL.length, unique.size());
        assertTrue(unique.contains(DeadThoughtVisualEvents.SOUL_BROKEN));
        assertTrue(unique.contains(DeadThoughtVisualEvents.SOUL_COLLAPSE));
    }
}
