package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SakuraEndlessFlowTest {
    @Test void bSeriesStopsAtSixAndRecognizesTheNativeSixthAdapter() {
        for (int i = 1; i <= 6; i++) {
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
        assertFalse(SakuraFlowRules.isNativeBAttack(new ResourceLocation("slashblade", "combo_b7")));
        assertEquals(6, SakuraFlowRules.stage(new ResourceLocation("blade_tetra", "awakened_sakura_b6")));
    }

    @Test void sixthCannotLeaveBeforeItsFinalNativeDamageTick() {
        for (int tick=0; tick<=6; tick++) assertFalse(SakuraFlowRules.completedB6(tick));
        assertTrue(SakuraFlowRules.completedB6(7));
        assertTrue(SakuraFlowRules.completedB6(8));
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
