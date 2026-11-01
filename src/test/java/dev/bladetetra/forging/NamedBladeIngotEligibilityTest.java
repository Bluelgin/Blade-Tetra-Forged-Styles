package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Extraction and tooltip must share server-safe identity without loading OBJ on a server. */
class NamedBladeIngotEligibilityTest {
    @Test
    void extractionCostIsRoundedUpAndNeverZero() {
        assertEquals(1, NamedBladeIngotEligibility.cost(1));
        assertEquals(1, NamedBladeIngotEligibility.cost(10));
        assertEquals(6, NamedBladeIngotEligibility.cost(60));
        assertEquals(11, NamedBladeIngotEligibility.cost(101));
        assertEquals(100, NamedBladeIngotEligibility.cost(1000));
    }

    @Test
    void recipeAndTooltipUseTheSameAuthoritativeSourceGate() throws IOException {
        String recipe = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/recipe/NamedBladeIngotRecipe.java"));
        String tooltip = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/client/LegacyResearchTooltipClient.java"));
        String eligibility = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/forging/NamedBladeIngotEligibility.java"));
        assertTrue(recipe.contains("NamedBladeIngotEligibility.inspect(center).extractable()"));
        assertTrue(recipe.contains("NamedBladeIngotEligibility.cost"));
        assertTrue(tooltip.contains("NamedBladeIngotEligibility.inspect(event.getItemStack())"));
        assertTrue(tooltip.contains("LegacyResearchabilityClient.inspect(extraction.kind())"));
        assertTrue(eligibility.contains("LegacyResearchability.inspect(source)"));
        assertFalse(eligibility.contains("net.minecraft.client."));
        assertFalse(eligibility.contains("LegacyClientModelAnalysis"));
        assertFalse(eligibility.contains("LegacyModelAnalysis.analyze"));
    }
}
