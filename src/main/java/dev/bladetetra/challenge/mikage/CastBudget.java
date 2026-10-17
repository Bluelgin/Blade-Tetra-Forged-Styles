package dev.bladetetra.challenge.mikage;

/** Independent watchdog: retries and successful evasions cannot keep a cast alive forever. */
public final class CastBudget {
    private final int maximumTicks, maximumFailures;
    private int age, failures;
    public CastBudget(int maximumTicks, int maximumFailures) {
        if (maximumTicks < 1 || maximumFailures < 1) throw new IllegalArgumentException("Positive cast limits required");
        this.maximumTicks = maximumTicks; this.maximumFailures = maximumFailures;
    }
    public boolean tick() { return ++age < maximumTicks && failures < maximumFailures; }
    public void arrivalFailed() { failures++; }
    public void arrivalSucceeded() { failures = 0; }
}
