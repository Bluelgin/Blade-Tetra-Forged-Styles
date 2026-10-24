package dev.bladetetra.client;

/** Pure, client-only detail decisions. No decision here can change projectile physics or damage. */
public final class SwordAuraRenderPolicy {
    public static boolean allowDecoration(double distanceSquared, int quality, double distance,
            int budget, int used) {
        if (quality <= 0 || budget <= 0 || !Double.isFinite(distanceSquared) || distanceSquared < 0
                || !Double.isFinite(distance) || distance <= 0) return false;
        if (quality == 1) {
            distance *= 0.5D;
            budget = Math.max(1, budget / 2);
        }
        return used < budget && distanceSquared <= distance * distance;
    }

    /** Match native Drive's tick-based fade, including its fully transparent tail. */
    public static int alpha(float lifetime, int age) {
        if (!Float.isFinite(lifetime) || lifetime <= 0 || age < 0) return 0;
        double remaining = Math.min(lifetime, Math.max(0.0D, lifetime - age)) / lifetime;
        double fade = remaining - 1.0D;
        double squared = fade * fade;
        return (int) (255 * Math.max(0.0D, 0.75D - squared * squared));
    }

    private SwordAuraRenderPolicy() {}
}
