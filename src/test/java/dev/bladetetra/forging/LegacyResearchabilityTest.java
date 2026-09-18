package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyResearchabilityTest {
    @Test
    void intactNamedBladeStateCanBeResearched() {
        assertTrue(LegacyResearchability.stateAllowsResearch(false, false));
    }

    @Test
    void brokenOrSealedStateCannotBeResearched() {
        assertFalse(LegacyResearchability.stateAllowsResearch(true, false));
        assertFalse(LegacyResearchability.stateAllowsResearch(false, true));
        assertFalse(LegacyResearchability.stateAllowsResearch(true, true));
    }
}
