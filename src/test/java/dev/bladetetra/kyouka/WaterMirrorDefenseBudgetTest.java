package dev.bladetetra.kyouka;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterMirrorDefenseBudgetTest {
    @Test void repeatedFormActivationCannotRefreshCooldownOrRollingBudget() {
        var budget = new WaterMirrorDefenseBudget();
        assertEquals(3, budget.available(0,10,300,300), .001);
        budget.commit(0,3);
        assertEquals(0,budget.available(59,100,300,300));
        assertEquals(3,budget.available(60,100,300,300));
        budget.commit(60,3);
        assertEquals(0,budget.available(120,100,300,300));
        assertEquals(3,budget.available(200,100,300,300));
        assertEquals(6,budget.available(260,100,300,300));
    }
    @Test void oxygenAndActualDamageBoundEveryReduction() {
        var budget=new WaterMirrorDefenseBudget();
        assertEquals(1.5,budget.available(0,10,150,300),.001);
        assertEquals(3,budget.available(0,10,3000,300),.001);
        assertEquals(0,budget.available(0,10,0,300));
        assertEquals(0,budget.available(0,Float.NaN,300,300));
        assertEquals(0,budget.available(0,10,300,0));
    }
}
