package dev.bladetetra.challenge.mikage;

import java.util.UUID;
import static dev.bladetetra.challenge.mikage.CombatSpacingState.Motion.*;

/** Headless scenarios for spacing decisions; physical movement still requires in-game validation. */
public final class MikageSpacingScenarios {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
    private static int assertions;
    public static void main(String[] args) {
        runAll();
        System.out.println("Mikage spacing scenarios passed: " + assertions + " assertions");
    }
    public static void runAll() {
        assertions = 0;
        spacingHasHysteresisAndBriefSteps();
        continuousChaseExhaustsTwoRetreats();
        pausesAndBlockedRoutesCannotRefreshRetreats();
        onlyTheParticipantsExchangeRestoresFootwork();
        targetLossAndEncounterCleanupResetState();
    }
    private static void spacingHasHysteresisAndBriefSteps() {
        var state = new CombatSpacingState();
        check(state.update(A, 7, 0) == ORBIT, "normal band permits lateral circling");
        check(state.update(A, 8.5, 6) == ORBIT, "outer boundary does not oscillate into chase");
        check(state.update(A, 8.6, 12) == CLOSE, "retreating player is followed");
        check(state.update(A, 7.3, 13) == CLOSE, "approach continues through the hysteresis band");
        check(state.update(A, 7.2, 14) == ORBIT, "approach ends at the inner boundary");
        check(state.update(A, 5.5, 20) == ORBIT, "inner boundary does not start a dodge");
        check(state.update(A, 5.4, 26) == RETREAT && state.retreats() == 1, "close pressure starts one short retreat");
        check(state.update(A, 6.4, 37) == RETREAT && state.retreats() == 1, "one retreat cannot be charged every tick");
        check(state.update(A, 6.4, 38) == HOLD, "step expires after twelve ticks");
        check(state.update(A, 5, 45) == HOLD, "seven ticks of breathing space cannot become another dodge");
        check(state.update(A, 7, 46) == ORBIT, "ordinary footwork resumes after eight ticks");
        var escape = new CombatSpacingState();
        escape.update(A, 5, 100);
        check(escape.update(A, 6.5, 101) == HOLD, "reaching safe spacing ends retreat early");
    }
    private static void continuousChaseExhaustsTwoRetreats() {
        var state = new CombatSpacingState();
        check(state.update(A, 4, 0) == RETREAT, "first approach earns a retreat");
        check(state.update(A, 4, 12) == HOLD, "first retreat has a catchable pause");
        check(state.update(A, 4, 20) == RETREAT && state.retreats() == 2, "second retreat consumes the final budget");
        check(!state.exchangeRequired(A), "final retreat finishes before a melee commitment");
        check(state.update(A, 4, 32) == ENGAGE && state.exchangeRequired(A), "continued pursuit earns a melee exchange");
        check(state.update(A, 9, 33) == CLOSE, "boss closes rather than retreats after spending its budget");
        check(state.update(A, 5.3, 34) == CLOSE, "approach continues into melee range");
        check(state.update(A, 5.2, 35) == ENGAGE, "boss holds once in exchange range");
        boolean neverRetreats = true;
        for (int tick = 36; tick < 2036; tick++) {
            var motion = state.update(A, tick % 2 == 0 ? 3 : 12, tick);
            neverRetreats &= motion == ENGAGE || motion == CLOSE;
        }
        check(neverRetreats && state.retreats() == 2, "two thousand chase ticks cannot produce a third retreat");
    }
    private static void pausesAndBlockedRoutesCannotRefreshRetreats() {
        var state = new CombatSpacingState();
        state.update(A, 4, 0);
        state.pause(5);
        check(state.motion() == HOLD && state.retreats() == 1, "skill start releases routine movement but retains its budget");
        state.pause(100);
        check(state.update(A, 4, 107) == HOLD, "post-skill breathing time preserves recovery");
        check(state.update(A, 4, 108) == RETREAT && state.retreats() == 2, "skill cannot grant a fresh pair of retreats");
        state.blocked(109);
        check(state.motion() == HOLD && state.exchangeRequired(A), "blocked last retreat yields to an exchange");
        check(state.update(A, 4, 110) == ENGAGE, "being cornered gives the player contact instead of endless dodge retries");
        var firstBlocked = new CombatSpacingState();
        firstBlocked.update(A, 4, 0); firstBlocked.blocked(1);
        check(firstBlocked.update(A, 4, 8) == HOLD, "invalid route has a short retry pause");
        check(firstBlocked.update(A, 4, 9) == RETREAT && firstBlocked.retreats() == 2,
                "blocked route still consumes its attempt");
    }
    private static void onlyTheParticipantsExchangeRestoresFootwork() {
        var state = new CombatSpacingState();
        state.update(A, 4, 0); state.update(A, 4, 12); state.update(A, 4, 20); state.update(A, 4, 32);
        state.exchangeCompleted(B, 33);
        check(state.exchangeRequired(A) && !state.exchangeRequired(B), "teammate's exchange cannot reset this target's retreat budget");
        state.exchangeCompleted(null, 34);
        check(state.exchangeRequired(A), "missing participant cannot reset the budget");
        state.exchangeCompleted(A, 35);
        check(!state.exchangeRequired(A) && state.retreats() == 0 && state.motion() == HOLD,
                "completed melee or successful counter restores the budget and releases movement");
        check(state.update(A, 4, 42) == HOLD, "melee completion keeps a catchable pause");
        check(state.update(A, 4, 43) == RETREAT && state.retreats() == 1, "ordinary footwork resumes after the exchange");
    }
    private static void targetLossAndEncounterCleanupResetState() {
        var state = new CombatSpacingState();
        state.update(A, 4, 0); state.pause(1);
        check(state.update(B, 7, 2) == ORBIT && state.retreats() == 0, "new participant starts with independent spacing");
        state.exchangeCompleted(A, 3);
        check(state.motion() == ORBIT, "old participant's delayed completion cannot pause a new target");
        state.update(B, 4, 8); state.clear();
        check(state.motion() == HOLD && state.retreats() == 0 && !state.exchangeRequired(B), "phase/defeat/cancel cleanup clears movement decisions");
        check(state.update(A, 10, 9) == CLOSE, "cleanup cannot leave a stale retry timer");
        check(state.update(null, 4, 10) == HOLD && state.retreats() == 0, "lost target stops footwork");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            state.update(A, 4, 20);
            check(state.update(A, invalid, 21) == HOLD && state.retreats() == 0, "invalid distance cannot accumulate movement");
        }
    }
    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}
