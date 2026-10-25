package dev.bladetetra.easteregg;

/** Pure deterministic numbers for four awakened souls; no game or client state. */
public final class AwakenedCombatMath {
    public static int scarSector(double deltaX, double deltaZ) {
        double angle = Math.atan2(deltaZ, deltaX);
        return Math.floorMod((int) Math.floor((angle + Math.PI) * 3.0D / Math.PI), 6);
    }

    public static float causalCut(float actualDamage, int directions) {
        if (directions < 3) return 0;
        return bounded(actualDamage, 32.0F, 0.6F + Math.min(6, directions) * 0.26F, 30.0F);
    }

    public static float mirrorCut(float actualDamage) {
        return bounded(actualDamage, 32.0F, 0.32F, 12.0F);
    }

    public static float petalBudget(float actualDamage, int charge) {
        if (charge < 3) return 0;
        return bounded(actualDamage, 32.0F, 0.35F + Math.min(6, charge) * 0.23F, 26.0F);
    }

    public static float thunderFinisher(float actualDamage, int charge) {
        if (charge < 2) return 0;
        return bounded(actualDamage, 32.0F, 0.35F + Math.min(4, charge) * 0.18F, 20.0F);
    }

    private static float bounded(float actualDamage, float inputCap,
            float multiplier, float cap) {
        if (!Float.isFinite(actualDamage) || actualDamage <= 0) return 0;
        return Math.max(0, Math.min(cap, Math.min(actualDamage, inputCap) * multiplier));
    }

    private AwakenedCombatMath() { }
}
