package dev.bladetetra.aura;

import dev.bladetetra.combat.BranchingStyleCombos;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleBranchRules;
import dev.bladetetra.customstyle.ComboCompatibilityResolver;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Positive allow-list of ordinary slash graphs; reject SA and scenic motions. */
public final class SwordAuraMotionFilter {
    // Include native continuations: A3 and other multi-hit moves emit real cuts
    // from their end nodes. Prefix matching would also admit unaudited new IDs.
    private static final Set<String> NATIVE_CUTS = Set.of(
            "combo_a1", "combo_a1_end", "combo_a1_end2", "combo_a2", "combo_a2_end", "combo_a2_end2",
            "combo_a3", "combo_a3_end", "combo_a3_end2", "combo_a3_end3", "combo_a4", "combo_a4_end",
            "combo_a4_ex", "combo_a4_ex_end", "combo_a4_ex_end2", "combo_a5ex", "combo_a5ex_end",
            "combo_b1", "combo_b1_end", "combo_b1_end2", "combo_b1_end3", "combo_b2", "combo_b3",
            "combo_b4", "combo_b5", "combo_b6", "combo_b7", "combo_b7_end", "combo_b_end", "combo_b_end2", "combo_b_end3",
            "combo_c", "combo_c_end", "upperslash", "rapid_slash", "rapid_slash_quick",
            "aerial_rave_a1", "aerial_rave_a1_end", "aerial_rave_a2", "aerial_rave_a2_end", "aerial_rave_a2_end2",
            "aerial_rave_a3", "aerial_rave_a3_end", "aerial_rave_b3", "aerial_rave_b3_end", "aerial_rave_b4", "aerial_rave_b4_end",
            "aerial_cleave", "aerial_cleave_loop");

    public static boolean permits(ResourceLocation combo, BladeStyle style) {
        if (style == BladeStyle.CUSTOM && combo != null && !"slashblade".equals(combo.getNamespace()))
            return ComboCompatibilityResolver.playable(combo);
        return permits(combo);
    }

    public static boolean permits(ResourceLocation combo) {
        if (combo == null) return false;
        String path = combo.getPath();
        if ("slashblade".equals(combo.getNamespace())) {
            return NATIVE_CUTS.contains(path);
        }
        if (!"blade_tetra".equals(combo.getNamespace())) return false;
        if (path.equals("iaido_draw") || path.equals("iaido_follow")
                || path.equals("iaido_return") || path.equals("iaido_finish")
                || path.equals("dangaku_spin") || path.equals("dangaku_rise")) return true;
        StyleBranchRules.Phase phase = BranchingStyleCombos.phase(combo);
        return phase != null && !phase.recovery() && !phase.dive()
                && phase != StyleBranchRules.Phase.R_JUMP
                && phase != StyleBranchRules.Phase.D_JUMP;
    }

    private SwordAuraMotionFilter() {}
}
