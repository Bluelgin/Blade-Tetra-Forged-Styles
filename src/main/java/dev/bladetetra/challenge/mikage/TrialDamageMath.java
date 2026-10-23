package dev.bladetetra.challenge.mikage;

/** Pure damage policy. Safety limits apply after armor, never erase armor's mitigation. */
public final class TrialDamageMath {
    public enum Impact {
        LIGHT(.06, .14), NORMAL(.11, .24), HEAVY(.20, .36), FAILURE(.34, .55);
        public final double targetFraction, maximumFraction;
        Impact(double target, double maximum) { targetFraction = target; maximumFraction = maximum; }
    }
    public static Impact impact(double ratio) {
        return ratio <= .45 ? Impact.LIGHT : ratio <= .95 ? Impact.NORMAL
                : ratio <= 1.30 ? Impact.HEAVY : Impact.FAILURE;
    }
    public static float capFinal(float damage, float maxHealth, Impact impact, double ceiling) {
        if (!Float.isFinite(damage) || damage <= 0 || !Float.isFinite(maxHealth) || maxHealth <= 0) return 0;
        return (float) Math.min(damage, maxHealth * Math.min(impact.maximumFraction, ceiling));
    }
    public static double adapt(double multiplier, double actual, double desired, double maximum) {
        if (!Double.isFinite(actual) || actual < 0 || desired <= 0) return multiplier;
        double next = actual < desired * .5 ? multiplier * 1.22
                : actual > desired * 1.6 ? multiplier * .86
                : multiplier > 1 ? Math.max(1, multiplier * .985) : multiplier;
        return Math.max(.65, Math.min(maximum, next));
    }
    /** Prevent crossing a phase floor, without fabricating damage after a reduced hit. */
    public static float preservePhaseFloor(float actualHealth, float floor) {
        return floor > 0 ? Math.max(actualHealth, floor) : actualHealth;
    }
    public static float limitPhaseDamage(float finalDamage, float health, float floor) {
        return floor > 0 ? Math.min(finalDamage, Math.max(0, health - floor)) : finalDamage;
    }
    private TrialDamageMath() { }
}
