package dev.bladetetra.combat;

/** Pure balance helpers for Void Scattering. */
final class VoidScatteringBalance {
    static final float DOMAIN_DAMAGE_MULTIPLIER = 0.45F;
    static final float RESIDUAL_DAMAGE_MULTIPLIER = 0.60F;

    private static final double SOFT_START = 24.0D;
    private static final double SOFT_RATE = 0.40D;
    private static final double EFFECTIVE_ATTACK_CAP = 64.0D;

    private static final double RETURN_SCALE = 0.24D;
    private static final double RETURN_CHARGE_SCALE = 0.15D;
    private static final double RETURN_CAP = 12.0D;
    private static final double SHATTER_RETURN_MULTIPLIER = 1.15D;

    private static final double RESIDUAL_COUNTER_SCALE = 0.17D;
    private static final double RESIDUAL_COUNTER_CAP = 8.0D;
    private static final double RESIDUAL_FALLBACK_SCALE = 0.13D;
    private static final double RESIDUAL_FALLBACK_CAP = 6.0D;

    static double effectiveAttack(double rawAttack) {
        if (!Double.isFinite(rawAttack) || rawAttack <= 0.0D) {
            return 0.0D;
        }
        double effective = rawAttack <= SOFT_START
                ? rawAttack
                : SOFT_START + (rawAttack - SOFT_START) * SOFT_RATE;
        return Math.min(EFFECTIVE_ATTACK_CAP, effective);
    }

    static float returnSwordDamage(double rawAttack, int voidCharge, boolean shattered) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        int boundedCharge = Math.max(0, Math.min(12, voidCharge));
        double base = Math.min(RETURN_CAP,
                Math.max(1.0D, effective * RETURN_SCALE
                        + boundedCharge * RETURN_CHARGE_SCALE));
        if (shattered) {
            base *= SHATTER_RETURN_MULTIPLIER;
        }
        return (float) base;
    }

    static float residualCounterSwordDamage(double rawAttack) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(RESIDUAL_COUNTER_CAP,
                Math.max(1.0D, effective * RESIDUAL_COUNTER_SCALE));
    }

    static float residualFallbackSwordDamage(double rawAttack) {
        double effective = effectiveAttack(rawAttack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.min(RESIDUAL_FALLBACK_CAP,
                Math.max(0.75D, effective * RESIDUAL_FALLBACK_SCALE));
    }

    static float reducedDamage(float incoming) {
        if (!Float.isFinite(incoming) || incoming <= 0.0F) {
            return 0.0F;
        }
        return incoming * DOMAIN_DAMAGE_MULTIPLIER;
    }

    static float residualReducedDamage(float incoming) {
        if (!Float.isFinite(incoming) || incoming <= 0.0F) {
            return 0.0F;
        }
        return incoming * RESIDUAL_DAMAGE_MULTIPLIER;
    }

    static int voidChargeForIncomingDamage(float incoming) {
        if (!Float.isFinite(incoming) || incoming <= 0.0F) {
            return 0;
        }
        if (incoming <= 4.0F) {
            return 1;
        }
        if (incoming <= 10.0F) {
            return 2;
        }
        if (incoming <= 20.0F) {
            return 3;
        }
        return 4;
    }

    private VoidScatteringBalance() {
    }
}
