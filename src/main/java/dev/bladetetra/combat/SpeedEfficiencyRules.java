package dev.bladetetra.combat;

/** Registry-free timing policy. Speed never changes damage or ordinary combo timelines. */
public final class SpeedEfficiencyRules {
    public static final double DEFAULT_REFERENCE_SPEED = 1.6;
    public static final double MAX_REDUCTION = .40;
    public enum Timing {
        GROUND_DODGE(.20), SPECIAL_SWORDS(.40), NORMAL_SA(1.0 / 3), SUPER_SA(.30);

        public final double maximumReduction;

        Timing(double maximumReduction) {
            this.maximumReduction = maximumReduction;
        }
    }
    public static final int NATIVE_AVOID_RECOVERY = 20;
    public static final int NATIVE_SWORD_PREPARATION = 10;
    public static final int NATIVE_SUPER_PREPARATION = 20;

    private SpeedEfficiencyRules() { }

    /** Linear progress: reference is neutral, twice reference reaches the action-specific cap. */
    public static double reduction(double speed, double reference, Timing timing, double maximum) {
        if (!Double.isFinite(speed) || !Double.isFinite(reference) || !Double.isFinite(maximum)
                || reference <= 0 || speed <= reference) return 0;
        double cap = Math.max(0, Math.min(timing.maximumReduction, maximum));
        return cap * Math.min(1, speed / reference - 1);
    }

    /** Integer ticks, no slow-blade penalty or zero-time skills; action caps are applied before rounding. */
    public static int ticks(int original, double reduction) {
        if (original <= 0) return original;
        double bounded = Double.isFinite(reduction) ? Math.max(0, Math.min(MAX_REDUCTION, reduction)) : 0;
        int saving = Math.min((int) Math.round(original * MAX_REDUCTION),
                (int) Math.round(original * bounded + 1e-9));
        return Math.max(1, original - saving);
    }

    public static long scheduledDelay(String name, long delay, double reduction) {
        boolean sword = NATIVE_SWORD_PREPARATION == delay && switch (name) {
            case "SpiralSwords", "StormSwords", "BlisteringSwords", "HeavyRainSwords" -> true;
            default -> false;
        };
        if (sword || ("chargeSuperSA".equals(name) && delay == NATIVE_SUPER_PREPARATION))
            return ticks((int) delay, reduction);
        if ("sendPartical".equals(name) && delay == 5) {
            // Follow the Super-SA windup, without shortening unrelated 5-tick actions.
            return Math.max(1, Math.round(5.0 * ticks(NATIVE_SUPER_PREPARATION, reduction)
                    / NATIVE_SUPER_PREPARATION));
        }
        return delay;
    }
}
