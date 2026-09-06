package dev.bladetetra.easteregg;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Marks legacy damage so awakened effects cannot recursively trigger each other. */
public final class SoulLegacyDamageGuard {
    private static final String SECONDARY = "blade_tetra_soul_legacy_secondary";
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static boolean isSecondary(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return DEPTH.get() > 0
                || direct != null && direct.getPersistentData().getBoolean(SECONDARY);
    }

    public static void markSecondary(Entity entity) {
        entity.getPersistentData().putBoolean(SECONDARY, true);
    }

    public static boolean apply(java.util.function.BooleanSupplier damageAction) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            return damageAction.getAsBoolean();
        } finally {
            int remaining = DEPTH.get() - 1;
            if (remaining <= 0) DEPTH.remove();
            else DEPTH.set(remaining);
        }
    }

    private SoulLegacyDamageGuard() {
    }
}
