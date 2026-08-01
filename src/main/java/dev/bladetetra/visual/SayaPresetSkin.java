package dev.bladetetra.visual;

import dev.bladetetra.BladeTetra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Authored, cosmetic-only saya artwork.
 *
 * <p>The selected preset is stored independently from both Tetra module data
 * and SlashBlade's blade state. Applying a preset therefore changes no
 * durability, integrity, repair material, or combat behavior.</p>
 */
public enum SayaPresetSkin {
    NONE("none"),
    BLACK_GOLD("black_gold"),
    VERMILION_CLOUD("vermilion_cloud"),
    SEIGAIHA("seigaiha"),
    SAKURA("sakura"),
    PURPLE_LIGHTNING("purple_lightning"),
    AKATSUKI("akatsuki"),
    KYOUKA("kyouka");

    public static final String TAG_KEY = "blade_tetra_saya_preset";

    private final String serializedName;

    SayaPresetSkin(String serializedName) {
        this.serializedName = serializedName;
    }

    public static SayaPresetSkin fromStack(ItemStack stack) {
        CompoundTag itemTag = stack.getTag();
        if (itemTag == null || !itemTag.contains(TAG_KEY, Tag.TAG_STRING)) {
            return NONE;
        }

        String stored = itemTag.getString(TAG_KEY);
        for (SayaPresetSkin preset : values()) {
            if (preset.serializedName.equals(stored)) {
                return preset;
            }
        }
        return NONE;
    }

    public static boolean apply(ItemStack blade, SayaPresetSkin preset) {
        if (preset == null || preset == NONE) {
            return false;
        }
        SayaBannerSkin.clear(blade);
        blade.getOrCreateTag().putString(TAG_KEY, preset.serializedName);
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

    public boolean present() {
        return this != NONE;
    }

    public String serializedName() {
        return serializedName;
    }

    public Component displayName() {
        return Component.translatable(
                "saya_skin.blade_tetra." + serializedName);
    }

    public ResourceLocation textureLocation() {
        if (!present()) {
            throw new IllegalStateException("The empty saya preset has no texture");
        }
        return ResourceLocation.fromNamespaceAndPath(
                BladeTetra.MOD_ID,
                "textures/saya_skins/" + serializedName + ".png");
    }

    public static SayaPresetSkin bySerializedName(String name) {
        if (name == null) {
            return NONE;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (SayaPresetSkin preset : values()) {
            if (preset.serializedName.equals(normalized)) {
                return preset;
            }
        }
        return NONE;
    }
}
