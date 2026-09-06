package dev.bladetetra.combat;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves combat style directly from module data, keeping Tetra as the single
 * source of truth instead of storing another style NBT value.
 */
public final class StyleResolver {
    public static BladeStyle resolve(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ModularSlashBladeItem.BLADE_SLOT, Tag.TAG_STRING)) {
            return BladeStyle.STANDARD;
        }

        return switch (tag.getString(ModularSlashBladeItem.BLADE_SLOT)) {
            case ModularSlashBladeItem.ORTHODOX_BLADE_MODULE -> BladeStyle.STANDARD;
            case ModularSlashBladeItem.BLADE_MODULE -> BladeStyle.IAIDO;
            case ModularSlashBladeItem.WAKIZASHI_MODULE -> BladeStyle.RENGEKI;
            case ModularSlashBladeItem.NODACHI_MODULE -> BladeStyle.DANGAKU;
            default -> BladeStyle.STANDARD;
        };
    }

    private StyleResolver() {
    }
}
