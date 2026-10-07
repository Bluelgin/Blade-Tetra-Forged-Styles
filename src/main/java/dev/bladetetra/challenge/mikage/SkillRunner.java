package dev.bladetetra.challenge.mikage;

/** One foreground cast, monotonic cast ids and exactly-once release/cleanup. */
public final class SkillRunner {
    private long sequence;
    private SkillExecution active;
    private CastScope scope;
    private boolean releasing;

    public boolean active() { return active != null; }
    public boolean windingUp() { return active != null && active.windingUp(); }
    public CastScope scope() { return scope; }

    public boolean start(SkillExecution execution) {
        if (active() || releasing) return false;
        active = java.util.Objects.requireNonNull(execution);
        scope = new CastScope(++sequence);
        try { execution.start(scope); }
        catch (RuntimeException ex) {
            try { stop(SkillExecution.StopReason.CANCELLED); }
            catch (RuntimeException cleanupFailure) { ex.addSuppressed(cleanupFailure); }
            throw ex;
        }
        return true;
    }

    public void tick() {
        if (!active()) return;
        SkillExecution.Status status;
        try { status = active.tick(); }
        catch (RuntimeException ex) {
            try { stop(SkillExecution.StopReason.CANCELLED); }
            catch (RuntimeException cleanupFailure) { ex.addSuppressed(cleanupFailure); }
            throw ex;
        }
        if (status != SkillExecution.Status.RUNNING) stop(switch (status) {
            case COMPLETE -> SkillExecution.StopReason.COMPLETE;
            case TARGET_LOST -> SkillExecution.StopReason.TARGET_LOST;
            case COUNTERED -> SkillExecution.StopReason.COUNTERED;
            default -> throw new IllegalStateException("Unexpected skill status");
        });
    }

    public void stop(SkillExecution.StopReason reason) {
        if (!active()) return;
        SkillExecution execution = active;
        CastScope released = scope;
        // Clear first so callbacks cannot stop the same cast twice.
        active = null;
        scope = null;
        releasing = true;
        RuntimeException failure = null;
        try { execution.stop(reason); }
        catch (RuntimeException ex) { failure = ex; }
        try { released.close(); }
        catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else failure.addSuppressed(ex);
        } finally { releasing = false; }
        if (failure != null) throw failure;
    }
}
