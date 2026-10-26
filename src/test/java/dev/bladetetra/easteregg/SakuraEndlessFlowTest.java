package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraEndlessFlowTest {
    @Test void bSeriesIsOnlyTheSevenActualAttackNodes() {
        for (int i = 1; i <= 7; i++) {
            assertTrue(SakuraEndlessFlow.isNativeBAttack(
                    new ResourceLocation("slashblade", "combo_b" + i)));
        }
        assertFalse(SakuraEndlessFlow.isNativeBAttack(
                new ResourceLocation("slashblade", "combo_b7_end")));
        assertFalse(SakuraEndlessFlow.isNativeBAttack(
                new ResourceLocation("blade_tetra", "rengeki_first")));
        assertFalse(SakuraEndlessFlow.isNativeBAttack(
                new ResourceLocation("slashblade", "combo_a1")));
        assertFalse(SakuraEndlessFlow.isNativeBAttack(null));
    }

    @Test void higherEntryRankAlwaysBurnsSlower() {
        int a = SakuraEndlessFlow.halfSecondCost(4, 120);
        int s = SakuraEndlessFlow.halfSecondCost(5, 120);
        int ss = SakuraEndlessFlow.halfSecondCost(6, 120);
        int sss = SakuraEndlessFlow.halfSecondCost(7, 120);
        assertEquals(60, a);
        assertEquals(53, s);
        assertEquals(48, ss);
        assertEquals(41, sss);
        assertTrue(a > s && s > ss && ss > sss);
    }

    @Test void burnHasPositiveMinimumEvenAtTinyConfigRates() {
        for (int rank = 4; rank <= 7; rank++) {
            assertTrue(SakuraEndlessFlow.halfSecondCost(rank, 1) >= 1);
        }
    }
}
