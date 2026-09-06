package dev.bladetetra.easteregg;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** Resolves the bespoke technique formed by one active two-soul inscription. */
public final class SoulResonance {
    public enum Resonance {
        MIRROR_MOON,
        FUNERAL_BLOSSOM,
        CRIMSON_THUNDER,
        MIRROR_BLOSSOM,
        MIRROR_THUNDER,
        THUNDER_BLOSSOM,
        NONE
    }

    public static Resonance resolve(ItemStack stack) {
        EnumSet<SoulLegacyState.Legacy> souls = SoulLegacyState.inscribed(stack);
        if (souls.size() != 2) return Resonance.NONE;
        if (has(souls, SoulLegacyState.Legacy.AKATSUKI,
                SoulLegacyState.Legacy.KYOUKA)) return Resonance.MIRROR_MOON;
        if (has(souls, SoulLegacyState.Legacy.AKATSUKI,
                SoulLegacyState.Legacy.SENBONZAKURA)) return Resonance.FUNERAL_BLOSSOM;
        if (has(souls, SoulLegacyState.Legacy.AKATSUKI,
                SoulLegacyState.Legacy.RAIKIRI)) return Resonance.CRIMSON_THUNDER;
        if (has(souls, SoulLegacyState.Legacy.KYOUKA,
                SoulLegacyState.Legacy.SENBONZAKURA)) return Resonance.MIRROR_BLOSSOM;
        if (has(souls, SoulLegacyState.Legacy.KYOUKA,
                SoulLegacyState.Legacy.RAIKIRI)) return Resonance.MIRROR_THUNDER;
        if (has(souls, SoulLegacyState.Legacy.SENBONZAKURA,
                SoulLegacyState.Legacy.RAIKIRI)) return Resonance.THUNDER_BLOSSOM;
        return Resonance.NONE;
    }

    public static boolean is(ItemStack stack, Resonance resonance) {
        return resonance != Resonance.NONE && resolve(stack) == resonance;
    }

    public static boolean isFormed(ItemStack stack, Resonance resonance) {
        if (!is(stack, resonance)) return false;
        if (SoulLegacyState.inscribed(stack).stream().anyMatch(soul ->
                !SoulLegacyState.isActive(stack, soul))) return false;
        BladeStyle style = StyleResolver.resolve(stack);
        return switch (resonance) {
            case MIRROR_MOON, MIRROR_THUNDER -> style == BladeStyle.IAIDO;
            case FUNERAL_BLOSSOM, THUNDER_BLOSSOM -> style == BladeStyle.RENGEKI;
            case MIRROR_BLOSSOM -> style == BladeStyle.IAIDO || style == BladeStyle.RENGEKI;
            case CRIMSON_THUNDER -> true;
            case NONE -> false;
        };
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        Resonance resonance = resolve(stack);
        if (resonance == Resonance.NONE) return;
        String suffix = resonance.name().toLowerCase(Locale.ROOT);
        boolean awakened = SoulLegacyState.inscribed(stack).stream().allMatch(soul ->
                SoulLegacyState.isActive(stack, soul));
        if (isFormed(stack, resonance)) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.resonance.formed",
                            Component.translatable("tooltip.blade_tetra.resonance." + suffix))
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.resonance." + suffix + ".description")
                    .withStyle(ChatFormatting.GRAY));
        } else if (!awakened) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.resonance.dormant",
                            Component.translatable("tooltip.blade_tetra.resonance." + suffix))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.blade_tetra.resonance.unformed",
                            Component.translatable("tooltip.blade_tetra.resonance." + suffix))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.resonance." + suffix + ".unformed")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean has(EnumSet<SoulLegacyState.Legacy> souls,
            SoulLegacyState.Legacy first, SoulLegacyState.Legacy second) {
        return souls.contains(first) && souls.contains(second);
    }

    private SoulResonance() {
    }
}
