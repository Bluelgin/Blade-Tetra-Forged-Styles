package dev.bladetetra.easteregg;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.customstyle.ComboCompatibilityResolver;
import net.minecraft.resources.ResourceLocation;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;
import java.util.Set;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy;

/** Separates active-art selection, installed-style eligibility and ordinary-action attribution. */
public final class SoulSkillState {
    public static final String SELECTED = "blade_tetra_selected_soul";
    public static final String BLADE_ID = "blade_tetra_awakened_v2_id";
    public static final int SWITCH_TICKS = 20;

    public static boolean fusion(ItemStack blade) {
        return blade.getItem() instanceof ModularSlashBladeItem
                && SoulLegacyState.inscribed(blade).size() == 2;
    }

    public static Legacy choose(Set<Legacy> souls, int saved, Legacy fallback) {
        if (saved > 0 && saved < Legacy.values().length && souls.contains(Legacy.values()[saved]))
            return Legacy.values()[saved];
        return souls.contains(fallback) ? fallback
                : souls.stream().filter(s -> s != Legacy.NONE).sorted().findFirst().orElse(Legacy.NONE);
    }

    public static Legacy selected(ItemStack blade) {
        if (!AwakenedBladeCombat.replacesLegacy() || !fusion(blade)) return SoulLegacyState.active(blade);
        return choose(SoulLegacyState.inscribed(blade),
                blade.hasTag() ? blade.getTag().getInt(SELECTED) : 0, SoulLegacyState.active(blade));
    }

    public static boolean allowsStyle(Legacy soul, BladeStyle style) {
        return switch (soul) {
            case KYOUKA -> style == BladeStyle.IAIDO;
            case SENBONZAKURA -> style == BladeStyle.RENGEKI;
            case AKATSUKI, RAIKIRI -> true;
            default -> false;
        };
    }

    /** Qualification comes from the installed, playable sequence; shared moves confer no family. */
    public static boolean allowsStyle(ItemStack blade, Legacy soul) {
        BladeStyle style = StyleResolver.resolve(blade);
        if (style != BladeStyle.CUSTOM || (soul != Legacy.KYOUKA && soul != Legacy.SENBONZAKURA))
            return allowsStyle(soul, style);
        var spec = CustomStyleSpec.decode(
                blade.getTag().getCompound(CustomStyleSpec.STACK_KEY));
        return ComboCompatibilityResolver.playable(spec)
                && spec.actions().stream().anyMatch(a -> SoulActionAffinity.soul(a) == soul);
    }

    public static boolean customLinked(ItemStack blade) {
        var souls = SoulLegacyState.inscribed(blade);
        return StyleResolver.resolve(blade) == BladeStyle.CUSTOM
                && souls.contains(Legacy.KYOUKA) && souls.contains(Legacy.SENBONZAKURA);
    }

    /** Persistent controllers stay attached across actions. Active arts still use enabled(). */
    public static boolean available(ItemStack blade, Legacy soul) {
        if (!SoulLegacyState.isActive(blade, soul)) return false;
        if (AwakenedBladeCombat.replacesLegacy() && customLinked(blade)) return allowsStyle(blade, soul);
        return enabled(blade, soul);
    }

    /** Inspect the captured source action, never the player's next action for delayed damage. */
    public static boolean acceptsAction(ItemStack blade, Legacy soul, ResourceLocation action) {
        return available(blade, soul) && (StyleResolver.resolve(blade) != BladeStyle.CUSTOM
                || SoulActionAffinity.soul(action) == soul);
    }

    public static boolean enabled(ItemStack blade, Legacy soul) {
        if (!SoulLegacyState.isActive(blade, soul)) return false;
        if (!AwakenedBladeCombat.replacesLegacy()) return true;
        if (StyleResolver.resolve(blade) == BladeStyle.CUSTOM && !allowsStyle(blade, soul)) return false;
        if (!fusion(blade)) return true;
        return selected(blade) == soul && allowsStyle(blade, soul);
    }

    public static BladeStyle requiredStyle(Legacy soul) {
        return soul == Legacy.KYOUKA ? BladeStyle.IAIDO : BladeStyle.RENGEKI;
    }

    private SoulSkillState() {}
}
