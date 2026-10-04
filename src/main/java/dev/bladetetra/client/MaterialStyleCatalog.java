package dev.bladetetra.client;

import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.visual.TetraMaterialVisualResolver;

import dev.bladetetra.client.MaterialTextureStyleEngine.MaterialStyle;
import dev.bladetetra.client.MaterialTextureStyleEngine.Palette;
import dev.bladetetra.client.MaterialTextureStyleEngine.SurfacePattern;

/**
 * Resolves material identities into immutable visual styles.
 *
 * <p>This class owns catalog data and fallback heuristics only. Pixel decoration,
 * emission and blade-surface algorithms stay in {@link MaterialTextureStyleEngine}.
 * Keeping those responsibilities separate makes material ordering explicit and
 * prevents the renderer from becoming the compatibility registry.</p>
 */
final class MaterialStyleCatalog {
    static MaterialStyle styleFor(String material) {
        return switch (material) {
            case "iron" -> style(
                    new Palette(0x52636D, 0xAEBCC4, 0xEEF4F3),
                    SurfacePattern.FORGED_METAL,
                    material);
            case "copper" -> style(
                    new Palette(0x63382B, 0xB86C46, 0xE6AE7A),
                    SurfacePattern.PATINA_METAL,
                    material);
            case "gold" -> style(
                    new Palette(0x6B501A, 0xD6AD43, 0xFFF09A),
                    SurfacePattern.POLISHED_METAL,
                    material);
            case "netherite" -> style(
                    new Palette(0x211D22, 0x4B4245, 0x89777D),
                    SurfacePattern.NETHERITE,
                    material);
            case "diamond" -> crystal(
                    new Palette(0x1F6569, 0x59CFCB, 0xC6FFF5), material);
            case "emerald" -> crystal(
                    new Palette(0x174D32, 0x3FB66D, 0xB2F2BD), material);
            case "amethyst", "pristine_amethyst" ->
                    crystal(
                            new Palette(0x442E65, 0x9D70CC, 0xE2C5FF),
                            material);
            case "obsidian" -> style(
                    new Palette(0x171323, 0x34264F, 0x756398),
                    SurfacePattern.OBSIDIAN,
                    material);
            case "blackstone" -> stone(
                    new Palette(0x17171B, 0x34343A, 0x68686E), material);
            case "flint" -> stone(
                    new Palette(0x252A30, 0x535B63, 0x9AA3A8), material);
            case "stone" -> stone(
                    new Palette(0x3E4244, 0x777B7E, 0xB7BBBA), material);
            case "andesite" -> stone(
                    new Palette(0x4A4D4E, 0x86898A, 0xC7C9C7), material);
            case "diorite" -> stone(
                    new Palette(0x696B68, 0xC3C3BB, 0xF1F0E8), material);
            case "granite" -> stone(
                    new Palette(0x4E3029, 0x9B6250, 0xD8A083), material);
            case "bone" -> style(
                    new Palette(0x6F6754, 0xC9BC99, 0xF1E9D0),
                    SurfacePattern.BONE,
                    material);
            case "oak", "stick" -> wood(0x8A5A2F);
            case "spruce" -> wood(0x5A3A24);
            case "birch" -> wood(0xC5A86C);
            case "jungle" -> wood(0x9A5A35);
            case "acacia" -> wood(0xA54F2D);
            case "dark_oak" -> wood(0x3F2B20);
            case "mangrove" -> wood(0x6C2F2F);
            case "cherry" -> wood(0xC47F89);
            case "crimson" -> wood(0x762D4E);
            case "warped" -> wood(0x2D706D);
            case "bamboo" -> wood(0xAD9D45);
            case "blaze_rod" -> style(
                    new Palette(0x70230E, 0xD66A1D, 0xFFD05D),
                    SurfacePattern.BLAZE,
                    material);
            case "end_rod" -> style(
                    new Palette(0x69645A, 0xD8D0B1, 0xFFFBE3),
                    SurfacePattern.ARCANE,
                    material);
            case "forged_beam" -> style(
                    new Palette(0x343B40, 0x6B747B, 0xBBC4C7),
                    SurfacePattern.PATTERN_WELDED,
                    material);
            case "dragonsteel_fire" -> style(
                    new Palette(0x2C151B, 0x863B49, 0xF1D5D0),
                    SurfacePattern.DRAGON_FIRE,
                    material);
            case "dragonsteel_ice" -> style(
                    new Palette(0x123E5B, 0x3FA9C7, 0xC8F6FF),
                    SurfacePattern.DRAGON_ICE,
                    material);
            case "dragonsteel_lightning" -> style(
                    new Palette(0x21193D, 0x594DA6, 0xC8C1FF),
                    SurfacePattern.DRAGON_LIGHTNING,
                    material);
            case "ghost_ingot", "phantasmal_ingot" -> style(
                    new Palette(0x1F6D64, 0x78E7CF, 0xE4FFF2),
                    SurfacePattern.GHOST,
                    material);
            case "apocalyptium", "apocalyptium_ingot" -> style(
                    new Palette(0x160C08, 0xB33C13, 0xFFD45E),
                    SurfacePattern.APOCALYPTIUM,
                    material);
            case "infinity", "infinity_ingot" -> style(
                    new Palette(0x070615, 0x241B54, 0xD9E8FF),
                    SurfacePattern.INFINITY,
                    material);
            case "cursed_metal", "cursed_ingot" -> style(
                    new Palette(0x081B2B, 0x15536F, 0x6CBED0),
                    SurfacePattern.CURSED_METAL,
                    material);
            case "dark_alloy", "dark_ingot" -> style(
                    new Palette(0x09080C, 0x28232E, 0xAE83E8),
                    SurfacePattern.DARK_ALLOY,
                    material);
            case "fiery", "fiery_ingot" -> style(
                    new Palette(0x160B08, 0xB83A0B, 0xFFF0A0),
                    SurfacePattern.BLAZE,
                    material);
            case "ironwood", "ironwood_ingot" -> style(
                    new Palette(0x403628, 0x8A7246, 0xD7C58A),
                    SurfacePattern.IRONWOOD,
                    material);
            case "knightmetal", "knightmetal_ingot" -> style(
                    new Palette(0x303735, 0x68716E, 0xD9E0D7),
                    SurfacePattern.KNIGHTMETAL,
                    material);
            case "steeleaf", "steeleaf_ingot" -> style(
                    new Palette(0x24351E, 0x54733A, 0xBBDD78),
                    SurfacePattern.STEELEAF,
                    material);
            default -> externalMaterialStyle(material);
        };
    }

