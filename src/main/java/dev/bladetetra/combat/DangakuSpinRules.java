package dev.bladetetra.combat;

/** Ordinary sustained attack budget; never an SA, damage shield or aerial hover. */
final class DangakuSpinRules {
    static final int HOLD_TICKS = 6;
    // Native Circle Slash spans 18 animation frames at 30 fps: 12 game ticks, no idle tail.
    static final int CIRCLE_TICKS = 12;
    static final int RECOVERY_TICKS = 9;
    static final double MOVEMENT_PENALTY = -.25D;
    static final double DAMAGE_SHARE = .4D;
    static final double DAMAGE_BUDGET = DangakuCircleSlashRules.FINISH_DAMAGE_BUDGET * DAMAGE_SHARE;
    static double damageScale(boolean critical) {
        return DangakuCircleSlashRules.damageScale(critical) * DAMAGE_SHARE;
    }

    static boolean canRepeat(boolean held, boolean grounded, boolean busy, boolean flying, boolean riding) {
        return held && grounded && !busy && !flying && !riding;
    }
    private DangakuSpinRules() {}
}
