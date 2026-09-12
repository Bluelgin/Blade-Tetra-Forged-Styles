package dev.bladetetra.combat;

/** Pure first-pass balance helpers for Void Scattering. */
final class VoidScatteringBalance {
    static final float DOMAIN_DAMAGE_MULTIPLIER = 0.45F;

    private static final double SOFT_START = 24.0D;
    private static final double SOFT_RATE = 0.40D;
    private static final double EFFECTIVE_ATTACK_CAP = 64.0D;
    private static final double RETURN_SCALE = 0.22D;
    private static final double RETURN_CAP = 10.0D;
    private static final double RESIDUAL_SCALE = 0.15D;
    private static final double RESIDUAL_CAP = 7.0D;

    static double effectiveAttack(double rawAttack) {
        if (!Double.isFinite(rawAttack) || rawAttack <= 0.0D) {
            return 0.0D;
        }
        double effective = rawAttack <= SOFT_START
                ? rawAttack
                : SOFT_START + (rawAttack - SOFT_START) * SOFT_RATE;
        return Math.min(EFFECTIVE_ATTACK_CAP, effective);
    }

    static float returnSwordDamage(double rawAttack) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(RETURN_CAP, Math.max(1.0D, effective * RETURN_SCALE));
    }

    static float residualSwordDamage(double rawAttack) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(RESIDUAL_CAP, Math.max(0.5D, effective * RESIDUAL_SCALE));
    }

    static float reducedDamage(float incoming) {
        if (!Float.isFinite(incoming) || incoming <= 0.0F) {
            return 0.0F;
        }
        return incoming * DOMAIN_DAMAGE_MULTIPLIER;
    }

    private VoidScatteringBalance() {
    }
}
