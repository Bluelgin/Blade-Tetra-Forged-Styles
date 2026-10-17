package dev.bladetetra.challenge.mikage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import static dev.bladetetra.challenge.mikage.CombatObservation.Behavior.*;
import static dev.bladetetra.challenge.mikage.SkillSpec.Tactic.*;

/** Chooses from currently legal skills. It does not execute or shorten them. */
public final class ReactiveCombatDirector {
    public record Decision(String skill, double score, List<String> reasons) {
        public Decision { reasons = List.copyOf(reasons); }
    }
    private final List<SkillTransition> transitions;
    private final ArrayDeque<SkillSpec> recent = new ArrayDeque<>();
    private UUID target;

    public ReactiveCombatDirector(List<SkillTransition> transitions) {
        this.transitions = List.copyOf(transitions);
    }

    public Decision choose(CombatObservation observation, Collection<SkillSpec> available,
            DoubleSupplier random) {
        if (observation == null || available.isEmpty()) return null;
        if (!observation.target().equals(target)) {
            recent.clear();
            target = observation.target();
        }
        Decision best = null;
        for (SkillSpec skill : available) {
            List<String> reasons = new ArrayList<>();
            double distance = observation.distance();
            double excess = Math.max(skill.minimumDistance() - distance, distance - skill.maximumDistance());
            double score = 2.0 - Math.max(0, excess) * 0.20;
            if (excess <= 0) reasons.add("preferred_distance");
            score += evidence(skill, observation, reasons);
            int index = 0;
            for (SkillSpec prior : recent) {
                if (prior.id().equals(skill.id())) {
                    score -= index == 0 ? 4.0 : 2.0;
                    reasons.add("repeat_penalty");
                }
                index++;
            }
            SkillSpec last = recent.peekFirst();
            if (last != null && last.family().equals(skill.family())) score -= 0.4;
            if (last != null) {
                for (SkillTransition transition : transitions) {
                    if (transition.from().equals(last.id()) && transition.to().equals(skill.id())
                            && observation.strength(transition.behavior()) >= transition.minimumEvidence()) {
                        score += transition.bonus();
                        reasons.add("follow_up:" + transition.behavior().name().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            score += Math.max(0, Math.min(1, random.getAsDouble())) * 0.35;
            if (best == null || score > best.score()) best = new Decision(skill.id(), score, reasons);
        }
        return best;
    }

    private static double evidence(SkillSpec skill, CombatObservation o, List<String> reasons) {
        double score = 0;
        for (SkillSpec.Tactic tactic : skill.tactics()) {
            var behavior = switch (tactic) {
                case GAP_CLOSE -> RETREATING;
                case CLOSE -> APPROACHING;
                case ANTI_AIR -> AIRBORNE;
                case GUARD_PRESSURE -> GUARDING;
                case COUNTER -> PRESSURE;
                case SETUP -> REPEATED_ART;
                case RANGED -> RETREATING;
            };
            double evidence = o.strength(behavior);
            if (evidence > 0) {
                score += evidence * 2.4;
                reasons.add("observed:" + behavior.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return score;
    }

    /** Only committed casts affect history; rejected or cancelled windups do not. */
    public void committed(SkillSpec skill, UUID participant) {
        if (!participant.equals(target)) recent.clear();
        target = participant;
        recent.addFirst(skill);
        while (recent.size() > 2) recent.removeLast();
    }

    public void reset() { recent.clear(); target = null; }
}
