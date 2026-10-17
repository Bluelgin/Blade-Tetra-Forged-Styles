package dev.bladetetra.challenge.mikage;

import java.util.Map;
import java.util.UUID;

/** Immutable, server-observed facts; never a player's next input. */
public record CombatObservation(UUID target, long tick, double distance,
        double height, Map<Behavior, Double> evidence) {
    public enum Behavior { APPROACHING, RETREATING, GUARDING, AIRBORNE, PRESSURE, REPEATED_ART }

    public CombatObservation {
        evidence = Map.copyOf(evidence);
    }

    public double strength(Behavior behavior) {
        return Math.max(0, Math.min(1, evidence.getOrDefault(behavior, 0.0)));
    }
}
