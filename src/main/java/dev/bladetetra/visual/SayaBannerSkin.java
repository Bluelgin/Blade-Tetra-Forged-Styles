package dev.bladetetra.visual;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.util.List;

/**
 * A cosmetic-only banner skin for the existing saya mesh.
 *
 * <p>The data is intentionally separate from Tetra module material data and
 * SlashBlade's blade state. It therefore cannot change module properties,
 * repair materials, combat effects, or durability.</p>
 */
public record SayaBannerSkin(
        boolean present,
        DyeColor baseColor,
        ListTag patterns) {
    public static final String TAG_KEY = "blade_tetra_saya_banner";
    private static final String BASE_COLOR_KEY = "base_color";
    private static final String PATTERNS_KEY = "patterns";
    private static final int MAX_PATTERNS = BannerBlockEntity.MAX_PATTERNS;
    private static final SayaBannerSkin NONE =
            new SayaBannerSkin(false, DyeColor.WHITE, new ListTag());

    public SayaBannerSkin {
        patterns = copyAndClamp(patterns);
    }

    public static SayaBannerSkin fromStack(ItemStack stack) {
        CompoundTag itemTag = stack.getTag();
        if (itemTag == null
                || !itemTag.contains(TAG_KEY, Tag.TAG_COMPOUND)) {
            return NONE;
        }

        CompoundTag skinTag = itemTag.getCompound(TAG_KEY);
        DyeColor baseColor = DyeColor.byId(
                skinTag.getInt(BASE_COLOR_KEY));
        ListTag patterns = skinTag.getList(
                PATTERNS_KEY,
                Tag.TAG_COMPOUND);
        return new SayaBannerSkin(true, baseColor, patterns);
    }

    public static boolean apply(ItemStack blade, ItemStack banner) {
        if (!(banner.getItem() instanceof BannerItem bannerItem)) {
            return false;
        }

        SayaPresetSkin.clear(blade);
        CompoundTag skinTag = new CompoundTag();
        skinTag.putInt(BASE_COLOR_KEY, bannerItem.getColor().getId());
        skinTag.put(
                PATTERNS_KEY,
                copyAndClamp(BannerBlockEntity.getItemPatterns(banner)));
        blade.getOrCreateTag().put(TAG_KEY, skinTag);
        return true;
    }

    public static boolean clear(ItemStack blade) {
        CompoundTag itemTag = blade.getTag();
        if (itemTag == null || !itemTag.contains(TAG_KEY)) {
            return false;
        }
        itemTag.remove(TAG_KEY);
        return true;
    }

    public int patternCount() {
        return patterns.size();
    }

    public String signature() {
        if (!present) {
            return "none";
        }
        return baseColor.getId() + ":" + patterns;
    }

    public List<Pair<Holder<BannerPattern>, DyeColor>> layers() {
        return BannerBlockEntity.createPatterns(baseColor, patterns);
    }

    private static ListTag copyAndClamp(ListTag source) {
        ListTag copy = source.copy();
        while (copy.size() > MAX_PATTERNS) {
            copy.remove(copy.size() - 1);
        }
        return copy;
    }
}
