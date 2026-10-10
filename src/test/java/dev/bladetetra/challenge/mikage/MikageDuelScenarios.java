package dev.bladetetra.challenge.mikage;

import java.util.Set;
import java.util.UUID;

/** Executable balance/input regressions, adapted to multiplayer from the Black Fox mechanics. */
public final class MikageDuelScenarios {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private static int assertions;
    public static void main(String[] args) {
        runAll();
        System.out.println("Mikage duel scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        swingIsRecentSingleUseAndBoundToTheActualWeapon();
        counterHasAnEightTickGuardAndSixtyTickCooldown();
        protectionIsPerPlayerAndDoesNotFillBalanceFromMultiHits();
        fiveContactsBreakBalanceAndHitsAccelerateRecovery();
        leavingAndEncounterCleanupReleaseInputAndProtection();
    }

    private static void swingIsRecentSingleUseAndBoundToTheActualWeapon() {
        var state = new DuelDefenseState<Object>();
        Object blade = new Object(), swapped = new Object();
        state.swing(A, 100, blade);
        check(!state.consumeSwing(B, 100, blade), "another player's swing cannot parry");
        check(!state.consumeSwing(A, 99, blade), "future-dated input is rejected");
        check(!state.consumeSwing(A, 101, swapped), "changing swords loses the input window");
        check(state.consumeSwing(A, 103, blade), "third tick is still in the copied window");
        check(!state.consumeSwing(A, 103, blade), "one swing cannot parry twice");
        state.swing(A, 100, blade);
        check(!state.consumeSwing(A, 103, blade), "duplicate native/vanilla event cannot rearm a consumed swing");
        state.swing(A, 110, blade);
        check(!state.consumeSwing(A, 114, blade), "fourth tick is too late");
        state.swing(A, 120, blade);
        check(state.consumeSwing(A, 120, blade), "fresh next swing can parry");
    }

    private static void counterHasAnEightTickGuardAndSixtyTickCooldown() {
        var state = new DuelDefenseState<Object>();
        check(state.counterReady(10), "initial counter is ready");
        state.counterStarted(10);
        check(state.guarding(17), "guard protects through tick seven");
        check(!state.guarding(18), "guard is gone before the answering hit at tick ten");
        check(!state.counterReady(69), "no repeated invulnerability during cooldown");
        check(state.counterReady(70), "counter releases at exactly sixty ticks");
        state.cancelGuard();
        check(state.counterReady(70), "cancelling a cast does not add permanent guard");
    }

    private static void protectionIsPerPlayerAndDoesNotFillBalanceFromMultiHits() {
        var state = new DuelDefenseState<Object>();
        check(!state.parried(A, 100), "first parry is not a balance break");
        check(state.protects(A, 123), "successful player has twenty-four tick protection");
        check(!state.protects(A, 124), "protection expires at its boundary");
        check(!state.protects(B, 101), "teammate receives no free immunity");
        check(!state.parried(B, 101), "simultaneous contacts do not break balance");
        check(state.progress(101) == 1, "multi-hit contact counts only once");
        check(state.protects(B, 124), "teammate's own parry still earns its own protection");
    }

    private static void fiveContactsBreakBalanceAndHitsAccelerateRecovery() {
        var state = new DuelDefenseState<Object>();
        for (int i = 0; i < 4; i++) check(!state.parried(i % 2 == 0 ? A : B, 100 + i * 60), "first four contacts accumulate");
        check(state.parried(A, 340), "fifth distinct contact breaks shared boss balance");
        check(state.progress(340) == 5 && state.staggerRemaining(340) == 100, "readable full meter and five-second opening");
        check(!state.counterReady(340), "boss cannot guard during its reward opening");
        state.hitAccepted(350);
        check(state.staggerRemaining(350) == 80, "accepted damage advances recovery by ten ticks");
        state.parried(B, 351);
        check(state.staggerRemaining(351) == 79, "contacts during stagger cannot restart the opening");
        check(!state.staggered(430) && state.progress(430) == 0, "recovery releases and resets the meter");
        state.parried(A, 500);
        for (int i = 1; i <= 4; i++) state.parried(A, 500 + i * 60);
        for (int i = 0; i < 20; i++) state.hitAccepted(740);
        check(state.staggerRemaining(740) == 1, "burst cannot skip straight past the final recovery tick");
    }

    private static void leavingAndEncounterCleanupReleaseInputAndProtection() {
        var state = new DuelDefenseState<Object>(); Object blade = new Object();
        state.swing(A, 10, blade); state.parried(A, 10);
        state.retain(Set.of(B), 11);
        check(!state.consumeSwing(A, 11, blade) && !state.protects(A, 11), "departed participant loses input and protection");
        state.swing(B, 20, blade); state.counterStarted(20); state.parried(B, 20);
        state.clear();
        check(!state.consumeSwing(B, 20, blade) && !state.protects(B, 20), "phase/defeat cleanup clears every participant");
        check(!state.guarding(20) && state.counterReady(20) && state.progress(20) == 0, "cleanup resets guard/cooldown/balance");
    }
    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}
