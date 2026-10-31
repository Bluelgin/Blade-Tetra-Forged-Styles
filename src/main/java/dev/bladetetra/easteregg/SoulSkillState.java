package dev.bladetetra.easteregg;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;
import java.util.Set;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy;

/** Combat selection only. The stable primary soul still owns item names and artwork. */
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

    public static boolean enabled(ItemStack blade, Legacy soul) {
        if (!SoulLegacyState.isActive(blade, soul)) return false;
        if (!AwakenedBladeCombat.replacesLegacy() || !fusion(blade)) return true;
        return selected(blade) == soul && allowsStyle(soul, StyleResolver.resolve(blade));
    }

    public static BladeStyle requiredStyle(Legacy soul) {
        return soul == Legacy.KYOUKA ? BladeStyle.IAIDO : BladeStyle.RENGEKI;
    }

    private SoulSkillState() {}
}
