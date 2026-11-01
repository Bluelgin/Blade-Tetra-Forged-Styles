package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NamedMaterialStatsTest {
    @Test
    void mixedPartsContributeIndependentlyWithoutASecondSetBonus() {
        assertEquals(10, NamedMaterialStats.of(20).contribution("blade"));
        assertEquals(2, NamedMaterialStats.of(8).contribution("saya"));
        assertEquals(3, NamedMaterialStats.of(12).contribution("tsuba"));
        for (double attack : new double[]{0, 2, 5, 7.24, 12.67, 24, 100}) {
            var stats = NamedMaterialStats.of(attack);
            assertEquals(attack, stats.contribution("blade") + stats.contribution("saya")
                    + stats.contribution("tsuba"), 1e-9);
        }
    }

    @Test
    void ironBaselineAndHighTierBoundsKeepAncillaryStatsControlled() {
        var iron = NamedMaterialStats.of(5);
        assertEquals(250, iron.durability());
        assertEquals(84, iron.capacity());
        assertEquals(2, iron.integrityCost());
        var dragon = NamedMaterialStats.of(24);
        assertTrue(dragon.durability() < 8000, "MMT dragonsteel is 8000 durability");
        assertTrue(dragon.integrityCost() > iron.integrityCost());
        int previousDurability = 0;
        for (int attack = 0; attack <= 10000; attack++) {
            var stats = NamedMaterialStats.of(attack);
            assertTrue(stats.durability() >= previousDurability);
            assertTrue(stats.durability() <= 5000);
            assertTrue(stats.capacity() >= 60 && stats.capacity() <= 160);
            assertTrue(stats.integrityCost() >= 2 && stats.integrityCost() <= 5);
            previousDurability = stats.durability();
        }
    }
}
