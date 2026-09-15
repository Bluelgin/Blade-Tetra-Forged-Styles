package dev.bladetetra.combat;

import java.util.Set;

/** Pure classifier kept separate so Final Scene provenance stays regression-testable. */
final class DeadThoughtSlashProvenance {
    private static final Set<String> FINAL_SCENE_COMBOS = Set.of(
            "sakura_end_left", "sakura_end_right",
            "sakura_end_finish", "sakura_end_finish2",
            "sakura_end_left_air", "sakura_end_right_air",
            "sakura_end_finish_air", "sakura_end_finish2_air");

    static boolean isFinalSceneCombo(String namespace, String path) {
        return "slashblade".equals(namespace) && FINAL_SCENE_COMBOS.contains(path);
    }

    private DeadThoughtSlashProvenance() {
    }
}
