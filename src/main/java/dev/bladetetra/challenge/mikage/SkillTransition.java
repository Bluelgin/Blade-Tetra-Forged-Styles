package dev.bladetetra.challenge.mikage;

/** Authored follow-up preference, evaluated again at a legal decision boundary. */
public record SkillTransition(String from, String to, CombatObservation.Behavior behavior,
        double minimumEvidence, double bonus) {
    public SkillTransition {
        if (from == null || to == null || from.equals(to) || behavior == null
                || !Double.isFinite(minimumEvidence) || minimumEvidence < 0 || minimumEvidence > 1
                || !Double.isFinite(bonus) || bonus < 0 || bonus > 4) {
            throw new IllegalArgumentException("Invalid skill transition");
        }
    }
}