    static MaterialStyle foxStyle(MaterialStyle fallback,
            FoxLegacyParts.Color color, boolean blade) {
        return switch (color) {
            case BLACK -> style(
                    blade
                            ? new Palette(0x100E16, 0x3B3346, 0x8B788C)
                            : new Palette(0x151019, 0x44313F, 0x98605D),
                    blade ? SurfacePattern.DARK_ALLOY : SurfacePattern.FORGED_METAL,
                    blade ? "fox-black-blade" : "fox-black-fitting");
            case WHITE -> style(
                    blade
                            ? new Palette(0x7D8589, 0xCFD5D4, 0xFFF7EB)
                            : new Palette(0x786B69, 0xD8CBC3, 0xFFF4E6),
                    blade ? SurfacePattern.KNIGHTMETAL : SurfacePattern.FORGED_METAL,
                    blade ? "fox-white-blade" : "fox-white-fitting");
            case NONE -> fallback;
        };
    }

    static int applyFoxAccent(int base, FoxLegacyParts.Color color,
            float x, float y, int component) {
        if (color == FoxLegacyParts.Color.NONE) {
            return base;
        }
        int accent = color == FoxLegacyParts.Color.BLACK ? 0xB22B3B : 0xC8464D;
        boolean mark = switch (component) {
            case 0 -> x > 8.0F && x < 55.0F && y > 3.0F && y < 6.0F
                    && ((int) (x / 7.0F) & 1) == 0;
            case 1 -> y > 38.0F && y < 52.0F
                    && Math.abs(((x - 4.0F) % 12.0F) - 6.0F) + Math.abs(y - 45.0F) < 4.0F;
            case 2 -> ((int) (x + y) % 9) <= 1;
            case 3 -> Math.abs(x - 64.0F) + Math.abs(y - 70.0F) < 5.5F;
            default -> false;
        };
        return mark ? Palette.lerp(base, accent, component == 0 ? 0.26F : 0.62F) : base;
    }

