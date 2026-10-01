package dev.bladetetra.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Compiled routing description of a player-authored Slash Art.
 *
 * <p>The editable composition lives on a Tetra Slash Art Orb. A blade receives a
 * versioned {@link ForgedSlashArtSpec} snapshot; this class compiles that snapshot
 * into a native ComboState splice plan. SlashBlade remains authoritative over
 * animation, movement, VFX, hit effects and damage.</p>
 */
public record ForgedSlashArtPlan(
        String key,
        String coreVariant,
        Technique primary,
        Technique secondary) {

    /** Compile an inscription already stored on a blade. */
    public static ForgedSlashArtPlan from(ItemStack stack) {
        return compose(ForgedSlashArtSpec.fromBlade(stack));
    }

    /** Compile the current editable module state of a Slash Art Orb. */
    public static ForgedSlashArtPlan fromOrb(ItemStack stack) {
        return compose(ForgedSlashArtSpec.fromOrb(stack));
    }

    static ForgedSlashArtPlan compose(ForgedSlashArtSpec spec) {
        return spec == null
                ? null
                : compose(spec.coreVariant(), spec.primary(), spec.secondary());
    }

    static ForgedSlashArtPlan compose(String coreVariant,
            Technique primary, Technique secondary) {
        if (coreVariant == null || coreVariant.isBlank()
                || primary == null || secondary == null) {
            return null;
        }

        String key = coreVariant + "|" + primary.id() + "|" + secondary.id();
        return new ForgedSlashArtPlan(
                key, coreVariant, primary, secondary);
    }

    /** Detailed tooltip for an inscribed SlashBlade stack. */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        ForgedSlashArtPlan plan = from(stack);
        if (plan != null) {
            appendPlanTooltip(plan, tooltip);
        }
    }

    /** Compact default tooltip; keyboard handling remains in the item/client layer. */
    public static void appendOrbTooltip(ItemStack stack, List<Component> tooltip) {
        ForgedSlashArtPlan plan = fromOrb(stack);
        if (plan == null) {
            int installed = ForgedSlashArtSpec.installedOrbComponentCount(stack);
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.forged.incomplete", installed, 3)
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            appendTitle(plan, tooltip);
        }
        ForgedSlashArtCore.appendBonusTooltip(ForgedSlashArtCore.orbVariant(stack), tooltip);
    }

    /** Alt-only details, including useful core identity on incomplete orbs. */
    public static void appendOrbDetails(ItemStack stack, List<Component> tooltip) {
        ForgedSlashArtPlan plan = fromOrb(stack);
        ForgedSlashArtCore.appendDetailsTooltip(ForgedSlashArtCore.orbVariant(stack), tooltip, plan != null);
        if (plan != null) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.forged.native_route")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.blade_tetra.forged.damage_context")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendTitle(
            ForgedSlashArtPlan plan, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.title",
                        Component.translatable(plan.primary().translationKey()),
                        Component.translatable(plan.secondary().translationKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void appendPlanTooltip(ForgedSlashArtPlan plan, List<Component> tooltip) {
        appendTitle(plan, tooltip);
        ForgedSlashArtCore.appendTooltip(plan.coreVariant(), tooltip);
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.native_route")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.forged.damage_context")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public enum Technique {
        JUDGEMENT_CUT("judgement_cut"),
        SAKURA_END("sakura_end"),
        VOID_SLASH("void_slash"),
        CIRCLE_SLASH("circle_slash"),
        DRIVE_VERTICAL("drive_vertical"),
        DRIVE_HORIZONTAL("drive_horizontal"),
        WAVE_EDGE("wave_edge"),
        PIERCING("piercing");

        private final String id;

        Technique(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "slash_art.slashblade." + id;
        }

        static Technique fromVariant(String variant) {
            String id = suffix(variant);
            for (Technique technique : values()) {
                if (technique.id.equals(id)) {
                    return technique;
                }
            }
            return null;
        }
    }

    private static String suffix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int separator = value.lastIndexOf('/');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }
}
