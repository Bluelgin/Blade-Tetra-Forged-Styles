package dev.bladetetra.challenge.mikage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MikageReliabilityTest {
    @Test void reducedDamageNeverFabricatesAHitAtThePhaseFloor() {
        assertEquals(205, TrialDamageMath.preservePhaseFloor(205, 200));
        assertEquals(200, TrialDamageMath.preservePhaseFloor(190, 200));
        assertEquals(0, TrialDamageMath.preservePhaseFloor(0, 0));
        assertEquals(5, TrialDamageMath.limitPhaseDamage(5, 210, 200));
        assertEquals(10, TrialDamageMath.limitPhaseDamage(50, 210, 200));
    }
    @Test void finalCapStillAllowsArmorCompensationBeforeMitigation() {
        assertEquals(4, TrialDamageMath.capFinal(4, 20, TrialDamageMath.Impact.NORMAL, .55), .0001);
        assertEquals(4.8, TrialDamageMath.capFinal(20, 20, TrialDamageMath.Impact.NORMAL, .55), .0001);
        assertEquals(2, TrialDamageMath.capFinal(20, 20, TrialDamageMath.Impact.FAILURE, .1), .0001);
        assertEquals(0, TrialDamageMath.capFinal(Float.NaN, 20, TrialDamageMath.Impact.NORMAL, .55));
    }
    @Test void adaptiveTargetsDifferBySkillAndRespectConfiguredBounds() {
        assertEquals(TrialDamageMath.Impact.LIGHT, TrialDamageMath.impact(.22));
        assertEquals(TrialDamageMath.Impact.NORMAL, TrialDamageMath.impact(.88));
        assertEquals(TrialDamageMath.Impact.HEAVY, TrialDamageMath.impact(1.08));
        assertTrue(TrialDamageMath.Impact.HEAVY.targetFraction > TrialDamageMath.Impact.LIGHT.targetFraction);
        assertEquals(1.22, TrialDamageMath.adapt(1, .1, 2.2, 4), .0001);
        assertEquals(4, TrialDamageMath.adapt(4, .1, 2.2, 4));
        assertEquals(.65, TrialDamageMath.adapt(.65, 10, 2.2, 4), .0001);
    }
    @Test void repeatedEvasionCannotResetTheAbsoluteCastBudget() {
        var budget = new CastBudget(5, 2);
        for (int i = 0; i < 4; i++) { budget.arrivalSucceeded(); assertTrue(budget.tick()); }
        budget.arrivalSucceeded(); assertFalse(budget.tick());
    }
    @Test void failedArrivalsExitButARealArrivalResetsOnlyTheFailureBudget() {
        var budget = new CastBudget(20, 2);
        budget.arrivalFailed(); assertTrue(budget.tick());
        budget.arrivalSucceeded(); budget.arrivalFailed(); assertTrue(budget.tick());
        budget.arrivalFailed(); assertFalse(budget.tick());
    }
    @Test void aStoppedTickCannotTerminateItsReplacementCast() {
        var runner = new SkillRunner();
        class Execution implements SkillExecution {
            int stops;
            public void start(CastScope scope) { }
            public Status tick() { return Status.RUNNING; }
            public void stop(StopReason reason) { stops++; }
        }
        var replacement = new Execution();
        var first = new Execution() {
            public Status tick() {
                runner.stop(StopReason.COUNTERED);
                assertTrue(runner.start(replacement));
                return Status.COMPLETE;
            }
        };
        assertTrue(runner.start(first)); runner.tick();
        assertEquals(1, first.stops); assertEquals(0, replacement.stops); assertTrue(runner.active());
        runner.stop(SkillExecution.StopReason.CANCELLED); assertEquals(1, replacement.stops);
    }
}
