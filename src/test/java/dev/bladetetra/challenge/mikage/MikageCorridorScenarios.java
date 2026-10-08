package dev.bladetetra.challenge.mikage;

import java.util.UUID;

public final class MikageCorridorScenarios {
    private static int assertions;
    public static void main(String[] args) {
        runAll(); System.out.println("Mikage corridor scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        fullNativeCompletionControlsThreePasses();
        contactsAreBoundedAndResetOnlyAtNextPass();
        parryStopsEveryPassAndCannotReenter();
        knockdownSharesBalanceAndRecoveryWithoutStacking();
        stoppingDuringFlightReleasesResourcesOnce();
    }
    private static GateCorridorSequence ready() {
        var s = new GateCorridorSequence();
        check(!s.parry(), "a deployment cannot be parried");
        for (int i = 0; i < 24; i++) s.tick();
        check(s.stage() == GateCorridorSequence.Stage.CUE, "deployment precedes cue");
        for (int i = 0; i < 16; i++) s.tick();
        check(s.stage() == GateCorridorSequence.Stage.APPROACH, "cue precedes arrival");
        return s;
    }
    private static void fullNativeCompletionControlsThreePasses() {
        var s = ready();
        for (int pass = 0; pass < 3; pass++) {
            s.approachComplete();
            for (int i = 0; i < 200; i++) s.tick();
            check(s.attacking(), "elapsed time cannot truncate native B");
            s.comboComplete(); check(!s.mayHit(1000), "exit has no damage"); s.exitComplete();
            check(s.pass() == pass + 1, "exactly one next pass");
            if (pass < 2) {
                for (int i = 0; i < 10 + 16 - (pass + 1) * 2; i++) s.tick();
                check(s.stage() == GateCorridorSequence.Stage.APPROACH, "each later entrance has a cue");
            }
        }
        check(s.stage() == GateCorridorSequence.Stage.LAND, "three passes land");
        s.landed(); check(s.stage() == GateCorridorSequence.Stage.COMPLETE, "landing completes");
    }
    private static void contactsAreBoundedAndResetOnlyAtNextPass() {
        var s = ready(); s.approachComplete();
        check(s.mayHit(100), "first contact allowed"); s.hit(100);
        check(!s.mayHit(109), "rapid native pulses cannot erase the contact interval");
        check(s.mayHit(110), "second contact after ten ticks"); s.hit(110);
        check(!s.mayHit(300), "all remaining native pulses are capped");
        s.comboComplete(); s.exitComplete();
        check(!s.mayHit(400), "interval cannot damage");
        for (int i = 0; i < 24; i++) s.tick(); s.approachComplete();
        check(s.mayHit(400), "new pass gets a fresh budget");
    }
    private static void parryStopsEveryPassAndCannotReenter() {
        var s = ready(); s.approachComplete(); s.hit(100); s.hit(110);
        check(s.parry(), "parry remains available after damage budget");
        check(s.stage() == GateCorridorSequence.Stage.DOWN, "parry cancels the whole release");
        check(!s.parry() && !s.mayHit(120), "one swing cannot knock down twice");
        for (int i = 0; i < 300; i++) s.tick();
        check(s.stage() == GateCorridorSequence.Stage.DOWN, "later gates cannot reactivate after parry");
        s.landed(); check(s.pass() == 0, "no remaining passes");
    }
    private static void knockdownSharesBalanceAndRecoveryWithoutStacking() {
        var state = new DuelDefenseState<Object>(); var player = new UUID(0, 1);
        check(!state.parried(player, 100), "knockdown earns one ordinary contact");
        state.openStagger(100, 40);
        check(state.staggerRemaining(100) == 40, "ordinary knockdown is forty ticks");
        check(!state.staggered(140) && state.progress(140) == 1, "short recovery retains progress");
        for (int t : new int[]{150, 160, 170}) check(!state.parried(player, t), "meter accumulates");
        check(state.parried(player, 180), "fifth contact breaks full balance");
        check(state.staggerRemaining(180) == 100, "full break replaces short timer");
        state.hitAccepted(181);
        check(state.staggerRemaining(181) == 89, "existing hit recovery remains in force");
    }
    private static void stoppingDuringFlightReleasesResourcesOnce() {
        var runner = new SkillRunner(); final int[] cleanup = {0};
        runner.start(new SkillExecution() {
            public void start(CastScope scope) { scope.own(() -> cleanup[0]++); }
            public Status tick() { return Status.RUNNING; }
            public boolean windingUp() { return true; }
            public void stop(StopReason reason) { }
        });
        runner.stop(SkillExecution.StopReason.TARGET_LOST); runner.stop(SkillExecution.StopReason.PHASE_CHANGE);
        check(cleanup[0] == 1 && !runner.active(), "disconnect/phase cleanup cannot release resources twice");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
