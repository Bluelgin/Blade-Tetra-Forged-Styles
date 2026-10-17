package dev.bladetetra.aura;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SwordAuraMotionFilterTest {
    @Test void allowsOrdinaryNativeAndStyleCuts() {
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("slashblade", "combo_a3")));
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("slashblade", "combo_b1")));
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "iaido_draw")));
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "dangaku_spin")));
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "dangaku_cleave")));
        assertTrue(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "dangaku_air_heavy")));
    }

    @Test void rejectsSaAndNeutralAndSprintFallback() {
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("slashblade", "judgement_cut_slash")));
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("slashblade", "circle_slash")));
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("slashblade", "none")));
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "iaido_root")));
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "dangaku_charged_sweep")));
        assertFalse(SwordAuraMotionFilter.permits(new ResourceLocation("blade_tetra", "programmatic_fusion")));
    }
}