    static MaterialStyle componentStyle(
            String material,
            int potatoBaseColor) {
        if ("potato".equals(material)) {
            return style(
                    Palette.fromBase(potatoBaseColor, 0.48F, 1.38F),
                    SurfacePattern.WOOD,
                    material + "-" + Integer.toHexString(potatoBaseColor));
        }
        return styleFor(material);
    }

    static MaterialStyle style(
            Palette palette,
            SurfacePattern pattern,
            String material) {
        return new MaterialStyle(palette, pattern, material.hashCode());
    }

    static MaterialStyle wood(int base) {
        return style(
                Palette.fromBase(base, 0.47F, 1.28F),
                SurfacePattern.WOOD,
                Integer.toHexString(base));
    }

    static MaterialStyle stone(Palette palette, String material) {
        return style(palette, SurfacePattern.STONE, material);
    }

    static MaterialStyle crystal(Palette palette, String material) {
        return style(palette, SurfacePattern.CRYSTAL, material);
    }

    static MaterialStyle externalMaterialStyle(String material) {
        MaterialStyle adventureStyle = curatedAdventureStyle(material);
        if (adventureStyle != null) {
            return adventureStyle;
        }
        if (containsAny(material, "cursed_ingot", "cursed_metal")) {
            return style(
                    new Palette(0x081B2B, 0x15536F, 0x6CBED0),
                    SurfacePattern.CURSED_METAL,
                    material);
        }
        if (containsAny(material, "dark_ingot", "dark_alloy")) {
            return style(
                    new Palette(0x09080C, 0x28232E, 0xAE83E8),
                    SurfacePattern.DARK_ALLOY,
                    material);
        }
        if (containsAny(material, "ironwood")) {
            return style(
                    new Palette(0x403628, 0x8A7246, 0xD7C58A),
                    SurfacePattern.IRONWOOD,
                    material);
        }
        if (containsAny(material, "knightmetal")) {
            return style(
                    new Palette(0x303735, 0x68716E, 0xD9E0D7),
                    SurfacePattern.KNIGHTMETAL,
                    material);
        }
        if (containsAny(material, "steeleaf")) {
            return style(
                    new Palette(0x24351E, 0x54733A, 0xBBDD78),
                    SurfacePattern.STEELEAF,
                    material);
        }
        if (containsAny(material, "infinity_ingot", "infinity")) {
            return style(
                    new Palette(0x070615, 0x241B54, 0xD9E8FF),
                    SurfacePattern.INFINITY,
                    material);
        }
        if (material.contains("dragonsteel_fire")) {
            return style(
                    new Palette(0x2C151B, 0x863B49, 0xF1D5D0),
                    SurfacePattern.DRAGON_FIRE,
                    material);
        }
        if (material.contains("dragonsteel_ice")) {
            return style(
                    new Palette(0x123E5B, 0x3FA9C7, 0xC8F6FF),
                    SurfacePattern.DRAGON_ICE,
                    material);
        }
        if (material.contains("dragonsteel_lightning")) {
            return style(
                    new Palette(0x21193D, 0x594DA6, 0xC8C1FF),
                    SurfacePattern.DRAGON_LIGHTNING,
                    material);
        }
        if (containsAny(material, "ghost_ingot", "phantasmal")) {
            return style(
                    new Palette(0x1F6D64, 0x78E7CF, 0xE4FFF2),
                    SurfacePattern.GHOST,
                    material);
        }
        if (material.contains("apocalyptium")) {
            return style(
                    new Palette(0x160C08, 0xB33C13, 0xFFD45E),
                    SurfacePattern.APOCALYPTIUM,
                    material);
        }
        MaterialStyle tetraMaterial = tetraMaterialStyle(material);
        if (tetraMaterial != null) {
            return tetraMaterial;
        }
        if (containsAny(material, "refined_obsidian", "obsidian")) {
            return style(
                    Palette.fromBase(0x432E63, 0.44F, 1.55F),
                    SurfacePattern.OBSIDIAN,
                    material);
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
            return crystal(generatedPalette(material), material);
        }
        if (containsAny(material, "ice", "frost", "glacial", "frozen")) {
            return style(
                    Palette.fromBase(0x63BCD0, 0.42F, 1.48F),
                    SurfacePattern.DRAGON_ICE,
                    material);
        }
        if (containsAny(material, "refined_glowstone", "lumium", "glowstone")) {
            return style(
                    Palette.fromBase(0xE0C65B, 0.48F, 1.34F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (containsAny(material, "blaze", "fiery", "signalum")) {
            return style(
                    Palette.fromBase(0xC95A2A, 0.42F, 1.38F),
                    SurfacePattern.BLAZE,
                    material);
        }
        if (containsAny(material, "enderium", "ender", "azure")) {
            return style(
                    Palette.fromBase(0x397D78, 0.43F, 1.42F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (containsAny(
                material,
                "mithril",
                "mythril",
                "aether",
                "arcane",
                "celestial",
                "spirit",
                "soul",
                "starmetal",
                "star_metal")) {
            return style(
                    Palette.fromBase(0x79AFC1, 0.42F, 1.46F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (material.contains("manasteel")) {
            return style(
                    Palette.fromBase(0x5CA9D8, 0.42F, 1.38F),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (material.contains("elementium")) {
            return style(
                    Palette.fromBase(0xD65DA5, 0.44F, 1.34F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (material.contains("terrasteel")) {
            return style(
                    Palette.fromBase(0x58A35B, 0.40F, 1.42F),
                    SurfacePattern.ENDER,
                    material);
        }
        if (material.contains("cobalt")) {
            return style(
                    Palette.fromBase(0x3D76BF, 0.42F, 1.40F),
                    SurfacePattern.FORGED_METAL,
                    material);
        }
        if (containsAny(material, "netherite", "dark_steel")) {
            return style(
                    Palette.fromBase(0x4B4245, 0.40F, 1.45F),
                    SurfacePattern.NETHERITE,
                    material);
        }
        if (containsAny(material, "tungsten", "titanium", "iridium", "platinum")) {
            int base = material.contains("tungsten")
                    ? 0x626B70
                    : material.contains("platinum") ? 0xD1D9D5 : 0x9DB4BE;
            return style(
                    Palette.fromBase(base, 0.44F, 1.38F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (containsAny(material, "adamant", "orichalcum")) {
            int base = material.contains("adamant") ? 0x426B52 : 0xA69C45;
            return style(
                    Palette.fromBase(base, 0.42F, 1.40F),
                    SurfacePattern.PATTERN_WELDED,
                    material);
        }
        if (containsAny(
                material,
                "damascus",
                "pattern_welded",
                "folded_steel",
                "forged_beam")) {
            return style(
                    externalMetalPalette(material),
                    SurfacePattern.PATTERN_WELDED,
                    material);
        }
        if (containsAny(
                material,
                "bronze",
                "brass",
                "constantan",
                "rose_gold")) {
            int base = material.contains("brass") ? 0xB99B45 : 0xA9693D;
            return style(
                    Palette.fromBase(base, 0.45F, 1.36F),
                    SurfacePattern.PATINA_METAL,
                    material);
        }
        if (containsAny(material, "copper")) {
            return style(
                    Palette.fromBase(0xB86C46, 0.44F, 1.36F),
                    SurfacePattern.PATINA_METAL,
                    material);
        }
        if (containsAny(material, "gold", "electrum")) {
            return style(
                    Palette.fromBase(0xD2B04F, 0.48F, 1.35F),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (containsAny(material, "uranium")) {
            return style(
                    Palette.fromBase(0x668C43, 0.40F, 1.45F),
                    SurfacePattern.STONE,
                    material);
        }
        if (containsAny(
                material,
                "steel",
                "silver",
                "tin",
                "lead",
                "nickel",
                "invar",
                "aluminum",
                "aluminium",
                "osmium",
                "zinc",
                "iron")) {
            return style(
                    externalMetalPalette(material),
                    SurfacePattern.FORGED_METAL,
                    material);
        }
        return style(
                generatedPalette(material),
                SurfacePattern.FORGED_METAL,
                material);
    }

    static MaterialStyle curatedAdventureStyle(String material) {
        if (material.contains("pale_steel")) {
            return style(
                    new Palette(0x35494C, 0x8BA9AA, 0xE6FFFF),
                    SurfacePattern.POLISHED_METAL,
                    material);
        }
        if (material.contains("terminum")) {
            return style(
                    new Palette(0x260938, 0x8327A8, 0xF28CFF),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (material.contains("enderite")) {
            return style(
                    new Palette(0x09050E, 0x4F0B62, 0xFF43E6),
                    SurfacePattern.ENDER,
                    material);
        }
        if (containsAny(material, "gobber_end", "gobber2_end")) {
            return style(
                    new Palette(0x0A3446, 0x24F2B8, 0xD8FFF5),
                    SurfacePattern.ENDER,
                    material);
        }
        if (containsAny(material, "gobber_nether", "gobber2_nether")) {
            return style(
                    new Palette(0x2A0B08, 0xC93102, 0xFFB35C),
                    SurfacePattern.BLAZE,
                    material);
        }
        if (containsAny(material, "gobber", "gobber2_ingot")) {
            return style(
                    new Palette(0x123C5C, 0x67C4F5, 0xDDFBFF),
                    SurfacePattern.ARCANE,
                    material);
        }
        if (containsAny(
                material,
                "dark_metal_ingot",
                "armor_plate_from_dark_metal")) {
            return style(
                    new Palette(0x080607, 0x302326, 0xE64D43),
                    SurfacePattern.DARK_ALLOY,
                    material);
        }
        if (material.contains("ghost_steel")) {
            return style(
                    new Palette(0x0C3E4B, 0x68C7C4, 0xE8FFF5),
                    SurfacePattern.GHOST,
                    material);
        }
        if (material.contains("immortal_ingot")) {
            return style(
                    new Palette(0x39372B, 0xBDB77E, 0xE8FF9A),
                    SurfacePattern.TOXIC,
                    material);
        }
        if (material.contains("ignitium")) {
            return style(
                    new Palette(0x391109, 0xE06B18, 0xFFE07B),
                    SurfacePattern.BLAZE,
                    material);
        }
        if (material.contains("witherite")) {
            return style(
                    new Palette(0x171B20, 0x59616A, 0xFF4A3D),
                    SurfacePattern.WITHERITE,
                    material);
        }
        if (material.contains("cursium")) {
            return style(
                    new Palette(0x073C3F, 0x14B7A8, 0xA5FFF3),
                    SurfacePattern.CRYSTAL,
                    material);
        }
        if (containsAny(material, "storm_ingot", "cataclysm_storm")) {
            return style(
                    new Palette(0x142942, 0x4A78BC, 0xE6F3FF),
                    SurfacePattern.DRAGON_LIGHTNING,
                    material);
        }
        if (containsAny(material, "abyssal_ingot", "cataclysm_abyssal")) {
            return style(
                    new Palette(0x0B0719, 0x3E168A, 0xA26CFF),
                    SurfacePattern.OBSIDIAN,
                    material);
        }
        if (containsAny(
                material,
                "black_steel_ingot",
                "cataclysm_black_steel")) {
            return style(
                    new Palette(0x0B0D11, 0x303640, 0x7D8794),
                    SurfacePattern.NETHERITE,
                    material);
        }
        if (containsAny(
                material,
                "ancient_metal_ingot",
                "cataclysm_ancient_metal")) {
            return style(
                    new Palette(0x5A2708, 0xC57A1D, 0xFFF0A0),
                    SurfacePattern.HEAVY_METAL,
                    material);
        }
        return null;
    }

    static MaterialStyle tetraMaterialStyle(String material) {
        TetraMaterialVisualResolver.MaterialVisual visual =
                TetraMaterialVisualResolver.resolve(material);
        if (visual == null) {
            return null;
        }

        int visibleBase = visual.color() == 0 ? 0x181818 : visual.color();
        Palette palette = Palette.fromBase(visibleBase, 0.45F, 1.40F);
        SurfacePattern surfacePattern = switch (visual.kind()) {
            case GEM -> SurfacePattern.CRYSTAL;
            case BONE -> SurfacePattern.BONE;
            case WOOD -> SurfacePattern.WOOD;
            case STONE -> SurfacePattern.STONE;
            case CLOTH, LEATHER -> SurfacePattern.BONE;
            case METAL -> switch (visual.surface()) {
                case POLISHED -> SurfacePattern.POLISHED_METAL;
                case CRUDE -> SurfacePattern.CRUDE_METAL;
                case HEAVY -> SurfacePattern.HEAVY_METAL;
                case DEFAULT -> SurfacePattern.FORGED_METAL;
            };
        };
        SurfacePattern pattern = switch (visual.trait()) {
            case FIRE -> SurfacePattern.BLAZE;
            case ICE -> SurfacePattern.DRAGON_ICE;
            case LIGHTNING -> SurfacePattern.DRAGON_LIGHTNING;
            case SOUL -> SurfacePattern.GHOST;
            case SHADOW -> SurfacePattern.DARK_ALLOY;
            case CURSED -> SurfacePattern.CURSED_METAL;
            case COSMIC -> SurfacePattern.INFINITY;
            case ARCANE -> SurfacePattern.ARCANE;
            case TOXIC -> SurfacePattern.TOXIC;
            case NONE -> surfacePattern;
        };
        return style(palette, pattern, material);
    }

    static Palette externalMetalPalette(String material) {
        int base;
        if (material.contains("silver") || material.contains("tin")) {
            base = 0xC2CED0;
        } else if (material.contains("lead")) {
            base = 0x555A72;
        } else if (material.contains("nickel") || material.contains("invar")) {
            base = 0xA6A58A;
        } else if (material.contains("osmium")) {
            base = 0x829CB5;
        } else if (material.contains("zinc")) {
            base = 0xAAB8AE;
        } else if (material.contains("aluminum")
                || material.contains("aluminium")) {
            base = 0xBCC8CE;
        } else {
            base = 0x919EA5;
        }
        return Palette.fromBase(base, 0.46F, 1.34F);
    }

    static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static Palette generatedPalette(String material) {
        int hash = material.hashCode();
        float hue = (hash & 0xffff) / 65535.0F;
        float saturation = 0.28F + ((hash >>> 16) & 0xff) / 255.0F * 0.25F;
        float brightness = 0.56F + ((hash >>> 24) & 0xff) / 255.0F * 0.16F;
        int base = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xffffff;
        return Palette.fromBase(base, 0.48F, 1.32F);
    }

    private MaterialStyleCatalog() {
    }
}
