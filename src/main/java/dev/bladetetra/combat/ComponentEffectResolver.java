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

    /**
     * Returns the active Tetra variant for a slot. Tetra stores material/variant
     * identity under the selected module id plus {@code _material}, rather than
     * under the slot id itself.
     */
    public static String moduleVariant(ItemStack stack, String slot) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(slot, Tag.TAG_STRING)) {
            return "";
        }
        String module = tag.getString(slot);
        if (module.isBlank()) {
            return "";
        }
        String variantKey = module + "_material";
        return tag.contains(variantKey, Tag.TAG_STRING)
                ? tag.getString(variantKey)
                : "";
    }

    private ComponentEffectResolver() {
    }
}
