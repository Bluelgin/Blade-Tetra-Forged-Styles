package dev.bladetetra.combat;

import java.util.ArrayList;
import java.util.List;

/** Executable without Minecraft; the same scenarios also run under JUnit in CI. */
public final class FusionHandoffScenarios {
    static FusionHandoff.Stage stage(String id, int safe) {
        return new FusionHandoff.Stage(id, 0, 300, 1F, 0, safe);
    }
    static FusionHandoff.Tracker tracker(int safe, int timeout) {
        return new FusionHandoff.Tracker(new FusionHandoff.Route(List.of(stage("A", safe)), 100),
                List.of(timeout), 0);
    }
    static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    static void shortSignature() {
        var tracker = tracker(2, 40);
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A", 0, 1));
        equal(FusionHandoff.Decision.READY, tracker.observe("A", 0, 2));
    }
    static void delayedSignature() {
        var tracker = tracker(22, 60);
        for (int tick = 0; tick < 22; tick++) {
            equal(FusionHandoff.Decision.WAIT, tracker.observe("A", 0, tick));
        }
        equal(FusionHandoff.Decision.READY, tracker.observe("A", 0, 22));
    }
    static void longRecovery() {
        var tracker = tracker(18, 1200);
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A", 0, 16));
        equal(FusionHandoff.Decision.READY, tracker.observe("A", 0, 18));
    }
    static void multiStage() {
        var route = new FusionHandoff.Route(List.of(stage("A1", -1), stage("A2", 6)), 100);
        var tracker = new FusionHandoff.Tracker(route, List.of(22, 30), 0);
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A1", 0, 16));
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A2", 22, 22));
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A2", 22, 27));
        equal(FusionHandoff.Decision.READY, tracker.observe("A2", 22, 28));
    }
    static void firstPollAfterProgression() {
        var route = new FusionHandoff.Route(List.of(stage("A1", -1), stage("A2", 2)), 100);
        var tracker = new FusionHandoff.Tracker(route, List.of(1, 30), 0);
        equal(FusionHandoff.Decision.WAIT, tracker.observe("A2", 1, 1));
        equal(FusionHandoff.Decision.READY, tracker.observe("A2", 1, 3));
    }
    static void interruption() {
        equal(FusionHandoff.Decision.INTERRUPTED, tracker(5, 40).observe("foreign", 1, 1));
        equal(FusionHandoff.Decision.INTERRUPTED, tracker(5, 40).observe("A", 2, 2));
        equal(FusionHandoff.Decision.INTERRUPTED, tracker(5, 40).observe("A", -1, 2));
        var route = new FusionHandoff.Route(List.of(stage("A1", -1), stage("A2", 2)), 100);
        var tracker = new FusionHandoff.Tracker(route, List.of(22, 30), 0);
        equal(FusionHandoff.Decision.INTERRUPTED, tracker.observe("A2", 2, 2));
    }
    static void watchdog() {
        var route = new FusionHandoff.Route(List.of(stage("loop", -1), stage("end", 2)), 10);
        var tracker = new FusionHandoff.Tracker(route, List.of(200, 30), 0);
        equal(FusionHandoff.Decision.EXPIRED, tracker.observe("loop", 0, 10));
    }
    static void softOverlapWindow() {
        var tracker = new FusionHandoff.SoftOverlapTracker("addon:A", 20, 0, 5, 20);
        equal(FusionHandoff.Decision.WAIT,
                tracker.observe("addon:A", 0, 4, 20));
        equal(FusionHandoff.Decision.READY,
                tracker.observe("addon:A", 0, 5, 20));
        equal(5L, tracker.due());
    }

    static void softOverlapProgressionAndInterruption() {
        var progressed = new FusionHandoff.SoftOverlapTracker("addon:A", 3, 0, 5, 20);
        equal(FusionHandoff.Decision.WAIT,
                progressed.observe("addon:A_end", 3, 3, 10));
        equal(FusionHandoff.Decision.READY,
                progressed.observe("addon:A_end", 3, 5, 10));

        var interrupted = new FusionHandoff.SoftOverlapTracker("addon:A", 10, 0, 5, 20);
        equal(FusionHandoff.Decision.INTERRUPTED,
                interrupted.observe("player:other", 3, 3, 8));

        var restarted = new FusionHandoff.SoftOverlapTracker("addon:A", 10, 0, 5, 20);
        equal(FusionHandoff.Decision.INTERRUPTED,
                restarted.observe("addon:A", 2, 2, 10));
    }

