package dev.bladetetra.combat;

/** Circle Slash is an ordinary finisher, not four full-strength slash arts. */
final class DangakuCircleSlashRules {
    static final int SEGMENTS = 4;
    // Preserve the former A4 finisher's two 0.44 hits, including its sweep tuning.
    static final double FINISH_DAMAGE_BUDGET = 2 * .44D * .85D;
    private static final double NATIVE_SEGMENT_DAMAGE = .325D;
    // EntitySlashEffect applies this additional ratio to native critical slashes.
    private static final float NATIVE_CRITICAL_FACTOR = 1.1F;

    static boolean matches(StyleBranchRules.Phase phase) {
        return phase == StyleBranchRules.Phase.D_FINISH
                || phase == StyleBranchRules.Phase.D_AIR_FINISH;
    }

    static double damageScale(boolean critical) {
        return FINISH_DAMAGE_BUDGET / (SEGMENTS * NATIVE_SEGMENT_DAMAGE
                * (critical ? NATIVE_CRITICAL_FACTOR : 1.0D));
    }

    private DangakuCircleSlashRules() {}
}
