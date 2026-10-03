package dev.bladetetra.combat;

/** Shared by combat and component descriptions; changing a value changes both. */
public final class ComponentCombatValues {
    public static final double DRAW_BONUS = 0.15;
    public static final double SPIRIT_BONUS = 0.15;
    public static final int JUST_EXTRA_TICKS = 2;
    public static final int SPIRIT_TICKS = 40;
    public static final float GUARD_REDUCTION = 0.25F;
    public static final int GUARD_DURABILITY_COST = 1;
    public static final int RANK_UNIT_DIVISOR = 50;

    public static long rankGain(long unitCapacity) {
        return Math.max(1L, unitCapacity / RANK_UNIT_DIVISOR);
    }

    private ComponentCombatValues() { }
}
