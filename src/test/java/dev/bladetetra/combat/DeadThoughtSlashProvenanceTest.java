package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadThoughtSlashProvenanceTest {
    @Test
    void acceptsOnlySlashBladeSakuraEndLineage() {
        assertTrue(DeadThoughtSlashProvenance.isFinalSceneCombo(
                "slashblade", "sakura_end_left"));
        assertTrue(DeadThoughtSlashProvenance.isFinalSceneCombo(
                "slashblade", "sakura_end_finish2_air"));

        assertFalse(DeadThoughtSlashProvenance.isFinalSceneCombo(
                "slashblade", "judgement_cut"));
        assertFalse(DeadThoughtSlashProvenance.isFinalSceneCombo(
                "other_addon", "sakura_end_left"));
    }
}
