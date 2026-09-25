package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.config.ClientVisualConfig;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialAppearance;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import dev.bladetetra.visual.TetraMaterialVisualResolver;
import dev.bladetetra.visual.TsukaWrapColor;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.event.client.RenderOverrideEvent;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.slf4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static dev.bladetetra.client.MaterialTextureManager.*;

/**
 * Material semantic layer for the procedural blade atlas.
 *
 * <p>Resolves material ids into palettes/surface patterns and owns the pattern
 * algorithms. Atlas loading, caching and component painting remain in
 * MaterialTextureManager.</p>
 */
final class MaterialTextureStyleEngine {
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

    static int decorateDragonScales(
            int color,
            Palette palette,
            int x,
            int y,
            int seed) {
        int row = Math.floorDiv(y + seed, 8);
        int localX = Math.floorMod(x + (row & 1) * 7 + seed, 14) - 7;
        int localY = Math.floorMod(y + seed, 8) - 1;
        double arc = Math.sqrt(
                localX * (double) localX
                        + localY * localY * 1.65D);
        if (localY >= 0 && Math.abs(arc - 6.5D) < 0.72D) {
            return Palette.lerp(color, palette.highlight(), 0.36F);
        }
        if (localY >= 1 && arc > 6.7D) {
            return Palette.scale(color, 0.76F);
        }
        return color;
    }

    static int cosmicHash(int x, int y, int seed) {
        int hash = seed ^ x * 0x45d9f3b ^ y * 0x119de1f3;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return hash & Integer.MAX_VALUE;
    }

    static int infinityEmissionAlpha(int x, int y, int seed) {
        int star = cosmicHash(x, y, seed);
        if (star % 613 == 0) {
            return 235;
        }
        if (star % 257 == 0) {
            return 165;
        }
        double nebula = Math.sin(
                x * 0.021D
                        + Math.sin(y * 0.037D + seed * 0.0003D) * 1.65D);
        return nebula > 0.965D ? 55 : 0;
    }

    static record MaterialStyle(
            Palette palette,
            SurfacePattern pattern,
            int seed) {
        int sample(float tone, int x, int y) {
            return pattern.decorate(
                    palette.sample(tone),
                    palette,
                    x,
                    y,
                    seed);
        }

        int sampleBlade(
                float tone,
                float atlasX,
                float atlasY) {
            return pattern.decorateBlade(
                    palette.sample(tone),
                    palette,
                    atlasX,
                    atlasY,
                    seed);
        }

        int emissionAlpha(
                int x,
                int y,
                float atlasX,
                float atlasY,
                boolean bladePixel) {
            if (bladePixel) {
                return pattern.bladeEmissionAlpha(
                        atlasX, atlasY, seed);
            }
            return pattern.emissionAlpha(x, y, seed);
        }
    }

