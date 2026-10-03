package dev.bladetetra.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import java.util.function.BooleanSupplier;

/** Explicitly distinguishes post-hit damage budgets from independent authored secondary attacks. */
public final class InheritedCombatDamage {
    private static final String TAG = "blade_tetra_balance_inherited";
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    public static boolean isInherited(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return DEPTH.get() > 0 || direct != null && direct.getPersistentData().getBoolean(TAG);
    }
    public static void mark(Entity entity) { entity.getPersistentData().putBoolean(TAG, true); }
    public static boolean apply(BooleanSupplier action) {
        int previous = DEPTH.get();
        DEPTH.set(previous + 1);
        try { return action.getAsBoolean(); }
        finally { if (previous == 0) DEPTH.remove(); else DEPTH.set(previous); }
    }
    private InheritedCombatDamage() {}
}
