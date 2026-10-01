package dev.bladetetra.combat;

import dev.bladetetra.item.ForgedSlashArtOrbItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.data.MaterialData;

/** Shared by tooltips, Tetra previews and the release-time damage snapshot. */
public final class ForgedSlashArtCore {
    public static final float MAX_BONUS = 0.15F;

    public static String materialKey(String variant) {
        if (variant == null) return "";
        return variant.startsWith("sa_core/") ? variant.substring(8) : variant;
    }

    public static String orbVariant(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ForgedSlashArtOrbItem)
                || !ComponentEffectResolver.hasModule(stack, ForgedSlashArtOrbItem.SA_CORE_SLOT,
                        ForgedSlashArtOrbItem.SA_CORE_MODULE)) return "";
        return ComponentEffectResolver.moduleVariant(stack, ForgedSlashArtOrbItem.SA_CORE_SLOT);
    }

    public static Component displayName(String variant) {
        return Component.translatable("tetra.material." + materialKey(variant));
    }

    public static float bonus(String variant) {
        String key = materialKey(variant);
        if (key.isBlank() || DataManager.instance == null
                || DataManager.instance.materialData == null
                || DataManager.instance.materialData.getData() == null) return 0;
        // These public fields and the material store are unchanged in 6.3, 6.9,
        // 6.12 and 6.17. Read live data: /reload and MMT overrides stay authoritative.
        MaterialData material = DataManager.instance.materialData.getData().values().stream()
                .filter(data -> data != null && key.equals(data.key)).findFirst().orElse(null);
        return material == null || material.primary == null ? 0 : bonusFromPrimary(material.primary);
    }

    /** Stone (2) is neutral; iron (5) 7.5%, diamond (6) 10%, 8+ capped at 15%. */
    public static float bonusFromPrimary(double primary) {
        if (!Double.isFinite(primary)) return 0;
        return (float) Math.max(0, Math.min(MAX_BONUS, (primary - 2) * 0.025));
    }

    public static float phaseScale(boolean secondary, float bonus) {
        float bounded = Float.isFinite(bonus) ? Math.max(0, Math.min(MAX_BONUS, bonus)) : 0;
        return (secondary ? ForgedSlashArtDamageBalance.SECONDARY_SCALE
                : ForgedSlashArtDamageBalance.PRIMARY_SCALE) * (1 + bounded);
    }

    public static String percent(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100);
    }

    public static void appendTooltip(String variant, java.util.List<Component> tooltip) {
        appendBonusTooltip(variant, tooltip);
        appendDetailsTooltip(variant, tooltip, true);
    }

    public static void appendBonusTooltip(String variant, java.util.List<Component> tooltip) {
        if (variant == null || variant.isBlank()) return;
        tooltip.add(Component.translatable("tooltip.blade_tetra.forged.core_bonus", percent(bonus(variant)))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public static void appendDetailsTooltip(String variant, java.util.List<Component> tooltip,
            boolean complete) {
        if (variant == null || variant.isBlank()) return;
        appendDetailsTooltip(variant, bonus(variant), tooltip, complete);
    }

    /** Pure formatting separated from the reloadable material lookup. */
    static void appendDetailsTooltip(String variant, float bonus,
            java.util.List<Component> tooltip, boolean complete) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.forged.core", displayName(variant))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (complete) tooltip.add(Component.translatable("tooltip.blade_tetra.forged.damage_scales",
                        percent(phaseScale(false, bonus)), percent(phaseScale(true, bonus)))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    private ForgedSlashArtCore() {}
}
