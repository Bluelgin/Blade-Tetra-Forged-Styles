package dev.bladetetra.combat;

import java.util.List;
import java.util.Objects;

/** Pure lifecycle observer. It never executes or predicts source callbacks. */
public final class FusionHandoff {
    public enum Policy { SIGNATURE_WINDOW, AUDITED_CHAIN }
    public enum Decision { WAIT, READY, INTERRUPTED, EXPIRED }

    /** Shape pins the audited registry entry; -1 means this stage must progress. */
    public record Stage(String combo, int start, int end, float speed,
            int extraTimeoutMs, int safeAfterTicks) {
        public Stage {
            Objects.requireNonNull(combo);
            if (speed <= 0 || safeAfterTicks < -1) {
                throw new IllegalArgumentException("Invalid stage");
            }
        }
    }

    public record Route(List<Stage> stages, int deadlineTicks) {
        public Route {
            stages = List.copyOf(stages);
            if (stages.isEmpty() || deadlineTicks < 1
                    || stages.get(stages.size() - 1).safeAfterTicks() < 0) {
                throw new IllegalArgumentException("An audit needs a bounded signature endpoint");
            }
        }

        public Policy policy() {
            return stages.size() == 1 ? Policy.SIGNATURE_WINDOW : Policy.AUDITED_CHAIN;
        }
    }

    /**
     * Aggressive but bounded observer for exact dictionary-known add-on sources
     * without a matching lifecycle audit.
     *
     * <p>The source is allowed to own the player ComboState for a short overlap
     * window. Once that window is reached, response B may replace the ComboState.
     * This intentionally accepts loss of A's late state-bound callbacks in favor
     * of preserving already spawned entities/effects and making fusion read as an
     * overlap instead of a serial combo.</p>
     *
     * <p>Natural timeout-owned source progression is accepted. Early transitions
     * and same-combo clock restarts are treated as external interruption so the
     * runtime never fires B over unrelated player input.</p>
     */
    public static final class SoftOverlapTracker {
        private final long created;
        private final int overlapTicks;
        private final int deadlineTicks;
        private String combo;
        private long actionTime;
        private int timeoutTicks;

        public SoftOverlapTracker(String combo, int timeoutTicks, long created,
                int overlapTicks, int deadlineTicks) {
            this.combo = Objects.requireNonNull(combo);
            if (timeoutTicks < 1 || overlapTicks < 1 || deadlineTicks < overlapTicks) {
                throw new IllegalArgumentException("Invalid soft-overlap bounds");
            }
            this.timeoutTicks = timeoutTicks;
            this.created = created;
            this.actionTime = created;
            this.overlapTicks = overlapTicks;
            this.deadlineTicks = deadlineTicks;
        }

        public Decision observe(String observedCombo, long committedAt, long now,
                int observedTimeoutTicks) {
            if (committedAt < created || committedAt > now || observedTimeoutTicks < 1) {
                return Decision.INTERRUPTED;
            }

            if (!combo.equals(observedCombo)) {
                // Only a timeout-owned transition is treated as source progression.
                // Earlier state changes are most likely player input or another cast.
                if (committedAt - actionTime < timeoutTicks) {
                    return Decision.INTERRUPTED;
                }
                combo = observedCombo;
                actionTime = committedAt;
                timeoutTicks = observedTimeoutTicks;
            } else if (committedAt != actionTime) {
                // Same ComboState with a fresh clock is a recast/restart.
                return Decision.INTERRUPTED;
            }

            if (now - created >= overlapTicks) {
                return Decision.READY;
            }
            return now - created >= deadlineTicks ? Decision.EXPIRED : Decision.WAIT;
        }

        public String combo() {
            return combo;
        }

        public long due() {
            return created + overlapTicks;
        }
    }

    public static final class Tracker {
        private final Route route;
        private final List<Integer> timeoutTicks;
        private final long created;
        private long actionTime;
        private int stage;
        private boolean observed;

        public Tracker(Route route, List<Integer> timeoutTicks, long created) {
            this.route = route;
            this.timeoutTicks = List.copyOf(timeoutTicks);
            if (timeoutTicks.size() != route.stages().size()) {
                throw new IllegalArgumentException("Timeout count");
            }
            this.created = created;
            this.actionTime = created;
        }

        public Decision observe(String combo, long committedAt, long now) {
            if (committedAt < created || committedAt > now) return Decision.INTERRUPTED;
            Stage current = route.stages().get(stage);
            if (!current.combo().equals(combo)) {
                // Only an audited edge, after its actual registry timeout, is progression.
                // This also handles a short first stage progressing before our first poll.
                if (stage + 1 >= route.stages().size()
                        || !route.stages().get(stage + 1).combo().equals(combo)
                        || committedAt - actionTime < timeoutTicks.get(stage)) {
                    return Decision.INTERRUPTED;
                }
                stage++;
                actionTime = committedAt;
                current = route.stages().get(stage);
            } else if (committedAt != actionTime) {
                // Same ComboState with a new clock is a restart, not continued execution.
                return Decision.INTERRUPTED;
            }
            observed = true;
            if (current.safeAfterTicks() >= 0 && now - actionTime >= current.safeAfterTicks()) {
                return Decision.READY;
            }
            return now - created >= route.deadlineTicks() ? Decision.EXPIRED : Decision.WAIT;
        }

        public boolean observed() {
            return observed;
        }

        public String combo() {
            return route.stages().get(stage).combo();
        }

        public long due() {
            int safe = route.stages().get(stage).safeAfterTicks();
            return safe < 0 ? -1 : actionTime + safe;
        }
    }

    private FusionHandoff() {
    }
}
