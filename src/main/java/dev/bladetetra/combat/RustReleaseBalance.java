package dev.bladetetra.combat;

import net.minecraft.world.damagesource.CombatRules;

/** Pure damage rules for the Agito lineage's normal-combo tempering effect. */
final class RustReleaseBalance {
    static final int OPENING = 1;
    static final int MIDDLE = 2;
    static final int LATE = 3;
    static final int FINISHER = 4;

    static double damageMultiplier(int phase) {
        return switch (phase) {
            case LATE -> 1.10D;
            case FINISHER -> 1.25D;
            default -> 1.0D;
        };
    }

    static double armorPenetration(int phase) {
        return switch (phase) {
            case MIDDLE -> 0.12D;
            case LATE -> 0.22D;
            case FINISHER -> 0.30D;
            default -> 0.0D;
        };
    }

    /**
     * Raises the incoming amount just enough that vanilla armor produces roughly
     * the result of the reduced armor value. This keeps one ordinary damage event
     * instead of adding a second iframe-bypassing hit.
     */
    static float adjustedIncomingDamage(float amount, double armor,
            double toughness, int phase) {
        float scaled = (float) Math.max(0.0D, amount * damageMultiplier(phase));
        double penetration = armorPenetration(phase);
        if (scaled <= 0.0F || armor <= 0.0D || penetration <= 0.0D) return scaled;
        float normalResult = CombatRules.getDamageAfterAbsorb(
                scaled, (float) armor, (float) toughness);
        float piercedResult = CombatRules.getDamageAfterAbsorb(
                scaled, (float) (armor * (1.0D - penetration)), (float) toughness);
        if (normalResult <= 1.0E-4F) return scaled;
        return scaled * Math.min(2.0F, piercedResult / normalResult);
    }

    static float edgeDamage(double attack) {
        return (float) Math.max(0.5D,
                Math.min(18.0D, Math.max(0.0D, attack) * 0.55D));
    }

    private RustReleaseBalance() {
    }
}
