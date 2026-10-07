package dev.bladetetra.challenge.mikage;

/** Monotonic phases survive healing; terminal transitions happen once. */
public final class EncounterLifecycle {
    public enum State { OPENING, COMBAT, TRANSITION, DEFEATED, CANCELLED }
    private State state = State.OPENING;
    private int phase = 1;

    public State state() { return state; }
    public int phase() { return phase; }
    public boolean terminal() { return state == State.DEFEATED || state == State.CANCELLED; }

    public boolean observeHealth(double fraction) {
        if (terminal()) return false;
        int desired = phaseFor(fraction);
        if (desired <= phase) return false;
        // Enter one narrative phase at a time, including after entity restoration.
        phase++;
        state = State.TRANSITION;
        return true;
    }

    public void ready() { if (!terminal()) state = State.COMBAT; }

    /** Recovery is state restoration, not a new narrative phase transition. */
    public void restore(double healthFraction) {
        if (terminal()) return;
        phase = phaseFor(healthFraction);
        state = State.OPENING;
    }

    private static int phaseFor(double fraction) {
        // Minecraft stores health as float; a gated 2/3 health ratio can round upward.
        // Without tolerance, the damage gate could prevent the next phase forever.
        return fraction <= 1.0 / 3.0 + 1.0e-6 ? 3 : fraction <= 2.0 / 3.0 + 1.0e-6 ? 2 : 1;
    }

    public boolean defeat() {
        if (terminal()) return false;
        state = State.DEFEATED;
        return true;
    }

    public boolean cancel() {
        if (terminal()) return false;
        state = State.CANCELLED;
        return true;
    }
}
