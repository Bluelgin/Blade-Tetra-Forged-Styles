package dev.bladetetra.forging;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Compatibility view: a historical fox guard now represents its complete hilt. */
public record FoxLegacyParts(Color blade, Color saya, Color tsuba, Color tsuka) {
    public enum Color { NONE, BLACK, WHITE }

    public static final String BLACK_TSUKA = "blade_tetra/legacy/fox_black_tsuka";
    public static final String WHITE_TSUKA = "blade_tetra/legacy/fox_white_tsuka";

    public static FoxLegacyParts fromStack(ItemStack stack) {
        return new FoxLegacyParts(
                Color.NONE,
                variantColor(stack, ModularSlashBladeItem.SAYA_SLOT, "fox_saya/"),
                variantColor(stack, ModularSlashBladeItem.TSUBA_SLOT, "fox_tsuba/"),
                variantColor(stack, ModularSlashBladeItem.TSUBA_SLOT, "fox_tsuba/"));
    }

    public Color completeSet() {
        if (saya != Color.NONE && saya == tsuba && saya == tsuka) {
            return saya;
        }
        return Color.NONE;
    }

    public boolean present() {
        return blade != Color.NONE || saya != Color.NONE
                || tsuba != Color.NONE || tsuka != Color.NONE;
    }

    public String signature() {
        return blade.name() + '-' + saya.name() + '-' + tsuba.name() + '-' + tsuka.name();
    }

    private static Color improvementColor(ItemStack stack, String slot,
            String black, String white) {
        if (ForgingImprovements.has(stack, slot, black)) {
            return Color.BLACK;
        }
        if (ForgingImprovements.has(stack, slot, white)) {
            return Color.WHITE;
        }
        return Color.NONE;
    }

    private static Color variantColor(ItemStack stack, String slot, String prefix) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(slot, Tag.TAG_STRING)) {
            return Color.NONE;
        }
        String key = tag.getString(slot) + "_material";
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return Color.NONE;
        }
        String variant = tag.getString(key);
        if ((prefix + "black").equals(variant)) {
            return Color.BLACK;
        }
        if ((prefix + "white").equals(variant)) {
            return Color.WHITE;
        }
        return Color.NONE;
    }
}
