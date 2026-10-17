package dev.bladetetra.aura;

import dev.bladetetra.combat.BranchingStyleCombos;
import dev.bladetetra.combat.StyleBranchRules;
import net.minecraft.resources.ResourceLocation;

/** Positive allow-list of ordinary slash graphs; reject SA and scenic motions. */
public final class SwordAuraMotionFilter {
    public static boolean permits(ResourceLocation combo) {
        if (combo == null) return false;
        String path = combo.getPath();
        if ("slashblade".equals(combo.getNamespace())) {
            return path.startsWith("combo_a")
                    || path.startsWith("combo_b")
                    || path.startsWith("aerial_rave_")
                    || path.equals("upperslash")
                    || path.equals("rapid_slash")
                    || path.equals("aerial_cleave")
                    || path.equals("aerial_cleave_loop");
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
