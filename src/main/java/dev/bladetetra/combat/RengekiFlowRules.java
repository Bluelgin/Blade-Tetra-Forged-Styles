package dev.bladetetra.combat;

import java.util.Set;

/** Pure constants/identity rules; native SlashBlade remains the attack/damage owner. */
final class RengekiFlowRules {
    static final double NATIVE_B_DAMAGE_MULTIPLIER = .85D;
    // Resharped 1.9.63's ordinary B rush slashes use 0.244 before our style tradeoff.
    // Reference one non-critical rush slash, not the multi-hit B7 finisher or an SA.
    static final double NATIVE_B_RUSH_DAMAGE_RATIO = .244D;
    static final double SPRINT_SHARE_OF_B_RUSH = .5D;
    static final float SPRINT_DAMAGE_RATIO = (float) (NATIVE_B_RUSH_DAMAGE_RATIO
            * NATIVE_B_DAMAGE_MULTIPLIER * SPRINT_SHARE_OF_B_RUSH);

    private static final Set<String> NATIVE_B_FLOW = Set.of(
            "combo_b1", "combo_b2", "combo_b3", "combo_b4", "combo_b5", "combo_b6", "combo_b7",
            "combo_b1_end", "combo_b1_end2", "combo_b1_end3",
            "combo_b_end", "combo_b_end2", "combo_b_end3", "combo_b7_end");

    static boolean isNativeBFlow(String namespace, String path) {
        return "slashblade".equals(namespace) && path != null && NATIVE_B_FLOW.contains(path);
    }

    static boolean isNativeAirFlow(String namespace, String path) {
        return "slashblade".equals(namespace) && path != null
                && (path.startsWith("aerial_rave_") || path.startsWith("aerial_cleave"));
    }

    private RengekiFlowRules() {}
}
