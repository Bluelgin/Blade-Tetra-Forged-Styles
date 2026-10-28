package dev.bladetetra.easteregg;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RaikiriRulesTest {
    private static RaikiriRules.Point p(double x,double y,double z) { return new RaikiriRules.Point(x,y,z); }
    @Test void resonanceUsesSweptSegmentRatherThanEndpointOnly() {
        assertEquals(1,RaikiriRules.distanceSquared(p(1.5,0,1),p(0,0,0),p(3.5,0,0)),1e-8);
        assertTrue(RaikiriRules.distanceSquared(p(1.5,0,1),p(0,0,0),p(3.5,0,0))<=4);
        assertTrue(RaikiriRules.distanceSquared(p(1.5,3,0),p(0,0,0),p(3.5,0,0))>4);
    }
    @Test void segmentDoesNotExtendPastLegalCollisionEndpoint() {
        assertEquals(9,RaikiriRules.distanceSquared(p(4,0,0),p(0,0,0),p(1,0,0)),1e-8);
        assertEquals(1,RaikiriRules.distanceSquared(p(-1,0,0),p(0,0,0),p(1,0,0)),1e-8);
    }
    @Test void zeroAndInvalidSegmentsAreSafe() {
        assertEquals(1,RaikiriRules.distanceSquared(p(1,0,0),p(0,0,0),p(0,0,0)));
        assertEquals(Double.POSITIVE_INFINITY,RaikiriRules.distanceSquared(p(Double.NaN,0,0),p(0,0,0),p(1,0,0)));
    }
    @Test void observedSegmentsRejectTeleportsGapsAndInvalidValues() {
        assertTrue(RaikiriRules.continuous(p(0,0,0),p(0.02,0,0),10,11,2.5));
        assertFalse(RaikiriRules.continuous(p(0,0,0),p(10,0,0),10,11,2.5));
        assertFalse(RaikiriRules.continuous(p(0,0,0),p(1,0,0),10,12,2.5));
        assertFalse(RaikiriRules.continuous(p(0,0,0),p(Double.NaN,0,0),10,11,2.5));
    }
    @Test void delaysBelongToEachRealSample() {
        assertEquals(6,RaikiriRules.delay(0,4,6,10));
        assertEquals(7,RaikiriRules.delay(1,4,6,10));
        assertEquals(8,RaikiriRules.delay(2,4,6,10));
        assertEquals(10,RaikiriRules.delay(3,4,6,10));
    }
    @Test void trailTotalAndSameTargetCapsSurviveRepeatAttempts() {
        var budget=new RaikiriRules.Budget(6,3);UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        assertEquals(2,budget.reserve(a,2));assertEquals(1,budget.reserve(a,2));
        assertEquals(0,budget.reserve(a,2));assertEquals(3,budget.reserve(b,20));
        assertEquals(0,budget.remaining());assertEquals(0,budget.reserve(UUID.randomUUID(),20));
    }
    @Test void invalidAmountsNeverPoisonBudget() {
        var budget=new RaikiriRules.Budget(6,3);UUID id=UUID.randomUUID();
        assertEquals(0,budget.reserve(id,Float.NaN));assertEquals(0,budget.reserve(id,-1));
        assertEquals(0,budget.reserve(null,2));assertEquals(6,budget.remaining());
        assertEquals(0,new RaikiriRules.Budget(Float.POSITIVE_INFINITY,3).reserve(id,2));
    }
    @Test void reservationIsConservativeWhenProtectionCancelsDamage() {
        var budget=new RaikiriRules.Budget(3,3);UUID target=UUID.randomUUID();
        assertEquals(3,budget.reserve(target,3)); // cancelled attempt is still consumed
        assertEquals(0,budget.reserve(target,3));
    }
    @Test void oneSwordRespondsOncePerStepAndChargeSaturates() {
        var charge=new RaikiriRules.Charge();UUID step=UUID.randomUUID();
        assertTrue(charge.resonate(step));assertFalse(charge.resonate(step));
        assertTrue(charge.consume());assertFalse(charge.consume());
        assertFalse(charge.resonate(step));assertTrue(charge.resonate(UUID.randomUUID()));
        assertFalse(charge.resonate(step));
        assertTrue(charge.resonate(UUID.randomUUID()));assertTrue(charge.consume());assertFalse(charge.consume());
    }
    @Test void swordsAndPlayersHaveIndependentChargeAndDamage() {
        UUID step=UUID.randomUUID(),target=UUID.randomUUID();
        var first=new RaikiriRules.Charge();var second=new RaikiriRules.Charge();
        assertTrue(first.resonate(step));assertTrue(second.resonate(step));
        assertTrue(first.consume());assertTrue(second.consume());
        var a=new RaikiriRules.Budget(3,3);var b=new RaikiriRules.Budget(3,3);
        assertEquals(3,a.reserve(target,3));assertEquals(3,b.reserve(target,3));
    }
    @Test void landingNeedsAnArmedNativeActionAndRealSupport() {
        assertTrue(RaikiriRules.landing(true,true,true,10));
        assertFalse(RaikiriRules.landing(false,true,true,10));
        assertFalse(RaikiriRules.landing(true,false,true,10));
        assertFalse(RaikiriRules.landing(true,true,false,10));
        assertFalse(RaikiriRules.landing(true,true,true,101));
    }
}
