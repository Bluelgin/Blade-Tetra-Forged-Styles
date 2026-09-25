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
        Technique secondary,
        Modifier modifier) {

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
                : compose(spec.coreVariant(), spec.primary(), spec.secondary(), spec.modifier());
    }

    static ForgedSlashArtPlan compose(String coreVariant,
            Technique primary, Technique secondary, Modifier modifier) {
        if (coreVariant == null || coreVariant.isBlank()
                || primary == null || secondary == null || modifier == null) {
            return null;
        }

        String key = coreVariant + "|" + primary.id() + "|" + secondary.id()
                + "|" + modifier.id();
        return new ForgedSlashArtPlan(
                key, coreVariant, primary, secondary, modifier);
    }

    /** Detailed tooltip for an inscribed SlashBlade stack. */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        ForgedSlashArtPlan plan = from(stack);
        if (plan != null) {
            appendPlanTooltip(plan, tooltip);
        }
    }

    /** Detailed tooltip for the editable Tetra carrier. */
    public static void appendOrbTooltip(ItemStack stack, List<Component> tooltip) {
        ForgedSlashArtPlan plan = fromOrb(stack);
        if (plan == null) {
            int installed = ForgedSlashArtSpec.installedOrbComponentCount(stack);
            tooltip.add(Component.translatable(
                            "tooltip.blade_tetra.forged.incomplete", installed, 4)
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        appendPlanTooltip(plan, tooltip);
    }

    private static void appendPlanTooltip(
            ForgedSlashArtPlan plan, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.title",
                        Component.translatable(plan.primary().translationKey()),
                        Component.translatable(plan.secondary().translationKey()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.core", plan.coreDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.modifier",
                        Component.translatable(plan.modifier().translationKey()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.forged.native_route")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private String coreDisplayName() {
        String value = coreVariant;
        int separator = value.lastIndexOf('/');
        if (separator >= 0 && separator + 1 < value.length()) {
            value = value.substring(separator + 1);
        }
        int namespace = value.lastIndexOf(':');
        if (namespace >= 0 && namespace + 1 < value.length()) {
            value = value.substring(namespace + 1);
        }
        value = value.replace('_', ' ');
        if (value.isBlank()) {
            return "Core";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
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

    /**
     * Modifiers now describe routing cadence only. They never synthesize damage
     * or replace SlashBlade's native combat callbacks.
     */
    public enum Modifier {
        BALANCED("balanced", 1, false),
        CONDENSED("condensed", 0, false),
        SHATTER("shatter", 2, false),
        SPREAD("spread", 1, false),
        ECHO("echo", 1, true),
        HASTE("haste", 0, false);

        private final String id;
        private final int spliceTailTicks;
        private final boolean repeatsSecondary;

        Modifier(String id, int spliceTailTicks, boolean repeatsSecondary) {
            this.id = id;
            this.spliceTailTicks = spliceTailTicks;
            this.repeatsSecondary = repeatsSecondary;
        }

        public String id() {
            return id;
        }

        int spliceTailTicks() {
            return spliceTailTicks;
        }

        boolean repeatsSecondary() {
            return repeatsSecondary;
        }

        public String translationKey() {
            return "tooltip.blade_tetra.forged.modifier." + id;
        }

        static Modifier fromVariant(String variant) {
            String id = suffix(variant);
            for (Modifier modifier : values()) {
                if (modifier.id.equals(id)) {
                    return modifier;
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