    static final class Source {
        final String name;
        final List<String> calls;
        Source(String name, List<String> calls) { this.name = name; this.calls = calls; }
        Source doArts() { calls.add(name + ".doArts"); return this; }
        void onClick() { calls.add(name + ".onClick"); }
        void tickAction() { calls.add(name + ".tickAction"); }
    }
    static List<String> execute(String a, String b) {
        List<String> calls = new ArrayList<>();
        Source first = new Source(a, calls), second = new Source(b, calls);
        Source[] active = { null };
        equal(true, FusionSourceExecution.enter(first::doArts, c -> true,
                c -> { active[0] = c; c.onClick(); }, c -> active[0] == c));
        var tracker = tracker(2, 40);
        for (int tick = 0; tick <= 2; tick++) {
            active[0].tickAction(); // normal inventory tick, BEFORE the fusion END observer
            if (tracker.observe("A", 0, tick) == FusionHandoff.Decision.READY) {
                equal(true, FusionSourceExecution.enter(second::doArts, c -> true,
                        c -> { active[0] = c; c.onClick(); }, c -> active[0] == c));
            }
        }
        active[0].tickAction(); // response is left installed for the source state machine
        return calls;
    }
    static void responseLifecycle() {
        equal(List.of("A.doArts", "A.onClick", "A.tickAction", "A.tickAction", "A.tickAction",
                "B.doArts", "B.onClick", "B.tickAction"), execute("A", "B"));
    }
    static void orderedIdentity() {
        equal(List.of("B.doArts", "B.onClick", "B.tickAction", "B.tickAction", "B.tickAction",
                "A.doArts", "A.onClick", "A.tickAction"), execute("B", "A"));
    }
    static void missingAndCancelled() {
        List<String> calls = new ArrayList<>();
        equal(false, FusionSourceExecution.enter(() -> null, c -> true,
                c -> calls.add("unexpected"), c -> true));
        equal(false, FusionSourceExecution.enter(() -> "invalid", c -> false,
                c -> calls.add("unexpected"), c -> true));
        equal(false, FusionSourceExecution.enter(() -> "valid", c -> true,
                c -> calls.add("cancelled motion"), c -> false));
        equal(List.of("cancelled motion"), calls);
        boolean failed = false;
        try {
            FusionSourceExecution.enter(() -> { throw new LinkageError("missing addon"); },
                    c -> true, c -> calls.add("unexpected"), c -> true);
        } catch (LinkageError expected) { failed = true; }
        equal(true, failed); // runtime catches it and executes its semantic response
    }
    static void nativeAudits() {
        var piercing = NativeFusionPresentationAudit.presentation("piercing").route("slashblade:piercing");
        equal(FusionHandoff.Policy.AUDITED_CHAIN, piercing.policy());
        var tracker = new FusionHandoff.Tracker(piercing, List.of(22, 15), 0);
        equal(FusionHandoff.Decision.WAIT, tracker.observe("slashblade:piercing", 0, 16));
        equal(FusionHandoff.Decision.WAIT, tracker.observe("slashblade:piercing_2", 22, 22));
        equal(FusionHandoff.Decision.READY, tracker.observe("slashblade:piercing_2", 22, 26));
        equal(23, NativeFusionPresentationAudit.presentation("void_slash")
                .route("slashblade:void_slash").stages().get(0).safeAfterTicks());
        equal(false, NativeFusionPresentationAudit.presentation("unknown").delegateRelease());
    }
    public static void main(String[] args) {
        shortSignature(); delayedSignature(); longRecovery(); multiStage(); firstPollAfterProgression();
        interruption(); watchdog(); softOverlapWindow(); softOverlapProgressionAndInterruption();
        responseLifecycle(); orderedIdentity(); missingAndCancelled(); nativeAudits();
        System.out.println("PASS: 13 fusion handoff lifecycle scenarios");
    }
}
