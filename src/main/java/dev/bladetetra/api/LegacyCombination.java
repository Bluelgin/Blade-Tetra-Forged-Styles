package dev.bladetetra.api;

import dev.bladetetra.forging.FoxLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Extension point for future authored effects produced by named-blade part
 * combinations. Registering nothing leaves mixed parts purely visual.
 */
public interface LegacyCombination {
    ResourceLocation id();

    int priority();

    boolean matches(ItemStack stack, FoxLegacyParts parts);
}
