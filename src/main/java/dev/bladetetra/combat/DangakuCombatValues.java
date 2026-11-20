package dev.bladetetra.combat;

/** Shared combat tuning for modular and sealed Dangaku blades. */
final class DangakuCombatValues {
    static final double CLEAVE_RANGE = 6.25D;
    static final double CLEAVE_MIN_DOT = Math.cos(Math.toRadians(45.0D));
    static final double SWEEP_RANGE = 5.25D;
    static final double SWEEP_MIN_DOT = Math.cos(Math.toRadians(110.0D));
    static final int ARMOR_END_TICK = 11;
    static final float ARMOR_DAMAGE = 0.65F;
    static final double HEAVY_DAMAGE = 0.55D;
    static final double SWEEP_DAMAGE = 0.85D;

    static final float HEAVY_SLASH_SIZE = 1.55F;
    static final float SWEEP_SLASH_SIZE = 1.18F;

    private DangakuCombatValues() {}
}
