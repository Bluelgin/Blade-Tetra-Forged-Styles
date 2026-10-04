package dev.bladetetra.combat;

/** Pure, tick-based input rules. Animation frames belong only to the adapter. */
public final class StyleBranchRules {
    public enum Phase {
        R_FIRST(3, 10), R_SECOND(3, 12), R_CHASE(11, 14), R_FLURRY(16, 22),
        R_FINISH(8, 12), R_RECOVERY(6, 8), R_UPPER(8, 13), R_JUMP(2, 8),
        R_AIR_FIRST(3, 10), R_AIR_SECOND(3, 12), R_AIR_FLURRY(16, 22), R_AIR_FINISH(8, 12), R_AIR_RECOVERY(6, 8),
        R_AIR_RISE(8, 13), R_AIR_DROP(10, 13), R_DIVE(3, 60), R_LAND(5, 10),
        D_SWEEP(4, 11), D_RETURN(4, 13), D_FINISH(14, 18), D_HEAVY(19, 24),
        D_RECOVERY(7, 9), D_CIRCLE_RECOVERY(7, 9), D_UPPER(8, 13), D_JUMP(2, 8),
        D_AIR_FIRST(4, 11), D_AIR_SECOND(4, 13), D_AIR_FINISH(14, 18), D_AIR_HEAVY(19, 24), D_AIR_RECOVERY(7, 9),
        D_AIR_CIRCLE_RECOVERY(7, 9),
        D_DIVE(3, 60), D_LAND(6, 13);

        private final int minimumTick;
        private final int duration;
        Phase(int minimumTick, int duration) {
            this.minimumTick = minimumTick;
            this.duration = duration;
        }
        public int minimumTick() { return minimumTick; }
        public int duration() { return duration; }
        public int commonMinimumTick() {
            return switch (this) {
                case R_FLURRY, R_AIR_FLURRY -> 9;
                case D_HEAVY, D_AIR_HEAVY -> 16;
                case R_RECOVERY, D_RECOVERY, D_CIRCLE_RECOVERY, R_AIR_RECOVERY,
                        D_AIR_RECOVERY, D_AIR_CIRCLE_RECOVERY -> 0;
                default -> minimumTick;
            };
        }
        public boolean airAttack() { return name().contains("_AIR_"); }
        public boolean rengeki() { return name().startsWith("R_"); }
        public boolean aerial() { return name().contains("AIR") || name().endsWith("DIVE") || name().endsWith("JUMP"); }
        public boolean dive() { return this == R_DIVE || this == D_DIVE; }
        public boolean recovery() { return name().endsWith("_RECOVERY"); }
    }

    public enum Intent { NONE, ATTACK, FORWARD, BACK }
    public static final int HEAVY_PAUSE_TICK = 8;
    public enum CommonMove { NONE, RAPID, UPPER, CLEAVE }
    public static CommonMove commonMove(Intent intent, boolean grounded, boolean rightClick) {
        if (!rightClick) return CommonMove.NONE;
        if (intent == Intent.BACK) return grounded ? CommonMove.UPPER : CommonMove.CLEAVE;
        return grounded && intent == Intent.FORWARD ? CommonMove.RAPID : CommonMove.NONE;
    }

    /** null means neutral. Returning the current phase locks root overrides too. */
    public static Phase next(Phase phase, long elapsed, Intent intent, boolean grounded) {
        if (grounded && phase.airAttack()) return groundedRecovery(phase);
        if (elapsed < phase.minimumTick()) return phase;
        if (intent == Intent.NONE) return null; // Native long-held SA remains available.
        if (phase.dive()) return phase; // Landing, timeout or native SA owns the exit.
        if (phase.recovery()) return null;
        if (grounded && phase.aerial()) return phase.rengeki() ? Phase.R_RECOVERY : Phase.D_RECOVERY;
        if (!grounded && !phase.aerial() && phase != Phase.R_UPPER && phase != Phase.D_UPPER
                && phase != Phase.R_LAND && phase != Phase.D_LAND) return null;
        return switch (phase) {
            case R_FIRST, R_SECOND -> phase == Phase.R_FIRST ? Phase.R_SECOND : Phase.R_FLURRY;
            case R_CHASE -> Phase.R_FLURRY;
            case R_FLURRY -> Phase.R_FINISH;
            case R_UPPER, D_UPPER -> phase.rengeki() ? Phase.R_JUMP : Phase.D_JUMP;
            case R_JUMP -> intent == Intent.BACK ? Phase.R_DIVE : Phase.R_AIR_FIRST;
            case D_JUMP -> intent == Intent.BACK ? Phase.D_DIVE : Phase.D_AIR_FIRST;
            case R_AIR_FIRST -> Phase.R_AIR_SECOND;
            case R_AIR_SECOND -> Phase.R_AIR_FLURRY;
            case R_AIR_FLURRY -> Phase.R_AIR_FINISH;
            case R_AIR_FINISH -> Phase.R_AIR_RECOVERY;
            case R_AIR_RISE -> Phase.R_AIR_DROP;
            case D_SWEEP -> Phase.D_RETURN;
            case D_RETURN -> elapsed >= HEAVY_PAUSE_TICK ? Phase.D_HEAVY : Phase.D_FINISH;
            case D_AIR_FIRST -> Phase.D_AIR_SECOND;
            case D_AIR_SECOND -> elapsed >= HEAVY_PAUSE_TICK ? Phase.D_AIR_HEAVY : Phase.D_AIR_FINISH;
            case D_FINISH -> Phase.D_CIRCLE_RECOVERY;
            case D_AIR_FINISH -> Phase.D_AIR_CIRCLE_RECOVERY;
            case D_AIR_HEAVY -> Phase.D_AIR_RECOVERY;
            case R_LAND -> grounded ? Phase.R_FIRST : Phase.R_RECOVERY;
            case D_LAND -> grounded ? Phase.D_SWEEP : Phase.D_RECOVERY;
            default -> phase.rengeki() ? Phase.R_RECOVERY : Phase.D_RECOVERY;
        };
    }

    public static Phase timeout(Phase phase) {
        if (phase.recovery()) return null;
        if (phase == Phase.D_AIR_FINISH) return Phase.D_AIR_CIRCLE_RECOVERY;
        if (phase.airAttack()) return phase.rengeki() ? Phase.R_AIR_RECOVERY : Phase.D_AIR_RECOVERY;
        return groundedRecovery(phase);
    }

    public static Phase groundedRecovery(Phase phase) {
        if (DangakuCircleSlashRules.matches(phase) || phase == Phase.D_AIR_CIRCLE_RECOVERY
                || phase == Phase.D_CIRCLE_RECOVERY) return Phase.D_CIRCLE_RECOVERY;
        return phase.rengeki() ? Phase.R_RECOVERY : Phase.D_RECOVERY;
    }

    private StyleBranchRules() {}
}
