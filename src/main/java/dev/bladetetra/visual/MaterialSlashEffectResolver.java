package dev.bladetetra.visual;

import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.SoulLegacyState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;

/**
 * Resolves a material-aware color for Resharped's synchronized slash effect.
 * The result is derived from Tetra's existing blade material variant and never
 * stored as another item property.
 */
public final class MaterialSlashEffectResolver {
    public static SlashVisual resolve(ItemStack stack) {
        if (AkatsukiAwakening.isActive(stack)) {
            return new SlashVisual(0xB5142C, ParticleTypes.CRIMSON_SPORE);
        }
        if (KyoukaAwakening.isActive(stack)) {
            int materialColor = resolve(MaterialAppearance.fromStack(stack).blade()).color();
            return new SlashVisual(blend(materialColor, 0xBEEBFF, 0.52F),
                    ParticleTypes.END_ROD);
        }
        if (SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.RAIKIRI)) {
            return new SlashVisual(0x9D8CFF, ParticleTypes.ELECTRIC_SPARK);
        }
        if (BladeLegacyEasterEggs.isBanshoUnlocked(stack)) {
            int color = switch (StyleResolver.resolve(stack)) {
                case IAIDO -> 0xC8F3FF;
                case RENGEKI -> 0xF3A2C8;
                case DANGAKU -> 0xF2C46D;
                default -> 0xD8D4FF;
            };
            if (NbtSageEasterEgg.isUnlocked(stack)) {
                color = blend(color, 0xC69BFF, 0.22F);
            }
            return new SlashVisual(color, ParticleTypes.ENCHANT);
        }
        if (BladeLegacyEasterEggs.isBairenUnlocked(stack)) {
            return new SlashVisual(0xF4E4B6, ParticleTypes.END_ROD);
        }
        if (BladeLegacyEasterEggs.isShoshinUnlocked(stack)) {
            return new SlashVisual(0xE1B875, null);
        }
        return resolve(MaterialAppearance.fromStack(stack).blade());
    }

    static SlashVisual resolve(String material) {
        SlashVisual adventureVisual = curatedAdventureVisual(material);
        if (adventureVisual != null) {
            return adventureVisual;
        }
        if (containsAny(material, "cursed_metal", "cursed_ingot")) {
            return new SlashVisual(0xB27AE8, ParticleTypes.WITCH);
        }
        if (containsAny(material, "dark_alloy", "dark_ingot")) {
            return new SlashVisual(0x765A9E, ParticleTypes.SOUL);
        }
        if (material.contains("steeleaf")) {
            return new SlashVisual(0x9BCB63, ParticleTypes.HAPPY_VILLAGER);
        }
        if (material.contains("ironwood")) {
            return new SlashVisual(0xC7B475, null);
        }
        if (material.contains("knightmetal")) {
            return new SlashVisual(0xC5D0CA, null);
        }
        if (containsAny(material, "dragonsteel_fire", "blaze", "fiery", "signalum")) {
            return new SlashVisual(0xF06A32, ParticleTypes.FLAME);
        }
        if (material.contains("apocalyptium")) {
            return new SlashVisual(0xD54116, ParticleTypes.FLAME);
        }
        if (containsAny(material, "dragonsteel_ice", "ice", "frost", "glacial", "frozen")) {
            return new SlashVisual(0x8BEAFF, ParticleTypes.SNOWFLAKE);
        }
        if (containsAny(material, "dragonsteel_lightning", "lightning")) {
            return new SlashVisual(0x8D82FF, ParticleTypes.ELECTRIC_SPARK);
        }
        if (containsAny(material, "ghost_ingot", "phantasmal", "spirit", "soul")) {
            return new SlashVisual(0x82F6DE, ParticleTypes.SOUL_FIRE_FLAME);
        }
        if (containsAny(material, "refined_obsidian", "obsidian")) {
            return new SlashVisual(0x7E65B5, ParticleTypes.REVERSE_PORTAL);
        }
        if (containsAny(material, "enderium", "ender", "azure")) {
            return new SlashVisual(0x61C6B9, ParticleTypes.PORTAL);
        }
        if (containsAny(material, "refined_glowstone", "lumium", "glowstone")) {
            return new SlashVisual(0xFFE477, ParticleTypes.END_ROD);
        }
        if (containsAny(
                material,
                "diamond",
                "emerald",
                "amethyst",
                "ruby",
                "sapphire",
                "topaz",
                "opal",
                "quartz",
                "crystal",
                "gem")) {
            int color = switch (material) {
                case "diamond" -> 0x8DECE7;
                case "emerald" -> 0x62DC89;
                case "amethyst", "pristine_amethyst" -> 0xC49AF0;
                default -> generatedColor(material);
            };
            return new SlashVisual(color, ParticleTypes.END_ROD);
        }
        if (material.contains("manasteel")) {
            return new SlashVisual(0x72C9F5, ParticleTypes.END_ROD);
        }
        if (material.contains("elementium")) {
            return new SlashVisual(0xF184C8, ParticleTypes.END_ROD);
        }
        if (material.contains("terrasteel")) {
            return new SlashVisual(0x72C978, ParticleTypes.END_ROD);
        }
        if (material.contains("cobalt")) {
            return new SlashVisual(0x659BE4, null);
        }
        if (containsAny(material, "netherite", "dark_steel")) {
            return new SlashVisual(0x8F707B, null);
        }
        if (containsAny(material, "bronze", "constantan", "rose_gold")) {
            return new SlashVisual(0xD28A54, null);
        }
        if (material.contains("brass")) {
            return new SlashVisual(0xE0C45E, null);
        }
        if (material.contains("copper")) {
            return new SlashVisual(0xDE8A60, null);
        }
        if (containsAny(material, "gold", "electrum")) {
            return new SlashVisual(0xFFE478, null);
        }
        if (material.contains("uranium")) {
            return new SlashVisual(0x8FBE61, ParticleTypes.HAPPY_VILLAGER);
        }
        if (containsAny(material, "silver", "tin", "aluminum", "aluminium", "zinc")) {
            return new SlashVisual(0xE1ECEC, null);
        }
        if (material.contains("lead")) {
            return new SlashVisual(0x7F85A3, null);
        }
        if (containsAny(material, "nickel", "invar")) {
            return new SlashVisual(0xCBC9A9, null);
        }
        if (material.contains("osmium")) {
            return new SlashVisual(0xAAC9E4, null);
        }
        if (containsAny(material, "steel", "iron", "forged_beam")) {
            return new SlashVisual(0xD1E0E6, null);
        }
        TetraMaterialVisualResolver.MaterialVisual visual =
                TetraMaterialVisualResolver.resolve(material);
        if (visual != null) {
            ParticleOptions particle = visual.kind()
                    == TetraMaterialVisualResolver.MaterialKind.GEM
                    ? ParticleTypes.END_ROD
                    : null;
            return new SlashVisual(
                    blend(visual.color(), 0xFFFFFF, 0.14F), particle);
        }
        return new SlashVisual(generatedColor(material), null);
    }

    private static SlashVisual curatedAdventureVisual(String material) {
        if (containsAny(material, "gobber_end", "gobber2_end")) {
            return new SlashVisual(0x61F4D2, ParticleTypes.PORTAL);
        }
        if (containsAny(material, "gobber_nether", "gobber2_nether")) {
            return new SlashVisual(0xF05A2A, ParticleTypes.FLAME);
        }
        if (containsAny(material, "gobber", "gobber2_ingot")) {
            return new SlashVisual(0x8DE4FF, ParticleTypes.END_ROD);
        }
        if (containsAny(
                material,
                "dark_metal_ingot",
                "armor_plate_from_dark_metal")) {
            return new SlashVisual(0xD34D46, ParticleTypes.CRIMSON_SPORE);
        }
        if (material.contains("ghost_steel")) {
            return new SlashVisual(0x9CFFE8, ParticleTypes.SOUL_FIRE_FLAME);
        }
        if (material.contains("immortal_ingot")) {
            return new SlashVisual(0xD6D095, ParticleTypes.WITCH);
        }
        if (material.contains("ignitium")) {
            return new SlashVisual(0xFF9D32, ParticleTypes.FLAME);
        }
        if (material.contains("witherite")) {
            return new SlashVisual(0xB1C98E, ParticleTypes.SOUL);
        }
        if (material.contains("cursium")) {
            return new SlashVisual(0xBD82E8, ParticleTypes.WITCH);
        }
        if (containsAny(material, "storm_ingot", "cataclysm_storm")) {
            return new SlashVisual(0x9CCBFF, ParticleTypes.ELECTRIC_SPARK);
        }
        if (containsAny(material, "abyssal_ingot", "cataclysm_abyssal")) {
            return new SlashVisual(0x5278AE, ParticleTypes.REVERSE_PORTAL);
        }
        if (containsAny(
                material,
                "black_steel_ingot",
                "cataclysm_black_steel")) {
            return new SlashVisual(0x7D8794, ParticleTypes.ASH);
        }
        if (containsAny(
                material,
                "ancient_metal_ingot",
                "cataclysm_ancient_metal")) {
            return new SlashVisual(0xD5B27A, null);
        }
        return null;
    }

    public static int blendTowardWhite(int color, float amount) {
        return blend(color, 0xFFFFFF, amount);
    }

    public static int blendTowardBlack(int color, float amount) {
        return blend(color, 0x08090C, amount);
    }

    private static int blend(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(channel(from, 16) * (1.0F - clamped)
                + channel(to, 16) * clamped);
        int green = Math.round(channel(from, 8) * (1.0F - clamped)
                + channel(to, 8) * clamped);
        int blue = Math.round(channel(from, 0) * (1.0F - clamped)
                + channel(to, 0) * clamped);
        return red << 16 | green << 8 | blue;
    }

    private static int generatedColor(String material) {
        int hash = material.hashCode();
        float hue = (hash & 0xffff) / 65535.0F;
        float saturation = 0.28F + ((hash >>> 16) & 0xff) / 255.0F * 0.25F;
        float brightness = 0.72F + ((hash >>> 24) & 0xff) / 255.0F * 0.18F;
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xff;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public record SlashVisual(int color, ParticleOptions particle) {
    }

    private MaterialSlashEffectResolver() {
    }
}
