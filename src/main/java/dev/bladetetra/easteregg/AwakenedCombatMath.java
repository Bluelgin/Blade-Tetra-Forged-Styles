package dev.bladetetra.easteregg;

/** Pure deterministic numbers for four awakened souls; no game or client state. */
public final class AwakenedCombatMath {
    public static int scarSector(double deltaX, double deltaZ) {
        double angle = Math.atan2(deltaZ, deltaX);
        return Math.floorMod((int) Math.floor((angle + Math.PI) * 3.0D / Math.PI), 6);
    }

    /**
     * A real crossing requires distinct attack bearings rather than three
     * adjacent wedges obtained by walking a quarter-circle.
     * This is a pure geometry gate: each of the six bits is a 60-degree sector.
     */
    public static boolean hasCausalCross(int mask) {
        if (Integer.bitCount(mask & 0b111111) < 3) return false;
        for (int i = 0; i < 6; i++) {
            if ((mask & (1 << i)) == 0) continue;
            for (int j = i + 1; j < 6; j++) {
                int gap = j - i;
                if ((mask & (1 << j)) != 0 && gap >= 2 && gap <= 4) return true;
            }
        }
        return false;
    }

    /** A confirmed hit, not dash button-spam, authorizes the next Raikiri step. */
    public static int nextThunderChain(int current, boolean confirmed,
            long lastStep, long now) {
        if (current <= 0 || !confirmed || now < lastStep
                || now - lastStep > 36) return 1;
        return Math.min(3, current + 1);
    }

    /** One committed event chooses the same formation on server and clients. */
    public static int sakuraFormation(boolean airborne, boolean sprinting) {
        return airborne ? 2 : sprinting ? 1 : 0;
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
