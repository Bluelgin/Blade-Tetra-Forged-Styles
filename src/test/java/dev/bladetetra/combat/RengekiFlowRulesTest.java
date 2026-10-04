package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RengekiFlowRulesTest {
    @Test void sprintRatioIsFixedAndDoesNotCapPanelDamage() {
        assertEquals(.1037F, RengekiFlowRules.SPRINT_DAMAGE_RATIO, 1e-7F);
        for (double panel : new double[]{1, 10, 100, 1000, 1000000}) {
            assertEquals(panel * .1037D, panel * RengekiFlowRules.SPRINT_DAMAGE_RATIO, panel * 1e-7);
        }
    }
    @Test void sprintIsHalfAnOrdinaryBRushSlashNotHalfTheFormerOvertunedPulse() {
        double ordinary = RengekiFlowRules.NATIVE_B_RUSH_DAMAGE_RATIO
                * RengekiFlowRules.NATIVE_B_DAMAGE_MULTIPLIER;
        assertEquals(.5D, RengekiFlowRules.SPRINT_SHARE_OF_B_RUSH);
        assertEquals(ordinary * .5D, RengekiFlowRules.SPRINT_DAMAGE_RATIO, 1e-7);
        assertTrue(RengekiFlowRules.SPRINT_DAMAGE_RATIO < .35F / 2);
    }
    @Test void nativeBAndRecoveriesRetainTheFormerDamageTradeoff() {
        for (int beat = 1; beat <= 7; beat++)
            assertTrue(RengekiFlowRules.isNativeBFlow("slashblade", "combo_b" + beat));
        for (String recovery : new String[]{"combo_b1_end", "combo_b1_end2", "combo_b1_end3",
                "combo_b_end", "combo_b_end2", "combo_b_end3", "combo_b7_end"})
            assertTrue(RengekiFlowRules.isNativeBFlow("slashblade", recovery));
        assertEquals(.85D, RengekiFlowRules.NATIVE_B_DAMAGE_MULTIPLIER);
    }
    @Test void directionalAirAndSlashArtsAreNotIncludedInNativeBDamageTuning() {
        for (String path : new String[]{"none", "combo_b8", "rapid_slash", "upperslash", "aerial_rave_a1",
                "circle_slash", "judgement_cut", "rengeki_flurry"})
            assertFalse(RengekiFlowRules.isNativeBFlow("slashblade", path));
        assertFalse(RengekiFlowRules.isNativeBFlow("another_mod", "combo_b1"));
        assertFalse(RengekiFlowRules.isNativeBFlow(null, "combo_b1"));
        assertFalse(RengekiFlowRules.isNativeBFlow("slashblade", null));
    }
    @Test void nativeAirVisualsDoNotResizeSlashArts() {
        for (String path : new String[]{"aerial_rave_a1", "aerial_rave_b4", "aerial_cleave_landing"})
            assertTrue(RengekiFlowRules.isNativeAirFlow("slashblade", path));
        for (String path : new String[]{"judgement_cut_slash_air", "sakura_end_left_air", "circle_slash"})
            assertFalse(RengekiFlowRules.isNativeAirFlow("slashblade", path));
        assertFalse(RengekiFlowRules.isNativeAirFlow("another_mod", "aerial_rave_a1"));
    }
}
