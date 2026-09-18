package dev.bladetetra.challenge;

/** Geometry and finite budgets shared by server logic and regression tests. */
public final class DivineSupportRules {
    public static boolean insideArray(double dx, double dz) {
        // Equilateral triangle with corners (0,-5), (-4.33,2.5), (4.33,2.5).
        return dz >= -5 && dz <= 2.5 && Math.abs(dx) <= (dz + 5) / Math.sqrt(3);
    }
    public static int phase(float health, float max) {
        if (max <= 0) return 0;
        float ratio = health / max;
        return ratio <= .2F ? 3 : ratio <= .4F ? 2 : ratio <= .7F ? 1 : 0;
    }
    public static float companionDamage(float health) { return Math.min(3, Math.max(0, health - 1)); }
    public static float playerWindowMultiplier(boolean markedOrBound, boolean eliteInArray) {
        return markedOrBound ? 1.2F : eliteInArray ? 1.1F : 1;
    }
    private DivineSupportRules() {}
}
