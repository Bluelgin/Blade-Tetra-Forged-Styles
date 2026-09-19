package dev.bladetetra.combat;

/** Shared charge/range math for Dangaku's held right-click sweep. */
public final class DangakuChargeMath {
    public static final int FULL_CHARGE_TICKS = 44;
    public static final int PEAK_GRACE_TICKS = 5;
    public static final double MIN_DECAYED_CHARGE = 0.25D;

    static final double REFERENCE_PANEL_DAMAGE = 24.0D;
    static final double PANEL_POWER_MIN = 0.75D;
    static final double PANEL_POWER_MAX = 1.50D;
    static final double DECAY_PER_TICK = 1.0D / 60.0D;
    static final double CHARGED_RANGE_BASE = 8.50D;
    static final double CHARGED_RANGE_MAX = 12.0D;
    static final double PARTIAL_RANGE_FLOOR = 5.50D;
    static final float DAMAGE_RATIO_FLOOR = 0.42F;
    static final float DAMAGE_RATIO_BASE = 0.58F;
    static final float DAMAGE_RATIO_MAX = 0.68F;

    public static double chargeForHeldTicks(long heldTicks) {
        if (heldTicks <= 0L) {
            return 0.0D;
        }
        if (heldTicks < FULL_CHARGE_TICKS) {
            return heldTicks / (double) FULL_CHARGE_TICKS;
        }
        long decayTicks = heldTicks - FULL_CHARGE_TICKS - PEAK_GRACE_TICKS;
        if (decayTicks <= 0L) {
            return 1.0D;
        }
        return Math.max(
                MIN_DECAYED_CHARGE,
                1.0D - decayTicks * DECAY_PER_TICK);
    }

    public static boolean isPeakWindow(long heldTicks) {
        return heldTicks >= FULL_CHARGE_TICKS
                && heldTicks <= FULL_CHARGE_TICKS + PEAK_GRACE_TICKS;
    }

    public static boolean isOvercharged(long heldTicks) {
        return heldTicks > FULL_CHARGE_TICKS + PEAK_GRACE_TICKS;
    }

    public static double sweepRange(double panelDamage, double charge) {
        double fullRange = lerp(
                CHARGED_RANGE_BASE,
                CHARGED_RANGE_MAX,
                panelScale(panelDamage));
        return lerp(
                PARTIAL_RANGE_FLOOR,
                fullRange,
                clamp01(charge));
    }

    public static float sweepDamageRatio(double panelDamage, double charge) {
        double fullRatio = lerp(
                DAMAGE_RATIO_BASE,
                DAMAGE_RATIO_MAX,
                panelScale(panelDamage));
        return (float) lerp(
                DAMAGE_RATIO_FLOOR,
                fullRatio,
                clamp01(charge));
    }

    public static double focusDistance(double range) {
        return 2.60D + Math.min(1.20D, Math.max(0.0D, range - PARTIAL_RANGE_FLOOR) * 0.18D);
    }

    public static float visualSize(double range) {
        return (float) (1.45D + Math.max(0.0D, range - PARTIAL_RANGE_FLOOR) * 0.13D);
    }

    static double panelScale(double panelDamage) {
        double panelPower = Math.sqrt(Math.max(1.0D, panelDamage) / REFERENCE_PANEL_DAMAGE);
        panelPower = Math.max(PANEL_POWER_MIN, Math.min(PANEL_POWER_MAX, panelPower));
        return (panelPower - PANEL_POWER_MIN) / (PANEL_POWER_MAX - PANEL_POWER_MIN);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double lerp(double min, double max, double value) {
        return min + (max - min) * value;
    }

    private DangakuChargeMath() {
    }
}
