package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordAuraRenderPolicyTest {
    @Test void nativeFadeIsPreservedAndTransparentTailIsSkipped() {
        for (int lifetime : new int[]{10, 12, 14}) {
            for (int age = 0; age <= lifetime + 2; age++) {
                double remaining = Math.min(lifetime, Math.max(0, lifetime - age)) / (double) lifetime;
                int nativeAlpha = (int) (255 * Math.max(0, -Math.pow(remaining - 1, 4) + 0.75));
                assertEquals(nativeAlpha, SwordAuraRenderPolicy.alpha(lifetime, age));
            }
            assertEquals(191, SwordAuraRenderPolicy.alpha(lifetime, 0));
            assertEquals(0, SwordAuraRenderPolicy.alpha(lifetime, lifetime));
        }
        assertEquals(0, SwordAuraRenderPolicy.alpha(Float.NaN, 0));
        assertEquals(0, SwordAuraRenderPolicy.alpha(0, 0));
    }
    @Test void mainOnlyNeverAdmitsOptionalLayers() {
        assertFalse(SwordAuraRenderPolicy.allowDecoration(0, 0, 48, 32, 0));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(0, 2, 48, 0, 0));
    }
    @Test void fullDetailHonorsDistanceAndFrameBudget() {
        assertTrue(SwordAuraRenderPolicy.allowDecoration(48 * 48, 2, 48, 32, 31));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(48 * 48 + 1, 2, 48, 32, 0));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(0, 2, 48, 32, 32));
    }
    @Test void balancedHalvesDistanceAndBudget() {
        assertTrue(SwordAuraRenderPolicy.allowDecoration(24 * 24, 1, 48, 32, 15));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(24 * 24 + 1, 1, 48, 32, 0));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(0, 1, 48, 32, 16));
    }
    @Test void invalidDistancesDoNotSpendDecorationWork() {
        assertFalse(SwordAuraRenderPolicy.allowDecoration(Double.NaN, 2, 48, 32, 0));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(Double.POSITIVE_INFINITY, 2, 48, 32, 0));
        assertFalse(SwordAuraRenderPolicy.allowDecoration(-1, 2, 48, 32, 0));
    }
}
