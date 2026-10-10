package dev.bladetetra.challenge.mikage;

import java.util.UUID;
import static dev.bladetetra.challenge.mikage.ThousandGatesSequence.Stage.*;
import static dev.bladetetra.challenge.mikage.ThousandGatesSequence.Contact.PARRY;
import static dev.bladetetra.challenge.mikage.ThousandGatesSequence.Contact.MISS;

/** Headless regressions for the authored sequence, separate from ordinary shared boss balance. */
public final class MikageThousandGatesScenarios {
    private static int assertions;
    public static void main(String[] args) {
        runAll();
        System.out.println("Mikage thousand gates scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        fiveSuccessesAccelerateAndBreak();
        evasionsResetTheStreakAndNeverFinish();
        acceptedHitsFinishAtEveryRound();
        unavailableArrivalsRetryBeforeAnyAttack();
        protectionDoesNotReuseInputOrFillTheOrdinaryMeter();
        executionLookupAndCancellationRemainScoped();
    }
    private static void fiveSuccessesAccelerateAndBreak() {
        var s = new ThousandGatesSequence();
        check(s.stage() == ENTER && s.remaining() == 12, "entry begins with a visible twelve-tick gate");
        for (int i=0; i<5; i++) {
            check(s.cueTicks() == 16-i*2 && s.revealTicks() == 8-i, "each successful round speeds up");
            arriveAtStrike(s);
            check(s.stage() == REVEAL && s.remaining() == 0, "contact waits for the announced strike");
            s.contact(PARRY);
            check(s.streak() == i+1, "exactly one success per real strike");
            check(s.terminal() == (i==4), "only the fifth success finishes");
        }
        check(s.stage() == BROKEN && !s.tick(), "fifth parry stops every future strike");
    }
    private static void evasionsResetTheStreakAndNeverFinish() {
        var s = new ThousandGatesSequence();
        for (int i=0;i<4;i++) { arriveAtStrike(s); s.contact(PARRY); }
        arriveAtStrike(s); s.contact(MISS);
        check(s.streak() == 0 && !s.terminal(), "miss after four successes resets without ending");
        check(s.cueTicks()+s.revealTicks() == 24, "new streak restores the learnable first rhythm");
        for (int i=0;i<1000;i++) { arriveAtStrike(s); s.contact(MISS); }
        check(!s.terminal() && s.streak() == 0, "evasion cannot exhaust a hidden attempt limit");
        for (int i=0;i<5;i++) { arriveAtStrike(s); s.contact(PARRY); }
        check(s.stage() == BROKEN, "five fresh successes still win after unlimited evasions");
    }
    private static void acceptedHitsFinishAtEveryRound() {
        for (int round=0; round<5; round++) {
            var s=new ThousandGatesSequence();
            for(int i=0;i<round;i++) { arriveAtStrike(s); s.contact(PARRY); }
            arriveAtStrike(s); s.contact(ThousandGatesSequence.Contact.HIT);
            check(s.stage() == HIT && s.terminal() && !s.tick(), "an accepted hit ends at every streak length");
        }
        var s=new ThousandGatesSequence();
        boolean rejected=false;
        try { s.contact(PARRY); } catch(IllegalStateException expected) { rejected=true; }
        check(rejected, "early contacts cannot create successes");
    }
    private static void unavailableArrivalsRetryBeforeAnyAttack() {
        var s=new ThousandGatesSequence();
        advanceTo(s,CUE);
        s.retryArrival();
        check(s.stage() == HIDDEN && s.remaining() == 10, "missing space returns to hidden retry");
        advanceTo(s,REVEAL);
        s.retryArrival();
        check(s.stage() == HIDDEN, "a blocked announced doorway is cancelled before revealing");
        arriveAtStrike(s); s.contact(PARRY);
        check(s.streak() == 1, "arrival retries do not consume or invent a parry");
    }
    private static void protectionDoesNotReuseInputOrFillTheOrdinaryMeter() {
        var state=new DuelDefenseState<Object>();
        UUID a=new UUID(0,1),b=new UUID(0,2); Object sword=new Object();
        for(int i=0;i<4;i++) state.parried(a,40+i*10);
        state.swing(a,80,sword);
        check(state.consumeSwing(a,80,sword), "first pursuit contact consumes actual input");
        state.protect(a,80);
        check(state.progress(80) == 4 && !state.staggered(80), "pursuit contacts leave normal meter untouched");
        check(state.protects(a,90) && !state.protects(b,90), "protection stays isolated during pursuit");
        check(!state.consumeSwing(a,90,sword), "protection never becomes a new parry input");
        state.swing(a,90,sword);
        check(state.consumeSwing(a,90,sword), "fresh input can parry inside old protection");
        state.breakBalance(91);
        check(state.staggerRemaining(91) == 100, "fifth pursuit success awards its full own stagger");
        state.clear();
        check(state.progress(91) == 0 && !state.protects(a,91), "phase cleanup removes shared effects");
    }
    private static void executionLookupAndCancellationRemainScoped() {
        var runner=new SkillRunner();
        var execution=new ProbeExecution();
        check(runner.execution(ProbeExecution.class) == null, "idle runner exposes no stale cast");
        check(runner.start(execution) && runner.execution(ProbeExecution.class) == execution,
                "active lookup returns the actual owned release");
        runner.stop(SkillExecution.StopReason.PHASE_CHANGE);
        check(execution.released == 1 && runner.execution(ProbeExecution.class) == null,
                "phase interruption releases scope once and drops active identity");
        runner.stop(SkillExecution.StopReason.CANCELLED);
        check(execution.released == 1, "repeated cleanup cannot release twice");
    }
    private static final class ProbeExecution implements SkillExecution {
        int released;
        public void start(CastScope scope) { scope.own(() -> released++); }
        public Status tick() { return Status.RUNNING; }
        public void stop(StopReason reason) { }
    }
    private static void arriveAtStrike(ThousandGatesSequence s) {
        int guard=100;
        while (!(s.stage() == REVEAL && s.remaining() == 0)) {
            if (--guard == 0 || s.terminal()) throw new AssertionError("Sequence never reaches a strike");
            s.tick();
        }
    }
    private static void advanceTo(ThousandGatesSequence s, ThousandGatesSequence.Stage stage) {
        int guard=100;
        while(s.stage()!=stage) { if(--guard==0) throw new AssertionError("Stage unreachable"); s.tick(); }
    }
    private static void check(boolean condition,String detail) {
        assertions++;
        if(!condition) throw new AssertionError(detail);
    }
}
