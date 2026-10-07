package dev.bladetetra.challenge.mikage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.bladetetra.challenge.mikage.CombatObservation.Behavior.*;
import static dev.bladetetra.challenge.mikage.SkillSpec.Tactic.*;

/** Executable without a Minecraft bootstrap; also invoked by the JUnit suite. */
public final class MikageReactiveScenarios {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private static int assertions;

    public static void main(String[] args) {
        runAll();
        System.out.println("Mikage reactive scenarios passed: " + assertions + " assertions");
    }

    public static void runAll() {
        assertions = 0;
        observationsArePerPlayerAndExpire();
        directorRespondsToEvidenceAndLegalSkills();
        followUpsAreReevaluatedForTheSameTarget();
        runnerOwnsEachReleaseAndCancelsExactlyOnce();
        cleanupSurvivesFailuresAndStartExceptions();
        phasesAreMonotonicAndRecoveryDoesNotReplayTransitions();
    }

    private static void observationsArePerPlayerAndExpire() {
        PlayerBehaviorMemory memory = new PlayerBehaviorMemory();
        for (int tick = 1; tick <= 20; tick++) {
            memory.sample(A, new PlayerBehaviorMemory.Sample(tick, 8, 0, 0, true, false));
            memory.sample(B, new PlayerBehaviorMemory.Sample(tick, 12, 4, 0.15, false, true));
        }
        check(memory.observation(A, 20).strength(GUARDING) == 1, "guard habit");
        check(memory.observation(A, 20).strength(RETREATING) == 0, "other player's retreat is isolated");
        check(memory.observation(B, 20).strength(RETREATING) == 1, "observed retreat");
        check(memory.observation(B, 20).strength(AIRBORNE) == 1, "observed flight");
        memory.attack(A, 20);
        memory.attack(A, 20);
        check(memory.observation(A, 20).strength(PRESSURE) == 0.2, "multi-hit deduplication");
        for (int tick = 21; tick <= 24; tick++) {
            memory.sample(A, new PlayerBehaviorMemory.Sample(tick, 8, 0, 0, false, false));
            memory.slashArt(A, tick, "slashblade:judgement_cut");
        }
        check(memory.observation(A, 24).strength(REPEATED_ART) == 1, "repeated released art");
        for (int tick = 25; tick <= 90; tick++) memory.sample(A,
                new PlayerBehaviorMemory.Sample(tick, 8, 0, 0, false, false));
        check(memory.observation(A, 90).strength(GUARDING) == 0, "old guard habit decays");
        check(memory.observation(A, 90).strength(PRESSURE) == 0, "old hits expire");
        check(memory.observation(A, 90).strength(REPEATED_ART) == 0, "old arts expire");
        check(memory.observation(B, 90) == null, "stale target cannot drive decisions");
        memory.retain(Set.of(B), 90);
        check(memory.observation(A, 90) == null, "leaving participant releases memory");
    }

    private static SkillSpec spec(String id, SkillSpec.Tactic tactic) {
        return new SkillSpec("test:" + id, id, Set.of(tactic), 0, 20);
    }

    private static CombatObservation observation(UUID target, Map<CombatObservation.Behavior, Double> evidence) {
        return new CombatObservation(target, 100, 8, 0, evidence);
    }

    private static void directorRespondsToEvidenceAndLegalSkills() {
        var director = new ReactiveCombatDirector(List.of());
        SkillSpec guard = spec("guard", GUARD_PRESSURE), air = spec("air", ANTI_AIR);
        var guarding = observation(A, Map.of(GUARDING, 1.0));
        var airborne = observation(A, Map.of(AIRBORNE, 1.0));
        check(director.choose(guarding, List.of(air, guard), () -> 0).skill().equals(guard.id()), "guard reaction");
        check(director.choose(airborne, List.of(guard, air), () -> 0).skill().equals(air.id()), "air reaction");
        check(director.choose(guarding, List.of(air), () -> 0).skill().equals(air.id()), "unavailable skill is never selected");
        check(director.choose(guarding, List.of(), () -> 0) == null, "empty pool waits");
        director.committed(guard, A);
        check(director.choose(guarding, List.of(guard, air), () -> 0).skill().equals(air.id()), "repeat penalty permits variety");
        check(director.choose(guarding, List.of(guard), () -> 0).skill().equals(guard.id()), "single legal skill does not deadlock");
    }

