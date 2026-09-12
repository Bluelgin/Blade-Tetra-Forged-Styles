package dev.bladetetra.combat;

/** Pure balance helpers for the Muramasa saya + Doutanuki hilt fusion. */
final class DouwariBalance {
    static final double SOFT_CAP_START = 24.0D;
    static final double SOFT_CAP_RATE = 0.40D;
    static final double EFFECTIVE_ATTACK_CAP = 64.0D;
    static final double ARMOR_PRESSURE_CAP = 30.0D;
    static final double TOUGHNESS_WEIGHT = 0.50D;
    static final double PRESSURE_RATE = 0.0225D;
    static final double MAX_DAMAGE = 56.0D;

    static double effectiveAttack(double attack) {
        double safe = Math.max(0.0D, attack);
        double effective = safe <= SOFT_CAP_START
                ? safe
                : SOFT_CAP_START + (safe - SOFT_CAP_START) * SOFT_CAP_RATE;
        return Math.min(EFFECTIVE_ATTACK_CAP, effective);
    }

    static double armorPressure(double armor, double toughness) {
        return Math.min(ARMOR_PRESSURE_CAP,
                Math.max(0.0D, armor) + Math.max(0.0D, toughness) * TOUGHNESS_WEIGHT);
    }

    static float damage(double attack, double armor, double toughness) {
        double effective = effectiveAttack(attack);
        if (effective <= 0.0D) {
            return 0.0F;
        }
        double pressure = armorPressure(armor, toughness);
        double multiplier = 1.0D + pressure * PRESSURE_RATE;

        // The ceiling grows with the release-time attack snapshot, then stops.
        // This keeps Douwari useful against armor without restoring linear
        // high-stat-pack scaling through the armor bonus itself.
        double dynamicCap = Math.min(MAX_DAMAGE, effective * 0.90D + 14.0D);
        return (float) Math.max(1.0D,
                Math.min(dynamicCap, effective * multiplier));
    }

    private DouwariBalance() {
    }
}
