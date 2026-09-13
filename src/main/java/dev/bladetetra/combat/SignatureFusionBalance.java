package dev.bladetetra.combat;

/** Pure balance helpers for the custom-damage stages in the SA/SE fusion batch. */
final class SignatureFusionBalance {
    private static final double SOFT_START = 24.0D;
    private static final double SOFT_RATE = 0.40D;
    private static final double EFFECTIVE_ATTACK_CAP = 64.0D;

    static double effectiveAttack(double rawAttack) {
        if (!Double.isFinite(rawAttack) || rawAttack <= 0.0D) {
            return 0.0D;
        }
        double effective = rawAttack <= SOFT_START
                ? rawAttack
                : SOFT_START + (rawAttack - SOFT_START) * SOFT_RATE;
        return Math.min(EFFECTIVE_ATTACK_CAP, effective);
    }

    static float piercingHit(double rawAttack) {
        return scaled(rawAttack, 0.24D, 1.25D, 9.0D);
    }

    static float voidClosure(double rawAttack) {
        return scaled(rawAttack, 0.30D, 1.5D, 11.5D);
    }

    private static float scaled(double rawAttack, double scale,
            double floor, double cap) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(cap, Math.max(floor, effective * scale));
    }

    private SignatureFusionBalance() {
    }
}
