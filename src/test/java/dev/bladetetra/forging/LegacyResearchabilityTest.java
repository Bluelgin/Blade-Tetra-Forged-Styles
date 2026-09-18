package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyResearchabilityTest {
    @Test
    void intactAndAuthoredSealedStatesRemainResearchable() {
        assertTrue(LegacyResearchability.stateAllowsResearch(false, false));
        assertTrue(LegacyResearchability.stateAllowsResearch(false, true));
    }

    @Test
    void brokenStateCannotAliasAnIntactCatalogEntry() {
        assertFalse(LegacyResearchability.stateAllowsResearch(true, false));
        assertFalse(LegacyResearchability.stateAllowsResearch(true, true));
    }
}
