package dev.bladetetra.combat;

/** Pure balance rules for Iaido spacing, focus and draw damage. */
public final class IaidoBalance {
    public static final int READY_TICKS = 8;
    public static final int DEFLECT_END_TICK = 2;
    public static final int DISRUPTED_TICKS = 12;

    public static final double OPTIMAL_RANGE = 2.0D;
    public static final double CLOSE_RANGE = 1.25D;
    public static final double UNPREPARED_MULTIPLIER = 1.15D;
    public static final double PRESSED_MULTIPLIER = 0.75D;
    public static final double CLOSE_MULTIPLIER = 1.05D;
    public static final double PREPARED_MULTIPLIER = 1.85D;
    public static final double APPROACH_MULTIPLIER = 2.00D;

    private static final float PERFECT_BASE_BONUS = 4.0F;
    private static final float PERFECT_MAX_HEALTH_RATIO = 0.008F;
    private static final float PERFECT_BONUS_CAP = 10.0F;
    private static final double PRIMARY_DRAW_SHARE = 0.65D;
    private static final double SECONDARY_DRAW_SHARE = 0.35D;

    public enum Spacing {
        PRESSED,
        CLOSE,
        OPTIMAL
    }

    public static double drawMultiplier(boolean prepared, boolean approached) {
        if (!prepared) return UNPREPARED_MULTIPLIER;
        return approached ? APPROACH_MULTIPLIER : PREPARED_MULTIPLIER;
    }

    public static Spacing spacing(double horizontalDistance) {
        if (horizontalDistance >= OPTIMAL_RANGE) return Spacing.OPTIMAL;
        if (horizontalDistance >= CLOSE_RANGE) return Spacing.CLOSE;
        return Spacing.PRESSED;
    }

    public static double effectiveMultiplier(double drawMultiplier, Spacing spacing) {
        return switch (spacing) {
            case OPTIMAL -> drawMultiplier;
            case CLOSE -> Math.min(drawMultiplier, CLOSE_MULTIPLIER);
            case PRESSED -> PRESSED_MULTIPLIER;
        };
    }

    /**
     * Native Combo C emits two slash effects. They are two visual beats of one
     * draw, so together they may spend only one draw's damage budget.
     */
    public static double drawSlashShare(int slashIndex) {
        return switch (slashIndex) {
            case 0 -> PRIMARY_DRAW_SHARE;
            case 1 -> SECONDARY_DRAW_SHARE;
            default -> 0.0D;
        };
    }

    public static float perfectBonus(float targetMaxHealth) {
        float scaled = PERFECT_BASE_BONUS
                + Math.max(0.0F, targetMaxHealth) * PERFECT_MAX_HEALTH_RATIO;
        return Math.min(PERFECT_BONUS_CAP, scaled);
    }

    private IaidoBalance() {
    }
}
