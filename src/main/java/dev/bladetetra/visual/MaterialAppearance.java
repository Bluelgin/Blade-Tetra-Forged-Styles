package dev.bladetetra.visual;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A deterministic view of the materials installed in a modular SlashBlade.
 *
 * <p>The values are derived directly from Tetra's existing module variant
 * strings. Nothing in this record is persisted as additional item NBT.</p>
 */
public record MaterialAppearance(
        String blade,
        String tsuka,
        String tsuba,
        String saya,
        String habaki,
        String kashira,
        String fuller,
        FullerProfile fullerProfile,
        EdgeFinishProfile edgeFinishProfile,
        TsukaProfile tsukaProfile,
        TsubaProfile tsubaProfile,
        SayaBannerSkin sayaSkin,
        SayaPresetSkin sayaPreset) {

    public static MaterialAppearance fromStack(ItemStack stack) {
        String blade = materialInSlot(
                stack, ModularSlashBladeItem.BLADE_SLOT, "iron");
        String tsuka = materialInSlot(
                stack, ModularSlashBladeItem.TSUKA_SLOT, "stick");
        String tsuba = materialInSlot(
                stack, ModularSlashBladeItem.TSUBA_SLOT, "iron");
        String saya = materialInSlot(
                stack, ModularSlashBladeItem.SAYA_SLOT, "oak");
        String habaki = materialInSlot(
                stack, ModularSlashBladeItem.HABAKI_SLOT, tsuba);
        String kashira = materialInSlot(
                stack, ModularSlashBladeItem.KASHIRA_SLOT, tsuka);
        String fuller = materialInSlot(
                stack, ModularSlashBladeItem.FULLER_SLOT, blade);
        FullerProfile fullerProfile = fullerProfile(stack);
        EdgeFinishProfile edgeFinishProfile = edgeFinishProfile(stack);
        TsukaProfile tsukaProfile = tsukaProfile(stack);
        TsubaProfile tsubaProfile = tsubaProfile(stack);
        SayaBannerSkin sayaSkin = SayaBannerSkin.fromStack(stack);
        SayaPresetSkin sayaPreset = SayaPresetSkin.fromStack(stack);
        return new MaterialAppearance(
                blade,
                tsuka,
                tsuba,
                saya,
                habaki,
                kashira,
                fuller,
                fullerProfile,
                edgeFinishProfile,
                tsukaProfile,
                tsubaProfile,
                sayaSkin,
                sayaPreset);
    }

    public String signature() {
        return String.join(
                "|",
                "atlas512-material-art-v4",
                blade,
                tsuka,
                tsuba,
                saya,
                habaki,
                kashira,
                fuller,
                fullerProfile.serializedName,
                edgeFinishProfile.serializedName,
                tsukaProfile.serializedName,
                tsubaProfile.serializedName,
                sayaSkin.signature(),
                sayaPreset.serializedName());
    }

    public ResourceLocation textureLocation() {
        long hash = 0xcbf29ce484222325L;
        for (byte value : signature().getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return ResourceLocation.fromNamespaceAndPath(
                BladeTetra.MOD_ID,
                "generated/material_" + Long.toUnsignedString(hash, 16));
    }

    private static String materialInSlot(
            ItemStack stack,
            String slot,
            String fallback) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(slot, Tag.TAG_STRING)) {
            return normalize(fallback);
        }

        String module = tag.getString(slot);
        String variantKey = module + "_material";
        if (!tag.contains(variantKey, Tag.TAG_STRING)) {
            return normalize(fallback);
        }

        String variant = tag.getString(variantKey);
        int separator = variant.lastIndexOf('/');
        String material = separator >= 0
                ? variant.substring(separator + 1)
                : variant;
        return material.isBlank() ? normalize(fallback) : normalize(material);
    }

    private static FullerProfile fullerProfile(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !tag.contains(
                        ModularSlashBladeItem.FULLER_SLOT,
                        Tag.TAG_STRING)) {
            return FullerProfile.NONE;
        }

        return switch (tag.getString(ModularSlashBladeItem.FULLER_SLOT)) {
            case ModularSlashBladeItem.LIGHTWEIGHT_FULLER_MODULE ->
                    FullerProfile.WIDE_BO_HI;
            case ModularSlashBladeItem.FULLER_MODULE ->
                    FullerProfile.TWIN_RIDGE;
            default -> FullerProfile.NONE;
        };
    }

    private static TsubaProfile tsubaProfile(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !tag.contains(
                        ModularSlashBladeItem.TSUBA_SLOT,
                        Tag.TAG_STRING)) {
            return TsubaProfile.MARU;
        }

        return switch (tag.getString(ModularSlashBladeItem.TSUBA_SLOT)) {
            case ModularSlashBladeItem.LIGHT_TSUBA_MODULE ->
                    TsubaProfile.MOKKO;
            case ModularSlashBladeItem.GUARD_TSUBA_MODULE ->
                    TsubaProfile.KAKU;
            default -> TsubaProfile.MARU;
        };
    }

    private static EdgeFinishProfile edgeFinishProfile(ItemStack stack) {
        if (ForgingImprovements.has(
                stack,
                ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.HAMAGURI)) {
            return EdgeFinishProfile.HAMAGURI;
        }
        if (ForgingImprovements.has(
                stack,
                ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.HIRA)) {
            return EdgeFinishProfile.HIRA;
        }
        if (ForgingImprovements.has(
                stack,
                ModularSlashBladeItem.BLADE_SLOT,
                ForgingImprovements.USUBA)) {
            return EdgeFinishProfile.USUBA;
        }
        return EdgeFinishProfile.PLAIN;
    }

    private static TsukaProfile tsukaProfile(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null
                || !tag.contains(
                        ModularSlashBladeItem.TSUKA_SLOT,
                        Tag.TAG_STRING)) {
            return TsukaProfile.STANDARD;
        }

        return switch (tag.getString(ModularSlashBladeItem.TSUKA_SLOT)) {
            case ModularSlashBladeItem.SWIFT_TSUKA_MODULE ->
                    TsukaProfile.SWIFT;
            case ModularSlashBladeItem.STABLE_TSUKA_MODULE ->
                    TsukaProfile.STABLE;
            default -> TsukaProfile.STANDARD;
        };
    }

    private static String normalize(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.lastIndexOf(':');
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return normalized
                .replaceAll("[^a-z0-9_\\-.]", "_");
    }

    public enum FullerProfile {
        NONE("none"),
        WIDE_BO_HI("wide_bo_hi"),
        TWIN_RIDGE("twin_ridge");

        private final String serializedName;

        FullerProfile(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    public enum EdgeFinishProfile {
        PLAIN("plain"),
        HAMAGURI("hamaguri"),
        HIRA("hira"),
        USUBA("usuba");

        private final String serializedName;

        EdgeFinishProfile(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    public enum TsubaProfile {
        MARU("maru"),
        MOKKO("mokko"),
        KAKU("kaku");

        private final String serializedName;

        TsubaProfile(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    public enum TsukaProfile {
        STANDARD("standard"),
        SWIFT("swift"),
        STABLE("stable");

        private final String serializedName;

        TsukaProfile(String serializedName) {
            this.serializedName = serializedName;
        }
    }

}
