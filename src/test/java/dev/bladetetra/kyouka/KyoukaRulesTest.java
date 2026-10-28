package dev.bladetetra.kyouka;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KyoukaRulesTest {
    @Test void oneFormationAddsSeventyFivePercent() {
        assertEquals(15, KyoukaRules.budget(20));
        assertEquals(35, 20 + KyoukaRules.budget(20));
        assertEquals(15, KyoukaRules.budget(8)+KyoukaRules.budget(12));
        assertEquals(0, KyoukaRules.budget(Double.NaN));
        assertEquals(0, KyoukaRules.budget(Double.POSITIVE_INFINITY));
        assertEquals(0, KyoukaRules.budget(-20));
        assertEquals(0, KyoukaRules.budget(1025));
    }
    @Test void rankUnitsAndGradeAreBothAuthoritative() {
        assertTrue(KyoukaRules.canStart(1200,300,4,60));
        assertFalse(KyoukaRules.canStart(1199,300,4,60));
        assertFalse(KyoukaRules.canStart(1200,300,3,60));
        assertTrue(KyoukaRules.canStart(4000,1000,4,60));
        assertFalse(KyoukaRules.canStart(1200,1000,4,60));
        assertFalse(KyoukaRules.canStart(4000,0,4,60));
        assertFalse(KyoukaRules.canStart(1200,300,4,1201));
        assertFalse(KyoukaRules.canStart(1200,300,4,301));
        assertTrue(KyoukaRules.canStart(1200,300,4,300));
        assertTrue(KyoukaRules.sustain(900,300,3));
        assertFalse(KyoukaRules.sustain(899,300,3));
        assertTrue(KyoukaRules.sustain(3000,1000,3));
        assertFalse(KyoukaRules.sustain(2999,1000,3));
    }
    @Test void framesLoopReverseAndStopWithoutAuthoredTrajectories() {
        assertEquals(150, KyoukaRules.motionTime(50,100,300,1000,1,false));
        assertEquals(300, KyoukaRules.motionTime(500,100,300,1000,1,false));
        assertEquals(150, KyoukaRules.motionTime(250,100,300,1000,1,true));
        assertEquals(250, KyoukaRules.motionTime(50,300,100,1000,1,false));
        assertEquals(100, KyoukaRules.motionTime(50,100,100,1000,1,true));
        assertEquals(100, KyoukaRules.motionTime(Double.NaN,100,300,1000,1,false));
    }
}
