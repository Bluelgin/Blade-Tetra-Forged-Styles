package dev.bladetetra.item;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.combat.ConductiveBladeHandler;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.compat.ContractBladeCompat;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.easteregg.SoulResonance;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.forging.ImprintAffinity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Builds one ordered, deduplicated Alt view for Blade Tetra information. */
public final class BladeDetailTooltip {
    public static void append(ItemStack stack, List<Component> tooltip) {
        appendSection(tooltip, styleSection(stack));
        appendSection(tooltip, soulSection(stack));
        appendSection(tooltip, techniqueAndLegacySection(stack));
        appendSection(tooltip, craftSection(stack));
    }

    private static List<Component> styleSection(ItemStack stack) {
        BladeStyle style = StyleResolver.resolve(stack);
        return List.of(
                Component.translatable("tooltip.blade_tetra.section.style",
                                Component.translatable(style.getTranslationKey()),
                                Component.translatable(style.getRoleTranslationKey()))
                        .withStyle(ChatFormatting.GOLD),
                Component.translatable(style.getTranslationKey() + ".summary")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.blade_tetra.style.common_form.compact")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static List<Component> soulSection(ItemStack stack) {
        EnumSet<SoulLegacyState.Legacy> souls = SoulLegacyState.inscribed(stack);
        if (souls.isEmpty()) {
            if (ComponentEffectResolver.hasModule(
                    stack,
                    ModularSlashBladeItem.INSCRIPTION_SLOT,
                    ModularSlashBladeItem.SOUL_INSCRIPTION_MODULE)) {
                return List.of(Component.translatable(
                                "tooltip.blade_tetra.soul.proudsoul_ingot")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        List<SoulLegacyState.Legacy> ordered = souls.stream().toList();
        Component names = joinSoulNames(ordered);
        lines.add(Component.translatable(ordered.size() > 1
                        ? "tooltip.blade_tetra.section.soul.fusion"
                        : "tooltip.blade_tetra.section.soul.single", names)
                .withStyle(ordered.size() > 1
                        ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));

        if (SoulLegacyState.isBroken(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.soul.dormant.broken")
                    .withStyle(ChatFormatting.RED));
        } else {
            List<SoulLegacyState.Legacy> sleeping = ordered.stream()
                    .filter(soul -> !SoulLegacyState.isUnlocked(stack, soul)).toList();
            if (!sleeping.isEmpty()) {
                lines.add(Component.translatable("tooltip.blade_tetra.soul.dormant.unawakened",
                                joinSoulNames(sleeping))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        SoulResonance.appendTooltip(stack, lines);
        appendAwakenedAbilities(stack, ordered, lines);
        return lines;
    }

    private static void appendAwakenedAbilities(ItemStack stack,
            List<SoulLegacyState.Legacy> souls, List<Component> lines) {
        if (souls.contains(SoulLegacyState.Legacy.AKATSUKI)
                && AkatsukiAwakening.isActive(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.akatsuki.execution")
                    .withStyle(ChatFormatting.RED));
            if (ContractBladeCompat.hasAkatsuki(stack)) {
                lines.add(Component.translatable("tooltip.blade_tetra.akatsuki.spirit_bound")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
        if (souls.contains(SoulLegacyState.Legacy.KYOUKA)
                && KyoukaAwakening.isActive(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.kyouka.awakened")
                    .withStyle(ChatFormatting.AQUA));
        }
        if (souls.contains(SoulLegacyState.Legacy.SENBONZAKURA)
                && SenbonzakuraAwakening.isActive(stack)) {
            int marks = SenbonzakuraAwakening.petalMarks(stack);
            lines.add((marks > 0
                    ? Component.translatable(
                            "tooltip.blade_tetra.senbonzakura.awakened.with_marks",
                            marks, 3)
                    : Component.translatable("tooltip.blade_tetra.senbonzakura.awakened"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (souls.contains(SoulLegacyState.Legacy.RAIKIRI)
                && SoulLegacyState.isActive(stack, SoulLegacyState.Legacy.RAIKIRI)) {
            lines.add(Component.translatable("tooltip.blade_tetra.raikiri.unlocked")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private static List<Component> techniqueAndLegacySection(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        BladeTechniqueHandler.appendTooltip(stack, lines);

        List<Component> legacies = new ArrayList<>();
        if (BladeLegacyEasterEggs.isBanshoUnlocked(stack)) {
            legacies.add(Component.translatable("tooltip.blade_tetra.legacy.bansho"));
        }
        if (BladeLegacyEasterEggs.isBairenUnlocked(stack)) {
            legacies.add(Component.translatable("tooltip.blade_tetra.legacy.bairen"));
        }
        if (BladeLegacyEasterEggs.isShoshinUnlocked(stack)) {
            legacies.add(Component.translatable("tooltip.blade_tetra.legacy.shoshin"));
        }
        if (!legacies.isEmpty()) {
            lines.add(Component.translatable("tooltip.blade_tetra.legacy.summary",
                            join(legacies))
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (NbtSageEasterEgg.isUnlocked(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.legacy.special",
                            Component.translatable("tooltip.blade_tetra.legacy.nbt_sage"))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return lines;
    }

    private static List<Component> craftSection(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        FoxLegacyParts fox = FoxLegacyParts.fromStack(stack);
        var namedParts = dev.bladetetra.forging.NamedLegacyParts.fromStack(stack);
        if (namedParts.present()) {
            for (var part : java.util.List.of(
                    new Object[]{"saya", namedParts.saya()},
                    new Object[]{"hilt", namedParts.tsuba()})) {
                if (part[1] instanceof dev.bladetetra.forging.LegacyImprintKind kind)
                    lines.add(Component.translatable("tooltip.blade_tetra.named.part",
                            Component.translatable("screen.blade_tetra.imprint." + part[0]),
                            Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GRAY));
            }
            if (namedParts.completeSet() != null
                    && stack.getItem() instanceof ModularSlashBladeItem blade) {
                ImprintAffinity affinity = blade.getImprintAffinity(stack);
                if (affinity != null) {
                    lines.add(Component.translatable(
                                    "tooltip.blade_tetra.named.affinity",
                                    Component.translatable(namedParts.completeSet().translationKey()),
                                    Component.translatable("tooltip.blade_tetra.named.grade."
                                            + affinity.grade()),
                                    percent(affinity.attackRatio()),
                                    percent(affinity.durabilityRatio()))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        }
        for (var missing : dev.bladetetra.forging.NamedLegacyParts.missing(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.named.missing",
                    Component.translatable("screen.blade_tetra.imprint."
                            + (missing.part().equals("tsuba") ? "hilt" : missing.part())),
                    missing.id()).withStyle(ChatFormatting.RED));
        }
        if (fox.present() && !namedParts.present()) {
            FoxLegacyParts.Color complete = fox.completeSet();
            lines.add(Component.translatable(complete == FoxLegacyParts.Color.NONE
                            ? "tooltip.blade_tetra.fox.parts"
                            : "tooltip.blade_tetra.fox.complete",
                            foxPart(fox.saya()), foxPart(fox.tsuba()), foxPart(fox.tsuka()))
                    .withStyle(complete == FoxLegacyParts.Color.BLACK
                            ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY));
        }
        int conductivity = ConductiveBladeHandler.conductivityScore(stack);
        if (conductivity > 0) {
            lines.add(Component.translatable("tooltip.blade_tetra.craft.conductivity",
                            conductivity, 6)
                    .withStyle(ChatFormatting.AQUA));
        }
        SayaPresetSkin preset = SayaPresetSkin.fromStack(stack);
        SayaBannerSkin banner = SayaBannerSkin.fromStack(stack);
        if (preset.present()) {
            lines.add(Component.translatable("tooltip.blade_tetra.craft.saya",
                            preset.displayName())
                    .withStyle(ChatFormatting.AQUA));
        } else if (banner.present()) {
            lines.add(Component.translatable("tooltip.blade_tetra.craft.saya_banner",
                            Component.translatable("color.minecraft."
                                    + banner.baseColor().getName()), banner.patternCount())
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    private static Component foxPart(FoxLegacyParts.Color color) {
        return Component.translatable("tooltip.blade_tetra.fox." +
                color.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String percent(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", ratio * 100.0D);
    }

    private static Component joinSoulNames(List<SoulLegacyState.Legacy> souls) {
        return join(souls.stream().map(SoulLegacyState::displayName).toList());
    }

    private static Component join(List<Component> values) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(Component.literal(" · "));
            result.append(values.get(index));
        }
        return result;
    }

    private static void appendSection(List<Component> tooltip, List<Component> section) {
        if (section.isEmpty()) return;
        if (!tooltip.isEmpty()) tooltip.add(Component.empty());
        tooltip.addAll(section);
    }

    private BladeDetailTooltip() {
    }
}