    enum SurfacePattern {
        FORGED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double layer = Math.sin(
                        x * 0.19D
                                + Math.sin(y * 0.31D + seed * 0.001D)
                                * 1.75D);
                double crossing = Math.sin(
                        x * 0.071D - y * 0.23D + seed * 0.004D);
                if (layer > 0.90D) {
                    return Palette.lerp(color, palette.highlight(), 0.20F);
                }
                if (layer < -0.92D || crossing < -0.985D) {
                    return Palette.scale(color, 0.82F);
                }
                return color;
            }
        },
        HEAVY_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double band = Math.sin(
                        x * 0.082D
                                + Math.sin(y * 0.145D + seed * 0.002D)
                                * 0.86D);
                int hammer = cosmicHash(x / 3, y / 3, seed);
                if (band < -0.76D) {
                    return Palette.scale(color, 0.72F);
                }
                if (band > 0.90D) {
                    return Palette.lerp(color, palette.highlight(), 0.18F);
                }
                if (hammer % 67 == 0) {
                    return Palette.scale(color, 0.80F);
                }
                return Palette.scale(color, 0.94F);
            }
        },
        CRUDE_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int pit = cosmicHash(x, y, seed);
                double scale = Math.sin(
                        x * 0.29D
                                + Math.sin(y * 0.41D + seed * 0.006D)
                                * 1.42D);
                if (pit % 43 == 0 || scale < -0.965D) {
                    return Palette.scale(color, 0.66F);
                }
                if (pit % 59 == 0 || scale > 0.94D) {
                    return Palette.lerp(color, palette.highlight(), 0.16F);
                }
                return Palette.scale(color, 0.91F);
            }
        },
        POLISHED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double broadShine = Math.sin(
                        x * 0.037D + y * 0.14D + seed * 0.002D);
                if (broadShine > 0.84D) {
                    return Palette.lerp(color, palette.highlight(), 0.31F);
                }
                return broadShine < -0.94D
                        ? Palette.scale(color, 0.88F)
                        : color;
            }
        },
        PATINA_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double patina = Math.sin(
                        x * 0.083D
                                + Math.sin(y * 0.19D + seed * 0.002D)
                                * 1.35D);
                if (patina > 0.91D) {
                    return Palette.lerp(color, 0x4F8874, 0.22F);
                }
                if (patina < -0.94D) {
                    return Palette.scale(color, 0.88F);
                }
                return FORGED_METAL.decorate(color, palette, x, y, seed);
            }
        },
        NETHERITE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double lamination = Math.sin(
                        x * 0.12D
                                + Math.sin(y * 0.23D + seed * 0.003D)
                                * 1.1D);
                if (lamination < -0.88D) {
                    return Palette.scale(color, 0.76F);
                }
                if (lamination > 0.93D) {
                    return Palette.lerp(color, 0x715A58, 0.20F);
                }
                return color;
            }
        },
        PATTERN_WELDED {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double wave = Math.sin(
                        x * 0.56D
                                + Math.sin(y * 0.72D + seed * 0.01D) * 2.0D);
                if (wave > 0.72D) {
                    return Palette.lerp(color, palette.highlight(), 0.34F);
                }
                if (wave < -0.76D) {
                    return Palette.scale(color, 0.72F);
                }
                return color;
            }
        },
        CURSED_METAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double binding = Math.sin(
                        x * 0.075D + Math.sin(y * 0.13D + seed * 0.002D));
                double counter = Math.sin(
                        x * 0.041D - y * 0.19D + seed * 0.003D);
                int bound = binding > 0.88D && counter > 0.12D
                        ? Palette.lerp(color, palette.highlight(), 0.52F)
                        : binding < -0.91D
                        ? Palette.scale(color, 0.78F)
                        : color;
                return generatedSurface(
                        bound, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        x, y, seed, 0.42F);
            }
        },
        WITHERITE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int forged = HEAVY_METAL.decorate(color, palette, x, y, seed);
                return generatedCracks(
                        forged, palette, MaterialPatternMask.WITHER_CRACKS,
                        x, y, seed, 0.76F);
            }
        },
        DARK_ALLOY {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double seam = Math.sin(
                        x * 0.048D + y * 0.16D + seed * 0.002D);
                int alloy = seam > 0.93D
                        ? Palette.lerp(color, palette.highlight(), 0.26F)
                        : seam < -0.84D
                        ? Palette.scale(color, 0.68F)
                        : color;
                return generatedSurface(
                        alloy, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.24F);
            }
        },
        IRONWOOD {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double grain = Math.sin(
                        x * 0.11D + Math.sin(y * 0.08D + seed * 0.002D) * 1.3D);
                int layered = grain > 0.86D
                        ? Palette.lerp(color, palette.highlight(), 0.22F)
                        : grain < -0.88D
                        ? Palette.scale(color, 0.80F)
                        : color;
                return generatedSurface(
                        layered, palette, MaterialPatternMask.IRONWOOD_LAYERS,
                        x, y, seed, 0.34F);
            }
        },
        STEELEAF {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double vein = Math.sin(
                        x * 0.055D + Math.sin(y * 0.18D + seed * 0.002D) * 0.8D);
                double branch = Math.sin(x * 0.028D - y * 0.24D + seed * 0.004D);
                int leaf = vein > 0.94D || vein > 0.78D && branch > 0.82D
                        ? Palette.lerp(color, palette.highlight(), 0.38F)
                        : vein < -0.94D
                        ? Palette.scale(color, 0.84F)
                        : color;
                return generatedSurface(
                        leaf, palette, MaterialPatternMask.STEELEAF_VEINS,
                        x, y, seed, 0.38F);
            }
        },
        KNIGHTMETAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int plate = Math.floorMod(x + seed, 18);
                double bevel = Math.sin(y * 0.10D + seed * 0.002D);
                if (plate == 0 || plate == 1) {
                    return Palette.scale(color, 0.72F);
                }
                return bevel > 0.90D
                        ? Palette.lerp(color, palette.highlight(), 0.20F)
                        : color;
            }
        },
        DRAGON_FIRE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                double wave = Math.sin(
                        x * 0.14D
                                + Math.sin(y * 0.21D + seed * 0.01D) * 1.6D);
                if (wave > 0.91D) {
                    return Palette.lerp(scaled, 0xFF9D4A, 0.58F);
                }
                if (wave < -0.94D) {
                    return Palette.scale(scaled, 0.68F);
                }
                return scaled;
            }
        },
        DRAGON_ICE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                int frost = Math.floorMod(x * 3 - y * 5 + seed, 19);
                if (frost <= 1) {
                    return Palette.lerp(scaled, 0xE8FFFF, 0.68F);
                }
                if (Math.floorMod(x + y + seed, 31) == 0) {
                    return Palette.lerp(
                            scaled, palette.highlight(), 0.46F);
                }
                return CRYSTAL.decorate(scaled, palette, x, y, seed);
            }
        },
        DRAGON_LIGHTNING {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int scaled = decorateDragonScales(
                        color, palette, x, y, seed);
                int bolt = Math.floorMod(
                        x * 5 + y * 7 + y / 4 * 3 + seed, 29);
                boolean branch = Math.floorMod(y + seed, 8) == 0
                        && bolt >= 2
                        && bolt <= 6;
                if (bolt <= 1 || branch) {
                    return Palette.lerp(scaled, 0xEEE9FF, 0.82F);
                }
                return bolt == 28 ? Palette.scale(scaled, 0.68F) : scaled;
            }
        },
        GHOST {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int shimmer = Math.floorMod(x * 2 + y * 5 + seed, 23);
                int spectral = color;
                if (shimmer <= 2) {
                    spectral = Palette.lerp(color, 0xF0FFF8, 0.68F);
                } else if (Math.floorMod(x - y + seed, 13) == 0) {
                    spectral = Palette.scale(color, 0.72F);
                }
                return generatedSurface(
                        spectral, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        x, y, seed, 0.28F);
            }
        },
        APOCALYPTIUM {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int vein = Math.floorMod(
                        x * 7 + y * 3 + y / 5 * 4 + seed, 31);
                boolean branch = Math.floorMod(x + seed, 9) == 0
                        && vein >= 2
                        && vein <= 7;
                if (vein <= 1 || branch) {
                    return Palette.lerp(color, 0xFFD45E, 0.74F);
                }
                if (vein >= 28) {
                    return Palette.lerp(color, 0x721B0D, 0.52F);
                }
                return Palette.scale(color, 0.78F);
            }
        },
        INFINITY {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double nebula = Math.sin(
                        x * 0.021D
                                + Math.sin(y * 0.037D + seed * 0.0003D)
                                * 1.65D);
                int cosmic = Palette.scale(color, 0.66F);
                if (nebula > 0.38D) {
                    cosmic = Palette.lerp(
                            cosmic,
                            nebula > 0.82D ? 0x6245A8 : 0x31256B,
                            nebula > 0.82D ? 0.34F : 0.18F);
                } else if (nebula < -0.76D) {
                    cosmic = Palette.lerp(cosmic, 0x081A3C, 0.26F);
                }

                int star = cosmicHash(x, y, seed);
                if (star % 613 == 0) {
                    return Palette.lerp(cosmic, 0xFFFFFF, 0.92F);
                }
                if (star % 257 == 0) {
                    return Palette.lerp(cosmic, 0x8EC8FF, 0.66F);
                }
                return cosmic;
            }
        },
        CRYSTAL {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double longitudinal = Math.sin(
                        (x + seed * 0.03D) * 0.095D
                                + Math.sin((y - seed) * 0.21D) * 0.72D);
                double diagonal = Math.sin(
                        x * 0.052D + y * 0.39D + seed * 0.013D);
                int planeA = Math.floorMod(x + y * 2 + seed, 31);
                int planeB = Math.floorMod(x * 2 - y * 3 + seed, 43);
                int faceted = color;
                if (longitudinal > 0.62D) {
                    faceted = Palette.lerp(
                            color, palette.highlight(), 0.16F);
                } else if (longitudinal < -0.68D) {
                    faceted = Palette.scale(color, 0.90F);
                }

                if (diagonal > 0.985D || planeA <= 1 || planeB == 0) {
                    faceted = Palette.lerp(
                            faceted, palette.highlight(), 0.38F);
                } else if (planeA >= 27 && planeB >= 38) {
                    faceted = Palette.scale(faceted, 0.78F);
                }
                return generatedSurface(
                        faceted, palette, MaterialPatternMask.CRYSTAL_FACETS,
                        x, y, seed, 0.34F);
            }
        },
        OBSIDIAN {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int facetA = Math.floorMod(x + y * 3 + seed, 37);
                int facetB = Math.floorMod(x * 2 - y * 5 + seed, 53);
                boolean crack = facetA == 0 || facetB == 0;
                boolean branch = Math.floorMod(y + seed, 23) == 0
                        && facetA <= 8;
                int obsidian = color;
                if (crack || branch) {
                    obsidian = Palette.lerp(
                            color,
                            palette.highlight(),
                            branch ? 0.48F : 0.82F);
                } else if (facetA > 29 && facetB > 43) {
                    obsidian = Palette.lerp(color, palette.mid(), 0.22F);
                } else {
                    obsidian = Palette.scale(color, 0.84F);
                }
                return generatedSurface(
                        obsidian, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.30F);
            }
        },
        STONE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int speckle = Math.floorMod(x * 17 + y * 11 + seed, 37);
                if (speckle == 0) {
                    return Palette.scale(color, 0.82F);
                }
                if (speckle == 1) {
                    return Palette.lerp(color, palette.highlight(), 0.14F);
                }
                return color;
            }
        },
        WOOD {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int grain = Math.floorMod(x + y / 3 * 2 + seed, 13);
                if (grain == 0) {
                    return Palette.scale(color, 0.72F);
                }
                if (grain == 1) {
                    return Palette.lerp(color, palette.highlight(), 0.16F);
                }
                return color;
            }
        },
        BONE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int pore = Math.floorMod(x * 13 + y * 17 + seed, 53);
                return pore <= 1 ? Palette.scale(color, 0.58F) : color;
            }
        },
        BLAZE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int heat = Math.floorMod(x * 2 + y + seed, 13);
                int heated = heat <= 2
                        ? Palette.lerp(color, palette.highlight(), 0.56F)
                        : Palette.scale(color, heat == 12 ? 0.72F : 1.0F);
                return generatedCracks(
                        heated, palette, MaterialPatternMask.MOLTEN,
                        x, y, seed, 0.82F);
            }
        },
        ARCANE {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int lane = Math.floorMod(y + seed, 18);
                int glyph = Math.floorMod(x * 5 + y * 3 + seed, 41);
                boolean runeStroke = (lane == 8 || lane == 9)
                        && (glyph <= 8 || glyph >= 35);
                int inscribed = color;
                if (runeStroke) {
                    inscribed = Palette.lerp(
                            color, palette.highlight(), 0.52F);
                } else {
                    double underGlow = Math.sin(
                            x * 0.055D + y * 0.17D + seed * 0.004D);
                    if (underGlow > 0.92D) {
                        inscribed = Palette.lerp(color, palette.mid(), 0.22F);
                    } else {
                        inscribed = Palette.scale(color, 0.94F);
                    }
                }
                return generatedCracks(
                        inscribed, palette, MaterialPatternMask.ARCANE_CIRCUIT,
                        x, y, seed, 0.58F);
            }
        },
        ENDER {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                int shimmer = Math.floorMod(x * 3 + y * 5 + seed, 29);
                int voidMetal = shimmer == 0 || shimmer == 1
                        ? Palette.lerp(color, palette.highlight(), 0.62F)
                        : color;
                return generatedSurface(
                        voidMetal, palette, MaterialPatternMask.VOID_RUNES,
                        x, y, seed, 0.34F);
            }
        },
        TOXIC {
            @Override
            int decorate(int color, Palette palette, int x, int y, int seed) {
                double vein = Math.sin(
                        x * 0.071D
                                + Math.sin(y * 0.18D + seed * 0.004D)
                                * 1.28D);
                int blister = cosmicHash(x, y, seed);
                if (vein > 0.91D) {
                    return Palette.lerp(color, 0xB9F25B, 0.48F);
                }
                if (blister % 73 == 0) {
                    return Palette.lerp(color, 0xE4FF8A, 0.38F);
                }
                return vein < -0.94D
                        ? Palette.scale(color, 0.74F)
                        : Palette.lerp(color, 0x527A38, 0.08F);
            }
        };

        /**
         * Blade faces use a deliberately restrained finish. Component
         * textures may retain material grain, but a forged blade receives no
         * random speckles or rapidly repeating waves.
         */
        int decorateBlade(
                int color,
                Palette palette,
                float atlasX,
                float atlasY,
                int seed) {
            float progress = clamp01((atlasX - 1.0F) / 62.0F);
            int finished = switch (this) {
                case HEAVY_METAL -> heavyBlade(
                        color, palette, progress, atlasY, seed);
                case CRUDE_METAL -> crudeBlade(
                        color, palette, progress, atlasY, seed);
                case POLISHED_METAL -> broadPolish(
                        color, palette, progress, atlasY);
                case PATINA_METAL -> broadPatina(
                        color, progress, atlasY);
                case NETHERITE -> broadLamination(
                        color, progress, atlasY);
                case CURSED_METAL, WITHERITE, DARK_ALLOY -> broadLamination(
                        color, progress, atlasY);
                case IRONWOOD -> broadLamination(
                        color, progress, atlasY);
                case STEELEAF -> broadPatina(
                        color, progress, atlasY);
                case KNIGHTMETAL -> broadPolish(
                        color, palette, progress, atlasY);
                case CRYSTAL -> largeCrystalFacets(
                        color, palette, progress, atlasY);
                case OBSIDIAN -> continuousObsidianCrack(
                        color, palette, progress, atlasY, seed);
                case INFINITY -> cosmicBlade(
                        color, palette, atlasX, atlasY, seed);
                case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING ->
                        decorateDragonScales(
                                color,
                                palette,
                                Math.round(atlasX * 2.0F),
                                Math.round(atlasY * 2.0F),
                                seed);
                case TOXIC -> toxicBlade(
                        color, palette, progress, atlasY, seed);
                default -> color;
            };

            int maskX = Math.round(atlasX);
            int maskY = Math.round(atlasY * 2.0F);
            int generated = switch (this) {
                case BLAZE, APOCALYPTIUM -> generatedCracks(
                        finished, palette, MaterialPatternMask.MOLTEN,
                        maskX, maskY, seed, 0.68F);
                case CURSED_METAL, GHOST -> generatedSurface(
                        finished, palette, MaterialPatternMask.SPECTRAL_CURSE,
                        maskX, maskY, seed, 0.34F);
                case WITHERITE -> generatedCracks(
                        finished, palette, MaterialPatternMask.WITHER_CRACKS,
                        maskX, maskY, seed, 0.76F);
                case OBSIDIAN, ENDER -> generatedSurface(
                        finished, palette, MaterialPatternMask.VOID_RUNES,
                        maskX, maskY, seed, 0.28F);
                case DARK_ALLOY -> finished;
                case IRONWOOD -> generatedSurface(
                        finished, palette, MaterialPatternMask.IRONWOOD_LAYERS,
                        maskX, maskY, seed, 0.28F);
                case STEELEAF -> generatedSurface(
                        finished, palette, MaterialPatternMask.STEELEAF_VEINS,
                        maskX, maskY, seed, 0.32F);
                case CRYSTAL -> finished;
                case ARCANE -> generatedCracks(
                        finished, palette, MaterialPatternMask.ARCANE_CIRCUIT,
                        maskX, maskY, seed, 0.44F);
                default -> finished;
            };

            if (this == OBSIDIAN || this == CRYSTAL || this == INFINITY) {
                return generated;
            }
            return masterFlowLine(
                    generated, palette, progress, atlasY, seed,
                    switch (this) {
                        case DRAGON_FIRE, DRAGON_ICE, DRAGON_LIGHTNING -> 0.18F;
                        case CURSED_METAL -> 0.24F;
                        case WITHERITE -> 0.17F;
                        case DARK_ALLOY -> 0.08F;
                        case IRONWOOD, STEELEAF, KNIGHTMETAL,
                                PATTERN_WELDED, NETHERITE,
                                HEAVY_METAL -> 0.13F;
                        case CRUDE_METAL -> 0.07F;
                        case TOXIC -> 0.15F;
                        default -> 0.04F;
                    });
        }

        int bladeEmissionAlpha(
                float atlasX,
                float atlasY,
                int seed) {
            float progress = clamp01((atlasX - 1.0F) / 62.0F);
            double distance = Math.abs(
                    atlasY - masterFlowY(progress, seed));
            return switch (this) {
                case DRAGON_FIRE -> distance < 0.48D ? 190 : 0;
                case DRAGON_ICE -> distance < 0.42D ? 170 : 0;
                case DRAGON_LIGHTNING -> distance < 0.36D ? 215 : 0;
                case GHOST -> distance < 0.48D ? 145 : 0;
                case APOCALYPTIUM -> distance < 0.42D ? 190 : 0;
                case CURSED_METAL -> distance < 0.40D ? 175 : 0;
                case WITHERITE -> generatedEmission(
                        MaterialPatternMask.WITHER_CRACKS,
                        atlasX, atlasY, seed, 202, 145);
                case DARK_ALLOY -> distance < 0.28D ? 105 : 0;
                case INFINITY -> infinityEmissionAlpha(
                        Math.round(atlasX * 4.0F),
                        Math.round(atlasY * 4.0F),
                        seed);
                case CRYSTAL -> crystalFacetDistance(progress, atlasY) < 0.30D
                        ? 135 : 0;
                case OBSIDIAN -> obsidianCrackDistance(
                        progress, atlasY, seed) < 0.31D ? 175 : 0;
                case BLAZE -> Math.max(
                        distance < 0.48D ? 185 : 0,
                        generatedEmission(
                                MaterialPatternMask.MOLTEN,
                                atlasX, atlasY, seed, 208, 180));
                case ARCANE -> Math.max(
                        distance < 0.38D ? 155 : 0,
                        generatedEmission(
                                MaterialPatternMask.ARCANE_CIRCUIT,
                                atlasX, atlasY, seed, 218, 135));
                case ENDER -> Math.max(
                        distance < 0.40D ? 160 : 0,
                        generatedEmission(
                                MaterialPatternMask.VOID_RUNES,
                                atlasX, atlasY, seed, 226, 110));
                case TOXIC -> distance < 0.34D ? 105 : 0;
                default -> 0;
            };
        }

        static int generatedSurface(
                int color,
                Palette palette,
                MaterialPatternMask mask,
                int x,
                int y,
                int seed,
                float strength) {
            int value = mask.sample(x, y, seed);
            if (value > 128) {
                float amount = (value - 128) / 127.0F * strength;
                return Palette.lerp(color, palette.highlight(), amount);
            }
            float shade = (128 - value) / 128.0F * strength * 0.48F;
            return Palette.scale(color, 1.0F - shade);
        }

        static int generatedCracks(
                int color,
                Palette palette,
                MaterialPatternMask mask,
                int x,
                int y,
                int seed,
                float strength) {
            int value = mask.sample(x, y, seed);
            if (value >= 176) {
                float amount = (value - 176) / 79.0F * strength;
                return Palette.lerp(color, palette.highlight(), amount);
            }
            if (value <= 68) {
                float shade = (68 - value) / 68.0F * strength * 0.34F;
                return Palette.scale(color, 1.0F - shade);
            }
            return color;
        }

        static int generatedEmission(
                MaterialPatternMask mask,
                float atlasX,
                float atlasY,
                int seed,
                int threshold,
                int maximum) {
            int value = mask.sample(
                    Math.round(atlasX), Math.round(atlasY * 2.0F), seed);
            if (value <= threshold) {
                return 0;
            }
            return Math.round(
                    (value - threshold) / (float) (255 - threshold) * maximum);
        }

        static int heavyBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            int layered = broadLamination(color, progress, atlasY);
            double lowerBand = Math.abs(
                    atlasY - (18.3D - Math.sin(progress * Math.PI) * 0.38D));
            if (lowerBand < 0.72D) {
                layered = Palette.scale(layered, 0.91F);
            }
            int hammer = cosmicHash(
                    Math.round(progress * 46.0F),
                    Math.round(atlasY * 0.72F),
                    seed);
            return hammer % 97 == 0
                    ? Palette.scale(layered, 0.86F)
                    : Palette.lerp(layered, palette.mid(), 0.03F);
        }

        static int crudeBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            int pit = cosmicHash(
                    Math.round(progress * 84.0F),
                    Math.round(atlasY * 1.25F),
                    seed);
            if (pit % 89 == 0) {
                return Palette.scale(color, 0.74F);
            }
            if (pit % 131 == 0) {
                return Palette.lerp(color, palette.highlight(), 0.12F);
            }
            return Palette.scale(color, 0.97F);
        }

        static int toxicBlade(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            double veinY = masterFlowY(progress, seed)
                    + Math.sin(progress * Math.PI * 4.0D) * 0.26D;
            double distance = Math.abs(atlasY - veinY);
            if (distance < 0.26D) {
                return Palette.lerp(color, 0xC8FF65, 0.42F);
            }
            if (distance < 0.58D) {
                return Palette.lerp(color, 0x527A38, 0.14F);
            }
            return Palette.lerp(color, palette.mid(), 0.03F);
        }

        static int masterFlowLine(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed,
                float strength) {
            double offset = atlasY - masterFlowY(progress, seed);
            double distance = Math.abs(offset);
            if (distance < 0.22D) {
                return Palette.lerp(color, palette.highlight(), strength);
            }
            if (offset >= 0.22D && offset < 0.58D) {
                return Palette.scale(color, 1.0F - strength * 0.42F);
            }
            return color;
        }

        static double masterFlowY(float progress, int seed) {
            double phase = ((seed & 0xff) / 255.0D - 0.5D) * 0.12D;
            return 14.35D
                    + Math.sin((progress - 0.10D) * Math.PI + phase) * 0.72D
                    + (progress - 0.5D) * 0.18D;
        }

        static int broadPolish(
                int color,
                Palette palette,
                float progress,
                float atlasY) {
            double center = 9.0D + progress * 6.0D;
            double distance = Math.abs(atlasY - center);
            return distance < 3.4D
                    ? Palette.lerp(color, palette.highlight(),
                            (float) ((3.4D - distance) / 3.4D) * 0.055F)
                    : color;
        }

        static int broadPatina(
                int color,
                float progress,
                float atlasY) {
            double center = 17.0D
                    - Math.sin(progress * Math.PI) * 0.65D;
            double distance = Math.abs(atlasY - center);
            return distance < 2.8D
                    ? Palette.lerp(color, 0x4F8874,
                            (float) ((2.8D - distance) / 2.8D) * 0.075F)
                    : color;
        }

        static int broadLamination(
                int color,
                float progress,
                float atlasY) {
            double center = 16.6D
                    + Math.sin(progress * Math.PI) * 0.45D;
            double distance = Math.abs(atlasY - center);
            return distance < 1.4D
                    ? Palette.scale(color,
                            0.94F + (float) (distance / 1.4D) * 0.06F)
                    : color;
        }

        static int largeCrystalFacets(
                int color,
                Palette palette,
                float progress,
                float atlasY) {
            double first = atlasY - (8.0D + progress * 9.0D);
            double second = atlasY - (25.0D - progress * 7.0D);
            double boundary = Math.min(Math.abs(first), Math.abs(second));
            if (boundary < 0.34D) {
                return Palette.lerp(color, palette.highlight(), 0.28F);
            }
            if (first > 0.0D && second < 0.0D) {
                return Palette.lerp(color, palette.highlight(), 0.07F);
            }
            return Palette.scale(color, 0.96F);
        }

        static int cosmicBlade(
                int color,
                Palette palette,
                float atlasX,
                float atlasY,
                int seed) {
            int x = Math.round(atlasX * 4.0F);
            int y = Math.round(atlasY * 4.0F);
            double nebula = Math.sin(
                    x * 0.021D
                            + Math.sin(y * 0.037D + seed * 0.0003D)
                            * 1.65D);
            int cosmic = Palette.lerp(color, 0x070615, 0.58F);
            if (nebula > 0.32D) {
                cosmic = Palette.lerp(
                        cosmic,
                        nebula > 0.82D ? 0x6B4CB8 : 0x31256B,
                        nebula > 0.82D ? 0.38F : 0.20F);
            } else if (nebula < -0.76D) {
                cosmic = Palette.lerp(cosmic, 0x081A3C, 0.28F);
            }

            int star = cosmicHash(x, y, seed);
            if (star % 613 == 0) {
                return Palette.lerp(cosmic, 0xFFFFFF, 0.94F);
            }
            if (star % 257 == 0) {
                return Palette.lerp(cosmic, 0x8EC8FF, 0.68F);
            }
            return Palette.lerp(cosmic, palette.mid(), 0.06F);
        }

        static double crystalFacetDistance(
                float progress,
                float atlasY) {
            double first = Math.abs(atlasY - (8.0D + progress * 9.0D));
            double second = Math.abs(atlasY - (25.0D - progress * 7.0D));
            return Math.min(first, second);
        }

        static int continuousObsidianCrack(
                int color,
                Palette palette,
                float progress,
                float atlasY,
                int seed) {
            double distance = obsidianCrackDistance(
                    progress, atlasY, seed);
            if (distance < 0.24D) {
                return Palette.lerp(color, palette.highlight(), 0.72F);
            }
            if (distance < 0.52D) {
                return Palette.lerp(color, palette.mid(), 0.18F);
            }
            return Palette.scale(color, 0.90F);
        }

        static double obsidianCrackDistance(
                float progress,
                float atlasY,
                int seed) {
            double phase = ((seed >>> 8) & 0xff) / 255.0D * 0.16D;
            double crackY = 13.1D
                    + Math.sin((progress + phase) * Math.PI) * 1.05D
                    + progress * 0.42D;
            return Math.abs(atlasY - crackY);
        }

        int emissionAlpha(int x, int y, int seed) {
            return switch (this) {
                case DRAGON_FIRE -> {
                    double wave = Math.sin(
                            x * 0.14D
                                    + Math.sin(y * 0.21D + seed * 0.01D)
                                    * 1.6D);
                    yield wave > 0.91D ? 190 : 0;
                }
                case DRAGON_ICE -> {
                    int frost = Math.floorMod(x * 3 - y * 5 + seed, 19);
                    yield frost <= 1
                            ? 175
                            : Math.floorMod(x + y + seed, 31) == 0 ? 120 : 0;
                }
                case DRAGON_LIGHTNING -> {
                    int bolt = Math.floorMod(
                            x * 5 + y * 7 + y / 4 * 3 + seed, 29);
                    boolean branch = Math.floorMod(y + seed, 8) == 0
                            && bolt >= 2 && bolt <= 6;
                    yield bolt <= 1 || branch ? 235 : 0;
                }
                case GHOST -> Math.floorMod(x * 2 + y * 5 + seed, 23) <= 2
                        ? 155 : 0;
                case APOCALYPTIUM -> {
                    int vein = Math.floorMod(
                            x * 7 + y * 3 + y / 5 * 4 + seed, 31);
                    boolean branch = Math.floorMod(x + seed, 9) == 0
                            && vein >= 2 && vein <= 7;
                    yield vein <= 1 || branch ? 215 : 0;
                }
                case CURSED_METAL -> {
                    double binding = Math.sin(
                            x * 0.075D + Math.sin(y * 0.13D + seed * 0.002D));
                    double counter = Math.sin(
                            x * 0.041D - y * 0.19D + seed * 0.003D);
                    yield binding > 0.88D && counter > 0.12D ? 185 : 0;
                }
                case DARK_ALLOY -> {
                    double seam = Math.sin(
                            x * 0.048D + y * 0.16D + seed * 0.002D);
                    yield seam > 0.96D ? 95 : 0;
                }
                case INFINITY -> infinityEmissionAlpha(x, y, seed);
                case CRYSTAL -> {
                    int planeA = Math.floorMod(x + y * 2 + seed, 31);
                    int planeB = Math.floorMod(x * 2 - y * 3 + seed, 43);
                    double diagonal = Math.sin(
                            x * 0.052D + y * 0.39D + seed * 0.013D);
                    yield diagonal > 0.985D || planeA <= 1 || planeB == 0
                            ? 105 : 0;
                }
                case OBSIDIAN -> {
                    int facetA = Math.floorMod(x + y * 3 + seed, 37);
                    int facetB = Math.floorMod(x * 2 - y * 5 + seed, 53);
                    boolean branch = Math.floorMod(y + seed, 23) == 0
                            && facetA <= 8;
                    yield facetA == 0 || facetB == 0 || branch ? 125 : 0;
                }
                case BLAZE -> Math.floorMod(x * 2 + y + seed, 13) <= 2
                        ? 180 : 0;
                case ARCANE -> {
                    int lane = Math.floorMod(y + seed, 18);
                    int glyph = Math.floorMod(x * 5 + y * 3 + seed, 41);
                    boolean runeStroke = (lane == 8 || lane == 9)
                            && (glyph <= 8 || glyph >= 35);
                    double underGlow = Math.sin(
                            x * 0.055D + y * 0.17D + seed * 0.004D);
                    yield runeStroke || underGlow > 0.92D ? 155 : 0;
                }
                case ENDER -> {
                    int shimmer = Math.floorMod(x * 3 + y * 5 + seed, 29);
                    yield shimmer <= 1 ? 150 : 0;
                }
                case TOXIC -> {
                    double vein = Math.sin(
                            x * 0.071D
                                    + Math.sin(y * 0.18D + seed * 0.004D)
                                    * 1.28D);
                    yield vein > 0.94D ? 115 : 0;
                }
                default -> 0;
            };
        }

        abstract int decorate(
                int color,
                Palette palette,
                int x,
                int y,
                int seed);
    }

    static record Palette(int shadow, int mid, int highlight) {
        static Palette fromBase(
                int base,
                float shadowScale,
                float highlightScale) {
            return new Palette(
                    scale(base, shadowScale),
                    base,
                    scale(base, highlightScale));
        }

        int sample(float tone) {
            if (tone <= 0.5F) {
                return lerp(shadow, mid, tone * 2.0F);
            }
            return lerp(mid, highlight, (tone - 0.5F) * 2.0F);
        }

        static int lerp(int from, int to, float amount) {
            int red = Math.round(channel(from, 16)
                    + (channel(to, 16) - channel(from, 16)) * amount);
            int green = Math.round(channel(from, 8)
                    + (channel(to, 8) - channel(from, 8)) * amount);
            int blue = Math.round(channel(from, 0)
                    + (channel(to, 0) - channel(from, 0)) * amount);
            return red << 16 | green << 8 | blue;
        }

        static int scale(int color, float scale) {
            int red = Math.min(255, Math.round(channel(color, 16) * scale));
            int green = Math.min(255, Math.round(channel(color, 8) * scale));
            int blue = Math.min(255, Math.round(channel(color, 0) * scale));
            return red << 16 | green << 8 | blue;
        }

        static int channel(int color, int shift) {
            return color >>> shift & 0xff;
        }
    }

    private MaterialTextureStyleEngine() {
    }
}
