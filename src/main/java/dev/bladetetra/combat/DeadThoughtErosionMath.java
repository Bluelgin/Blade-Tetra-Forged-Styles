package dev.bladetetra.combat;

/**
 * Pure math for Dead Thought's intentionally unbounded soul erosion.
 * Erosion may exceed 100%; soulIntegrity therefore intentionally becomes negative.
 */
final class DeadThoughtErosionMath {
    static double add(double current, double added) {
        double safeCurrent = Double.isFinite(current) ? Math.max(0.0D, current) : 0.0D;
        double safeAdded = Double.isFinite(added) ? Math.max(0.0D, added) : 0.0D;
        return safeCurrent + safeAdded;
    }

    static double soulIntegrity(double erosion) {
        double safe = Double.isFinite(erosion) ? Math.max(0.0D, erosion) : 0.0D;
        return 1.0D - safe;
    }

    static boolean isSoulBroken(double erosion) {
        return soulIntegrity(erosion) <= 0.0D;
    }

    static double remainingFraction(double erosion) {
        return Math.max(0.0D, soulIntegrity(erosion));
    }

    static float effectiveCap(float actualMaxHealth, double erosion, float engineFloor) {
        float safeMax = Math.max(0.0F, actualMaxHealth);
        float floor = Math.min(Math.max(0.0F, engineFloor), safeMax);
        return (float) Math.max(floor, safeMax * remainingFraction(erosion));
    }

    private DeadThoughtErosionMath() {
    }
}
