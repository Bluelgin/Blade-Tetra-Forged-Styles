package dev.bladetetra.forging;

import net.minecraft.util.Mth;

/** Pure balance calculation for a completed named-blade fitting set. */
public record ImprintAffinity(
        double attackBonus,
        double attackRatio,
        int durabilityBonus,
        double durabilityRatio) {
    public static final double MIN_ATTACK_RATIO = 0.10D;
    public static final double MAX_ATTACK_RATIO = 0.30D;
    public static final double MIN_DURABILITY_RATIO = 0.15D;
    public static final double MAX_DURABILITY_RATIO = 0.35D;
    private static final double GAP_INHERITANCE = 0.50D;

    public static ImprintAffinity calculate(
            double modularAttack,
            int modularDurability,
            double sourceAttack,
            int sourceDurability) {
        double safeAttack = Math.max(0.0D, modularAttack);
        int safeDurability = Math.max(2, modularDurability);

        double minimumAttack = safeAttack * MIN_ATTACK_RATIO;
        double maximumAttack = safeAttack * MAX_ATTACK_RATIO;
        double inheritedAttack = Math.max(0.0D, sourceAttack - safeAttack)
                * GAP_INHERITANCE;
        double attackBonus = safeAttack <= 0.0D
                ? 0.0D
                : Mth.clamp(inheritedAttack, minimumAttack, maximumAttack);

        double minimumDurability = safeDurability * MIN_DURABILITY_RATIO;
        double maximumDurability = safeDurability * MAX_DURABILITY_RATIO;
        double inheritedDurability = Math.max(0, sourceDurability - safeDurability)
                * GAP_INHERITANCE;
        int durabilityBonus = (int) Math.round(Mth.clamp(
                inheritedDurability, minimumDurability, maximumDurability));

        return new ImprintAffinity(
                attackBonus,
                safeAttack <= 0.0D ? 0.0D : attackBonus / safeAttack,
                durabilityBonus,
                durabilityBonus / (double) safeDurability);
    }

    public String grade() {
        if (attackRatio >= 0.25D) return "lifelike";
        if (attackRatio >= 0.20D) return "exquisite";
        if (attackRatio >= 0.15D) return "mature";
        return "initial";
    }
}
