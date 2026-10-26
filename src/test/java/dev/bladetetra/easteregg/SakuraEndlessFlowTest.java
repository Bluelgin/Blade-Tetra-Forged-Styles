package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraEndlessFlowTest {
    @Test void bSeriesIsOnlyTheSevenActualAttackNodes() {
        for (int i = 1; i <= 7; i++) {
            assertTrue(SakuraFlowRules.isNativeBAttack(
                    new ResourceLocation("slashblade", "combo_b" + i)));
        }
        assertFalse(SakuraFlowRules.isNativeBAttack(
                new ResourceLocation("slashblade", "combo_b7_end")));
        assertFalse(SakuraFlowRules.isNativeBAttack(
                new ResourceLocation("blade_tetra", "rengeki_first")));
        assertFalse(SakuraFlowRules.isNativeBAttack(
                new ResourceLocation("slashblade", "combo_a1")));
        assertFalse(SakuraFlowRules.isNativeBAttack(null));
    }

    @Test void higherEntryRankAlwaysBurnsSlower() {
        int a = SakuraFlowRules.halfSecondCost(4, 120);
        int s = SakuraFlowRules.halfSecondCost(5, 120);
        int ss = SakuraFlowRules.halfSecondCost(6, 120);
        int sss = SakuraFlowRules.halfSecondCost(7, 120);
        assertEquals(60, a);
        assertEquals(53, s);
        assertEquals(48, ss);
        assertEquals(41, sss);
        assertTrue(a > s && s > ss && ss > sss);
    }

    @Test void burnHasPositiveMinimumEvenAtTinyConfigRates() {
        for (int rank = 4; rank <= 7; rank++) {
            assertTrue(SakuraFlowRules.halfSecondCost(rank, 1) >= 1);
        }
    }
}
