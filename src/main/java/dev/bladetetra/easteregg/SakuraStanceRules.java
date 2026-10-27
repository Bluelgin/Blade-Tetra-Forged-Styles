package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;

/** Pure action identity: follow-up/landing, B attacks and Slash Arts never toggle. */
public final class SakuraStanceRules {
    public static final String DISPERSED = "blade_tetra_awakened_v2_sakura_dispersed";
    public static final int DISPERSE = 7, CONDENSE = 8;
    public static int action(ResourceLocation combo) {
        if (combo == null || !"slashblade".equals(combo.getNamespace())) return 0;
        return switch (combo.getPath()) {
            case "aerial_cleave" -> DISPERSE;
            case "upperslash" -> CONDENSE;
            default -> 0;
        };
    }
    public static boolean changes(int action, boolean dispersed) {
        return action == DISPERSE && !dispersed || action == CONDENSE && dispersed;
    }
    public static boolean committed(int action, ResourceLocation current, long startedAt, long actualAt) {
        if ((action != DISPERSE && action != CONDENSE) || startedAt < 0 || actualAt < startedAt) return false;
        if (action(current) == action && actualAt == startedAt) return true;
        // Native cleave advances into its looping fall on tick 1, even without a target.
        return action == DISPERSE && actualAt <= startedAt + 1 && current != null
                && current.toString().equals("slashblade:aerial_cleave_loop");
    }
    private SakuraStanceRules() {}
}
