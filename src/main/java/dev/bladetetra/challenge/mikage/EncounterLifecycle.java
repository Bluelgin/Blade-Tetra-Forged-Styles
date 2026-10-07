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
        int desired = fraction <= 1.0 / 3.0 ? 3 : fraction <= 2.0 / 3.0 ? 2 : 1;
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
        phase = healthFraction <= 1.0 / 3.0 ? 3 : healthFraction <= 2.0 / 3.0 ? 2 : 1;
        state = State.OPENING;
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
