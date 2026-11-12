package dev.bladetetra.combat;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.customstyle.CustomBladeLength;
import dev.bladetetra.customstyle.CustomStyleSpec;
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

        if (CustomBladeLength.isCustomModule(tag.getString(ModularSlashBladeItem.BLADE_SLOT))) {
            return CustomStyleSpec.decode(tag.getCompound(CustomStyleSpec.STACK_KEY)) != null
                    ? BladeStyle.CUSTOM : BladeStyle.STANDARD;
        }
        return switch (tag.getString(ModularSlashBladeItem.BLADE_SLOT)) {
            case ModularSlashBladeItem.ORTHODOX_BLADE_MODULE, SaltyFishParts.STANDARD_BLADE -> BladeStyle.STANDARD;
            case ModularSlashBladeItem.BLADE_MODULE, SaltyFishParts.IAIDO_BLADE -> BladeStyle.IAIDO;
            case ModularSlashBladeItem.WAKIZASHI_MODULE, SaltyFishParts.SHORT_BLADE -> BladeStyle.RENGEKI;
            case ModularSlashBladeItem.NODACHI_MODULE, SaltyFishParts.LONG_BLADE -> BladeStyle.DANGAKU;
            default -> BladeStyle.STANDARD;
        };
    }

    private StyleResolver() {
    }
}
