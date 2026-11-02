package dev.bladetetra.item;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.BladeTechniqueHandler;
import dev.bladetetra.combat.ConductiveBladeHandler;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.easteregg.AwakenedBladeCombat;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.NbtSageEasterEgg;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.easteregg.SoulResonance;
import dev.bladetetra.easteregg.SoulSkillState;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import dev.bladetetra.forging.FoxLegacyParts;
import dev.bladetetra.forging.LegacyFusion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
        return List.of(Component.translatable("tooltip.blade_tetra.section.style",
                Component.translatable(style.getTranslationKey()),
                Component.translatable(style.getRoleTranslationKey())).withStyle(ChatFormatting.GOLD));
    }

    private static List<Component> soulSection(ItemStack stack) {
        var souls = SoulLegacyState.inscribed(stack);
        if (souls.isEmpty()) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(souls.size() > 1
                ? "tooltip.blade_tetra.section.soul.fusion" : "tooltip.blade_tetra.section.soul.single",
                joinSoulNames(souls.stream().toList())).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (SoulLegacyState.isBroken(stack)) {
            lines.add(Component.translatable("tooltip.blade_tetra.soul.dormant.broken").withStyle(ChatFormatting.RED));
            return lines;
        }
        var sleeping = souls.stream().filter(soul -> !SoulLegacyState.isUnlocked(stack, soul)).toList();
        if (!sleeping.isEmpty()) lines.add(Component.translatable("tooltip.blade_tetra.soul.dormant.unawakened",
                joinSoulNames(sleeping)).withStyle(ChatFormatting.DARK_GRAY));
        var selected = SoulSkillState.fusion(stack) ? SoulSkillState.selected(stack) : SoulLegacyState.active(stack);
        if (selected != SoulLegacyState.Legacy.NONE) {
            lines.add(Component.translatable("tooltip.blade_tetra.soul.selected",
                    SoulLegacyState.displayName(selected)).withStyle(ChatFormatting.GOLD));
            if ((SoulSkillState.fusion(stack) || StyleResolver.resolve(stack) == BladeStyle.CUSTOM)
                    && !SoulSkillState.allowsStyle(stack, selected)) lines.add(Component.translatable(
                    "hud.blade_tetra.soul.requires", Component.translatable(
                            SoulSkillState.requiredStyle(selected).getTranslationKey())).withStyle(ChatFormatting.DARK_GRAY));
            else if (SoulLegacyState.isUnlocked(stack, selected)) lines.add(Component.translatable(
                    "tooltip.blade_tetra.soul.brief." + selected.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (SoulSkillState.customLinked(stack)) lines.add(Component.translatable(
                "tooltip.blade_tetra.soul.custom_linked").withStyle(ChatFormatting.AQUA));
        if (!AwakenedBladeCombat.replacesLegacy()) SoulResonance.appendTooltip(stack, lines);
        return lines;
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
        var namedBlade = dev.bladetetra.forging.NamedLegacyParts.bladeFromStack(stack);
        if (namedParts.present() || namedBlade != null) {
            for (var part : java.util.List.of(
                    new Object[]{"blade", namedBlade},
                    new Object[]{"saya", namedParts.saya()},
                    new Object[]{"hilt", namedParts.tsuba()})) {
                if (part[1] instanceof dev.bladetetra.forging.LegacyImprintKind kind)
                    lines.add(Component.translatable("tooltip.blade_tetra.named.part",
                            Component.translatable("screen.blade_tetra.imprint." + part[0]),
                            Component.translatable(kind.translationKey())).withStyle(ChatFormatting.GRAY));
            }
            LegacyFusion fusion = LegacyFusion.installed(namedParts);
            if (fusion != null) {
                boolean activeFusion = LegacyFusion.active(stack) == fusion;
                Component name = Component.translatable(
                        "tooltip.blade_tetra.legacy_fusion." + fusion.id());
                lines.add(Component.translatable(
                                activeFusion
                                        ? "tooltip.blade_tetra.legacy_fusion.active"
                                        : "tooltip.blade_tetra.legacy_fusion.candidate",
                                name)
                        .withStyle(activeFusion
                                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));

            }
        }
        var candidate = dev.bladetetra.combat.ProgrammaticFusionPlan.candidate(stack);
        if (candidate != null && LegacyFusion.active(stack) == null) {
            lines.add(Component.translatable(dev.bladetetra.combat.ProgrammaticFusionPlan.from(stack) == null
                    ? "tooltip.blade_tetra.fusion.available" : "tooltip.blade_tetra.fusion.confirmed")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
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
