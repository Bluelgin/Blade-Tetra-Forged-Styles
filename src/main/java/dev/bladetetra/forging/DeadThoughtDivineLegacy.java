package dev.bladetetra.forging;

import net.minecraft.world.item.ItemStack;

/**
 * Persistent marker for the Mikage/Divine-Domain route to Dead Thought.
 *
 * <p>The SJAP fitting convergence and this postgame route intentionally converge on
 * the same {@link LegacyFusion#NIHILUL_SAYA_CRIMSON_CHERRY_HILT} combat identity.
 * The marker records how the blade learned that identity; it does not duplicate SA/SE
 * registrations or pretend that SJAP fittings are installed.</p>
 */
public final class DeadThoughtDivineLegacy {
    public static final String BOUND_TAG = "blade_tetra_dead_thought_divine_bound";

    public static boolean isBound(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag()
                && stack.getTag().getBoolean(BOUND_TAG);
    }

    public static void bind(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(BOUND_TAG, true);
    }

    private DeadThoughtDivineLegacy() {
    }
}
