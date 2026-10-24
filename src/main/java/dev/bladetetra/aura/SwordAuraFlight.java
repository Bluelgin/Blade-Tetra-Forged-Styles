package dev.bladetetra.aura;

/** Shared flight budget; extending range must not leave slower styles with a short lifetime. */
public final class SwordAuraFlight {
    public static final double RANGE_BLOCKS = 16.0D;
    public static final double RANGE_SQUARED = RANGE_BLOCKS * RANGE_BLOCKS;
    public static int lifetime(float speed) {
        if (!Float.isFinite(speed) || speed <= 0) throw new IllegalArgumentException("Positive finite speed required");
        return (int) Math.ceil(RANGE_BLOCKS / speed) + 4;
    }
    private SwordAuraFlight() { }
}
