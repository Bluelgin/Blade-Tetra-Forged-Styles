package dev.bladetetra.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Reads functional component choices from the same Tetra slot tags that drive
 * crafting and stats.
 */
public final class ComponentEffectResolver {
    public static boolean hasModule(ItemStack stack, String slot, String module) {
        CompoundTag tag = stack.getTag();
        return tag != null
                && tag.contains(slot, Tag.TAG_STRING)
                && module.equals(tag.getString(slot));
    }

    private ComponentEffectResolver() {
    }
}
