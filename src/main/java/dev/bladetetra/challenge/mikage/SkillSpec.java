package dev.bladetetra.challenge.mikage;

import java.util.Set;

/** Selection metadata only. Each release creates a separate SkillExecution. */
public record SkillSpec(String id, String family, Set<Tactic> tactics,
        double minimumDistance, double maximumDistance) {
    public enum Tactic { CLOSE, GAP_CLOSE, RANGED, ANTI_AIR, COUNTER, GUARD_PRESSURE, SETUP }

    public SkillSpec {
        if (id == null || id.isBlank() || family == null || family.isBlank()
                || !Double.isFinite(minimumDistance) || !Double.isFinite(maximumDistance)
                || minimumDistance < 0 || maximumDistance < minimumDistance) {
            throw new IllegalArgumentException("Invalid skill selection metadata");
        }
        tactics = Set.copyOf(tactics);
    }
}
