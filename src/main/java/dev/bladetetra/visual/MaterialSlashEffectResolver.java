package dev.bladetetra.visual;

import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.KyoukaAwakening;
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
        if (BladeLegacyEasterEggs.isRaikiriUnlocked(stack)) {
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
        return new SlashVisual(generatedColor(material), null);
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
