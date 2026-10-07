package dev.bladetetra.challenge.mikage;

/** Mutable state belongs to this release instance, never to a shared skill catalog. */
public interface SkillExecution {
    enum Status { RUNNING, COMPLETE, TARGET_LOST, COUNTERED }
    enum StopReason { COMPLETE, TARGET_LOST, COUNTERED, PHASE_CHANGE, DEFEATED, CANCELLED }
    void start(CastScope scope);
    Status tick();
    void stop(StopReason reason);
}
