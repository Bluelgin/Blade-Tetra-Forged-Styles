package dev.bladetetra.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Compiled runtime description of a player-authored Slash Art.
 *
 * <p>The editable composition lives on a Tetra Slash Art Orb. A blade receives a
 * versioned {@link ForgedSlashArtSpec} snapshot; this class compiles that snapshot
 * into bounded runtime damage, timing and topology.</p>
 */
public record ForgedSlashArtPlan(
        String key,
        String coreVariant,
        double powerBudget,
        Technique primary,
        Technique secondary,
        Modifier modifier,
        int primaryCount,
        int secondaryCount,
        int secondaryCycles,
        double primaryDamagePerHit,
        double secondaryDamagePerHit,
        int primaryDelayTicks,
        int secondaryDelayTicks,
        int echoSpacingTicks,
        double angleScale) {
    private static final double PRIMARY_SHARE = 0.55D;
    private static final double SECONDARY_SHARE = 0.45D;

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
        double power = corePowerFor(coreVariant);
        int primaryCount = modifier.modifyCount(primary.baseCount());
        int secondaryCount = modifier.modifyCount(secondary.baseCount());
        int cycles = modifier.secondaryCycles();
        double effective = power * modifier.damageEfficiency();
        double primaryDamage = effective * PRIMARY_SHARE / primaryCount;
        double secondaryDamage = effective * SECONDARY_SHARE
                / (secondaryCount * cycles);
        int primaryDelay = modifier.scaleTicks(primary.attackDelayTicks());
        int handoff = modifier.scaleTicks(primary.handoffDelayTicks());
        int secondaryDelay = Math.max(primaryDelay + 2, handoff);
        String key = coreVariant + "|" + primary.id() + "|" + secondary.id()
                + "|" + modifier.id();
        return new ForgedSlashArtPlan(key, coreVariant, power, primary, secondary,
                modifier, primaryCount, secondaryCount, cycles,
                primaryDamage, secondaryDamage, primaryDelay, secondaryDelay,
                modifier.echoSpacingTicks(), modifier.angleScale());
    }

    static double corePowerFor(String variant) {
        String value = variant == null ? "" : variant.toLowerCase(Locale.ROOT);
        if (value.contains("netherite")) {
            return 1.45D;
        }
        if (value.contains("diamond")) {
            return 1.30D;
        }
        if (value.contains("emerald")) {
            return 1.18D;
        }
        if (value.contains("obsidian")) {
            return 1.15D;
        }
        if (value.contains("amethyst") || value.contains("quartz")) {
            return 1.08D;
        }
        if (value.contains("gold")) {
            return 1.00D;
        }
        if (value.contains("iron") || value.contains("steel")) {
            return 0.92D;
        }
        if (value.contains("copper")) {
            return 0.82D;
        }
        if (value.contains("stone") || value.contains("cobble")) {
            return 0.72D;
        }
        // Modded Tetra minerals remain usable without a compatibility catalog.
        return 0.95D;
    }

    public ResourceLocation primaryMotionId() {
        return ModComboStates.getForgedMotion(primary, modifier.hasteAnimation());
    }

    public int totalHits() {
        return primaryCount + secondaryCount * secondaryCycles;
    }

    public double effectiveDamageBudget() {
        return primaryDamagePerHit * primaryCount
                + secondaryDamagePerHit * secondaryCount * secondaryCycles;
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
                        "tooltip.blade_tetra.forged.stats",
                        String.format(Locale.ROOT, "%.2f", plan.powerBudget()),
                        plan.totalHits())
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
        JUDGEMENT_CUT("judgement_cut",
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 16, 19),
        SAKURA_END("sakura_end",
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 1, 8),
        VOID_SLASH("void_slash",
                ProgrammaticFusionProfile.Response.VOID_TRIDENT, 16, 19),
        CIRCLE_SLASH("circle_slash",
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 4, 10),
        DRIVE_VERTICAL("drive_vertical",
                ProgrammaticFusionProfile.Response.VERTICAL_DRIVE, 3, 7),
        DRIVE_HORIZONTAL("drive_horizontal",
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 3, 7),
        WAVE_EDGE("wave_edge",
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 3, 10),
        PIERCING("piercing",
                ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 22, 25);

        private final String id;
        private final ProgrammaticFusionProfile.Response response;
        private final int attackDelayTicks;
        private final int handoffDelayTicks;

        Technique(String id, ProgrammaticFusionProfile.Response response,
                int attackDelayTicks, int handoffDelayTicks) {
            this.id = id;
            this.response = response;
            this.attackDelayTicks = attackDelayTicks;
            this.handoffDelayTicks = handoffDelayTicks;
        }

        public String id() {
            return id;
        }

        public ProgrammaticFusionProfile.Response response() {
            return response;
        }

        public int baseCount() {
            return response.projectileCount();
        }

        int attackDelayTicks() {
            return attackDelayTicks;
        }

        int handoffDelayTicks() {
            return handoffDelayTicks;
        }

        public String translationKey() {
            return "tooltip.blade_tetra.forged.technique." + id;
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

    public enum Modifier {
        BALANCED("balanced", 1.0D, 1.00D, 1.00D, 1, 1.00D, 8, false),
        CONDENSED("condensed", 0.5D, 1.06D, 0.70D, 1, 1.08D, 8, false),
        SHATTER("shatter", 2.0D, 0.84D, 1.20D, 1, 1.00D, 8, false),
        SPREAD("spread", 1.0D, 0.90D, 1.80D, 1, 1.00D, 8, false),
        ECHO("echo", 1.0D, 0.88D, 1.00D, 2, 1.00D, 8, false),
        HASTE("haste", 1.0D, 0.92D, 1.00D, 1, 0.72D, 6, true);

        private final String id;
        private final double countScale;
        private final double damageEfficiency;
        private final double angleScale;
        private final int secondaryCycles;
        private final double timingScale;
        private final int echoSpacingTicks;
        private final boolean hasteAnimation;

        Modifier(String id, double countScale, double damageEfficiency,
                double angleScale, int secondaryCycles, double timingScale,
                int echoSpacingTicks, boolean hasteAnimation) {
            this.id = id;
            this.countScale = countScale;
            this.damageEfficiency = damageEfficiency;
            this.angleScale = angleScale;
            this.secondaryCycles = secondaryCycles;
            this.timingScale = timingScale;
            this.echoSpacingTicks = echoSpacingTicks;
            this.hasteAnimation = hasteAnimation;
        }

        public String id() {
            return id;
        }

        int modifyCount(int base) {
            return Math.max(1, Math.min(8,
                    (int) Math.ceil(Math.max(1, base) * countScale)));
        }

        double damageEfficiency() {
            return damageEfficiency;
        }

        double angleScale() {
            return angleScale;
        }

        int secondaryCycles() {
            return secondaryCycles;
        }

        int scaleTicks(int ticks) {
            return Math.max(1, (int) Math.round(ticks * timingScale));
        }

        int echoSpacingTicks() {
            return echoSpacingTicks;
        }

        public boolean hasteAnimation() {
            return hasteAnimation;
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
