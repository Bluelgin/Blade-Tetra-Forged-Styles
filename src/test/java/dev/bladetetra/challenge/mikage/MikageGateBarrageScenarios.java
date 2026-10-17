package dev.bladetetra.challenge.mikage;

import java.util.*;

public final class MikageGateBarrageScenarios {
    private static int assertions;
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    public static void main(String[] args) {
        runAll(); System.out.println("Mikage gate barrage scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        warmupAndContinuousBoundedFire();
        threeIndependentInputsBreakSharedCore();
        invalidInputsCannotAdvanceCore();
        damageAndDepartureAreIsolated();
        phaseCancellationReleasesOnce();
    }
    private static GateBarrageSequence<Object> ready() {
        var s = new GateBarrageSequence<Object>();
        for (int i = 0; i < 9; i++) s.tick();
        check(s.stage() == GateBarrageSequence.Stage.OPEN, "opening lasts ten ticks");
        s.tick(); check(s.stage() == GateBarrageSequence.Stage.CHARGE, "opening precedes charge");
        check(!s.volley(10, 0) && !s.mayDamage(A, 10), "charge cannot fire or damage");
        for (int i = 0; i < 30; i++) s.tick();
        check(s.stage() == GateBarrageSequence.Stage.FIRING, "forty-tick warning precedes fire");
        return s;
    }
    private static void warmupAndContinuousBoundedFire() {
        var s = ready(); var sword = new Object(); s.swing(A, 1, sword);
        check(!s.coreHit(A, 40, sword), "a warmup swing expires before firing");
        check(s.volley(40, 59), "a complete five-sword batch fits the cap");
        check(!s.volley(40, 60) && !s.volley(40, 64), "never overfill the live entity budget");
        s.tick(); check(!s.volley(41, 0), "no additional batch between four-tick beats");
        for (int i = 0; i < 10000; i++) s.tick();
        check(s.stage() == GateBarrageSequence.Stage.FIRING && s.remainingHits() == 3,
                "waiting or dodging cannot exhaust the release");
        for (int i = 0; i < 3; i++) s.tick();
        check(s.volley(10044, 0), "capacity recovery resumes indefinite fire");
    }
    private static void threeIndependentInputsBreakSharedCore() {
        var s = ready(); var sword = new Object(); var other = new Object();
        s.swing(A, 100, sword); check(s.coreHit(A, 100, sword), "first sword input cracks core");
        check(s.remainingHits() == 2 && !s.volley(100, 0), "crack pauses the volley");
        s.swing(A, 100, sword);
        check(!s.coreHit(A, 112, sword), "same-tick duplicate cannot rearm a used input");
        check(!s.coreHit(A, 120, sword), "later pulses cannot reuse the first input");
        s.swing(B, 110, other); check(!s.coreHit(B, 111, other), "shared core contact interval");
        check(s.coreHit(B, 112, other), "second participant may advance the shared objective");
        check(!s.volley(123, 0) && s.volley(124, 0), "twelve-tick firing pause has an exact boundary");
        s.swing(A, 124, sword); check(s.coreHit(A, 124, sword), "third fresh input breaks core");
        check(s.remainingHits() == 0 && s.stage() == GateBarrageSequence.Stage.BREAK, "collapse precedes completion");
        check(!s.mayDamage(A, 125) && !s.volley(125, 0), "collapse cannot attack");
        check(!s.coreHit(B, 125, other), "collapse cannot accept a fourth crack");
        for (int i = 0; i < 15; i++) s.tick();
        check(s.stage() == GateBarrageSequence.Stage.BREAK, "collapse remains visible for sixteen ticks");
        s.tick(); check(s.stage() == GateBarrageSequence.Stage.COMPLETE, "collapse completes once");
        for (int i = 0; i < 100; i++) s.tick();
        check(s.stage() == GateBarrageSequence.Stage.COMPLETE, "cannot reactivate after core break");
    }
    private static void invalidInputsCannotAdvanceCore() {
        var s = ready(); var sword = new Object(); var identicalButDifferent = new Object();
        check(!s.coreHit(A, 100, sword), "damage without sword input is rejected");
        s.swing(A, 100, sword);
        check(!s.coreHit(B, 100, sword), "another participant cannot borrow input");
        check(!s.coreHit(A, 99, sword), "future input cannot authorize past damage");
        check(!s.coreHit(A, 100, identicalButDifferent), "switching blade invalidates input identity");
        check(!s.coreHit(A, 121, sword), "input expires after twenty ticks");
        s.swing(A, 200, sword);
        check(s.coreHit(A, 220, sword), "twenty-tick edge is accepted");
        s.swing(A, 240, null); check(!s.coreHit(A, 240, sword), "empty-hand input does not rearm");
    }
    private static void damageAndDepartureAreIsolated() {
        var s = ready(); var sword = new Object();
        check(s.mayDamage(A, 100), "first damage contact is allowed");
        check(s.mayDamage(A, 100), "rejected trial damage consumes no contact budget");
        s.damaged(A, 100);
        check(!s.mayDamage(A, 109) && s.mayDamage(A, 110), "native pulses respect ten-tick contact interval");
        check(s.mayDamage(B, 100), "party damage budget is independent");
        s.swing(A, 100, sword); s.swing(B, 100, sword);
        s.retain(Set.of(B), 101);
        check(!s.coreHit(A, 101, sword), "departed player's input is discarded");
        check(s.coreHit(B, 101, sword), "remaining party input survives target departure");
        s.swing(B, 200, sword); s.retain(Set.of(B), 221);
        check(!s.coreHit(B, 221, sword), "retention removes stale swings");
    }
    private static void phaseCancellationReleasesOnce() {
        for (var reason : new SkillExecution.StopReason[]{SkillExecution.StopReason.PHASE_CHANGE,
                SkillExecution.StopReason.TARGET_LOST, SkillExecution.StopReason.CANCELLED}) {
            var runner = new SkillRunner(); int[] cleanups = {0};
            runner.start(new SkillExecution() {
                public void start(CastScope scope) { scope.own(() -> cleanups[0]++); }
                public Status tick() { return Status.RUNNING; }
                public void stop(StopReason reason) { }
            });
            runner.stop(reason); runner.stop(reason);
            check(cleanups[0] == 1 && !runner.active(), "gate and live set cleanup is owned once: " + reason);
        }
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
