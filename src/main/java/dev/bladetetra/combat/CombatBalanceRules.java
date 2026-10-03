package dev.bladetetra.combat;

/** Registry-free arithmetic; classification is separate from final-hit scaling. */
public final class CombatBalanceRules {
    public enum AttackKind { ORDINARY, SLASH_ART, SUMMONED_SWORD }

    public static double multiplier(double global, double style, double attack) {
        return bounded(global) * bounded(style) * bounded(attack);
    }

    private static double bounded(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(10.0D, value)) : 1.0D;
    }

    public static float damage(float original, double multiplier, boolean inherited) {
        if (inherited || original <= 0.0F || multiplier == 1.0D) return original;
        return (float) Math.min(Float.MAX_VALUE, original * multiplier);
    }

    private CombatBalanceRules() {}
}
