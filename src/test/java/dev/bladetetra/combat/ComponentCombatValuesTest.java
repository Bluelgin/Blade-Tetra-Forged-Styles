package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentCombatValuesTest {
    @Test void extractionDoesNotChangeCombatBalance() {
        assertEquals(0.15, ComponentCombatValues.DRAW_BONUS);
        assertEquals(0.15, ComponentCombatValues.SPIRIT_BONUS);
        assertEquals(2, ComponentCombatValues.JUST_EXTRA_TICKS);
        assertEquals(40, ComponentCombatValues.SPIRIT_TICKS);
        assertEquals(.25F, ComponentCombatValues.GUARD_REDUCTION);
        assertEquals(1, ComponentCombatValues.GUARD_DURABILITY_COST);
    }
    @Test void rankFormulaPreservesIntegerRoundingAndMinimum() {
        assertEquals(1, ComponentCombatValues.rankGain(0));
        assertEquals(1, ComponentCombatValues.rankGain(49));
        assertEquals(1, ComponentCombatValues.rankGain(99));
        assertEquals(2, ComponentCombatValues.rankGain(100));
        assertEquals(20, ComponentCombatValues.rankGain(1000));
    }
}
