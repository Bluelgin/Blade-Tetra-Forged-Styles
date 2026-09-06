package dev.bladetetra.visual;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A single bounded cosmetic value for the cord wrapped around a tsuka. */
public final class TsukaWrapColor {
    public static final String DEFAULT = "default";
    private static final String TAG_KEY = "blade_tetra:tsuka_wrap_color";

    public static String resolve(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.contains(TAG_KEY, Tag.TAG_BYTE)) {
            return DEFAULT;
        }
        int id = tag.getByte(TAG_KEY);
        return id >= 0 && id < DyeColor.values().length
                ? DyeColor.byId(id).getName()
                : DEFAULT;
    }

    public static DyeColor dye(String serializedName) {
        for (DyeColor color : DyeColor.values()) {
            if (color.getName().equals(serializedName)) {
                return color;
            }
        }
        return null;
    }

    public static DyeColor fromCarpet(ItemStack stack) {
        for (DyeColor color : DyeColor.values()) {
            if (stack.is(carpet(color))) {
                return color;
            }
        }
        return null;
    }

    public static void apply(ItemStack blade, DyeColor color) {
        blade.getOrCreateTag().putByte(TAG_KEY, (byte) color.getId());
    }

    public static boolean clear(ItemStack blade) {
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.contains(TAG_KEY, Tag.TAG_BYTE)) {
            return false;
        }
        tag.remove(TAG_KEY);
        return true;
    }

    private static Item carpet(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_CARPET;
            case ORANGE -> Items.ORANGE_CARPET;
            case MAGENTA -> Items.MAGENTA_CARPET;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_CARPET;
            case YELLOW -> Items.YELLOW_CARPET;
            case LIME -> Items.LIME_CARPET;
            case PINK -> Items.PINK_CARPET;
            case GRAY -> Items.GRAY_CARPET;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_CARPET;
            case CYAN -> Items.CYAN_CARPET;
            case PURPLE -> Items.PURPLE_CARPET;
            case BLUE -> Items.BLUE_CARPET;
            case BROWN -> Items.BROWN_CARPET;
            case GREEN -> Items.GREEN_CARPET;
            case RED -> Items.RED_CARPET;
            case BLACK -> Items.BLACK_CARPET;
        };
    }

    private TsukaWrapColor() {
    }
}
