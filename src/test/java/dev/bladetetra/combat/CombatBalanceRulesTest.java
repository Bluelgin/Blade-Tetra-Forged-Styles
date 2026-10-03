package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatBalanceRulesTest {
    @Test void defaultsAreExactlyNeutral() {
        assertEquals(1.0D, CombatBalanceRules.multiplier(1, 1, 1));
        assertEquals(10F, CombatBalanceRules.damage(10, 1, false));
    }
    @Test void multipliersComposeOnce() {
        assertEquals(6F, CombatBalanceRules.damage(10,
                CombatBalanceRules.multiplier(.75, .8, 1), false), .0001F);
    }
    @Test void inheritedBudgetIsNotScaledTwice() {
        assertEquals(6F, CombatBalanceRules.damage(6, .6, true));
    }
    @Test void eachIndependentHitGetsTheSameScale() {
        for (int hit = 0; hit < 20; hit++) assertEquals(5F, CombatBalanceRules.damage(10, .5, false));
    }
    @Test void zeroDisablesDamage() {
        assertEquals(0F, CombatBalanceRules.damage(100, CombatBalanceRules.multiplier(0, 1, 1), false));
    }
    @Test void finiteRangeAndInvalidInputsAreSafe() {
        assertEquals(1000, CombatBalanceRules.multiplier(100, 100, 100));
        assertEquals(0, CombatBalanceRules.multiplier(-1, 1, 1));
        assertEquals(1, CombatBalanceRules.multiplier(Double.NaN, Double.POSITIVE_INFINITY, 1));
        assertEquals(Float.MAX_VALUE, CombatBalanceRules.damage(Float.MAX_VALUE, 1000, false));
    }
}