    private static void followUpsAreReevaluatedForTheSameTarget() {
        SkillSpec setup = spec("setup", SETUP), chase = spec("chase", GAP_CLOSE), close = spec("close", CLOSE);
        var director = new ReactiveCombatDirector(List.of(new SkillTransition(setup.id(), chase.id(), RETREATING, 0.6, 3)));
        director.committed(setup, A);
        var decision = director.choose(observation(A, Map.of(RETREATING, 1.0)), List.of(chase, close), () -> 0);
        check(decision.skill().equals(chase.id()), "conditional follow-up");
        check(decision.reasons().contains("follow_up:retreating"), "decision is explainable");
        decision = director.choose(observation(A, Map.of(APPROACHING, 1.0)), List.of(chase, close), () -> 0);
        check(decision.skill().equals(close.id()), "changed player behavior invalidates planned follow-up");
        decision = director.choose(observation(B, Map.of(RETREATING, 1.0)), List.of(chase), () -> 0);
        check(!decision.reasons().contains("follow_up:retreating"), "follow-up does not transfer to a different player");
    }

    private static class FakeExecution implements SkillExecution {
        int starts, ticks, stops;
        CastScope scope;
        StopReason reason;
        final List<String> cleanup;
        FakeExecution(List<String> cleanup) { this.cleanup = cleanup; }
        public void start(CastScope scope) {
            starts++;
            this.scope = scope;
            scope.own(() -> cleanup.add("release:" + scope.id()));
        }
        public Status tick() { return ++ticks == 2 ? Status.COMPLETE : Status.RUNNING; }
        public void stop(StopReason reason) { stops++; this.reason = reason; }
    }

    private static void runnerOwnsEachReleaseAndCancelsExactlyOnce() {
        var runner = new SkillRunner();
        var cleanup = new ArrayList<String>();
        var first = new FakeExecution(cleanup);
        var second = new FakeExecution(cleanup);
        check(runner.start(first), "start first cast");
        check(!runner.start(second) && second.starts == 0, "foreground ownership");
        runner.tick();
        check(runner.active() && first.ticks == 1, "windup is not skipped");
        runner.stop(SkillExecution.StopReason.PHASE_CHANGE);
        runner.stop(SkillExecution.StopReason.CANCELLED);
        runner.tick();
        check(first.stops == 1 && first.reason == SkillExecution.StopReason.PHASE_CHANGE, "single stop callback");
        check(first.scope.closed() && cleanup.size() == 1, "single resource cleanup");
        check(first.ticks == 1, "cancelled skill never ticks again");
        check(runner.start(second) && second.scope.id() > first.scope.id(), "new release has fresh identity");
        runner.tick(); runner.tick();
        check(!runner.active() && second.reason == SkillExecution.StopReason.COMPLETE, "normal completion");
        check(second.stops == 1 && cleanup.size() == 2, "completion cleanup");
    }

    private static void cleanupSurvivesFailuresAndStartExceptions() {
        var order = new ArrayList<String>();
        CastScope scope = new CastScope(10);
        scope.own(() -> order.add("first"));
        scope.own(() -> { throw new IllegalStateException("cleanup"); });
        scope.own(() -> order.add("last"));
        try { scope.close(); throw new AssertionError("expected cleanup failure"); }
        catch (IllegalStateException expected) {}
        check(order.equals(List.of("last", "first")), "failure does not skip other owned resources");
        scope.close();
        scope.own(() -> order.add("late"));
        check(order.size() == 3, "late registration is released immediately");
        var runner = new SkillRunner();
        var failing = new FakeExecution(order) {
            public void start(CastScope scope) { super.start(scope); throw new IllegalStateException("start"); }
        };
        try { runner.start(failing); throw new AssertionError("expected start failure"); }
        catch (IllegalStateException expected) {}
        check(!runner.active() && failing.stops == 1 && failing.scope.closed(), "failed start releases ownership");
    }

    private static void phasesAreMonotonicAndRecoveryDoesNotReplayTransitions() {
        var lifecycle = new EncounterLifecycle();
        lifecycle.ready();
        check(lifecycle.observeHealth(0.6) && lifecycle.phase() == 2, "enter phase two");
        lifecycle.ready();
        check(!lifecycle.observeHealth(0.9) && lifecycle.phase() == 2, "healing cannot replay earlier phases");
        check(lifecycle.observeHealth(0.3) && lifecycle.phase() == 3, "enter phase three");
        check(lifecycle.defeat() && !lifecycle.defeat() && !lifecycle.cancel(), "terminal transition once");
        var recovered = new EncounterLifecycle();
        recovered.restore(0.2);
        check(recovered.phase() == 3 && !recovered.observeHealth(0.2), "restored boss does not replay phase dialogue");
        check(recovered.cancel() && !recovered.defeat(), "discard is not victory");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
