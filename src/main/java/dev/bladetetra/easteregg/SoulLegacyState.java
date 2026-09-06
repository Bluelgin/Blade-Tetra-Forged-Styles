package dev.bladetetra.easteregg;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

/** Resolves the one- or two-soul inscription currently hosted by a blade. */
public final class SoulLegacyState {
    public static final String RAIKIRI_VARIANT =
            "awakened_soul_inscription/raikiri";

    public enum Legacy {
        NONE,
        AKATSUKI,
        KYOUKA,
        SENBONZAKURA,
        RAIKIRI
    }

    /** Returns a stable primary soul for names and single-palette rendering. */
    public static Legacy active(ItemStack stack) {
        for (Legacy legacy : new Legacy[] {
                Legacy.AKATSUKI, Legacy.KYOUKA,
                Legacy.SENBONZAKURA, Legacy.RAIKIRI }) {
            if (isActive(stack, legacy)) return legacy;
        }
        return Legacy.NONE;
    }

    public static EnumSet<Legacy> inscribed(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE.equals(
                tag.getString(ModularSlashBladeItem.INSCRIPTION_SLOT))) {
            return EnumSet.noneOf(Legacy.class);
        }
        String key = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material";
        if (!tag.contains(key, Tag.TAG_STRING)) return EnumSet.noneOf(Legacy.class);
        return switch (tag.getString(key)) {
            case "awakened_soul_inscription/blood_crystal" -> of(Legacy.AKATSUKI);
            case "awakened_soul_inscription/proudsoul_sphere" -> of(Legacy.KYOUKA);
            case "awakened_soul_inscription/sakura_crystal" -> of(Legacy.SENBONZAKURA);
            case RAIKIRI_VARIANT -> of(Legacy.RAIKIRI);
            case "awakened_soul_inscription/akatsuki_kyouka" ->
                    of(Legacy.AKATSUKI, Legacy.KYOUKA);
            case "awakened_soul_inscription/akatsuki_senbonzakura" ->
                    of(Legacy.AKATSUKI, Legacy.SENBONZAKURA);
            case "awakened_soul_inscription/akatsuki_raikiri" ->
                    of(Legacy.AKATSUKI, Legacy.RAIKIRI);
            case "awakened_soul_inscription/kyouka_senbonzakura" ->
                    of(Legacy.KYOUKA, Legacy.SENBONZAKURA);
            case "awakened_soul_inscription/kyouka_raikiri" ->
                    of(Legacy.KYOUKA, Legacy.RAIKIRI);
            case "awakened_soul_inscription/senbonzakura_raikiri" ->
                    of(Legacy.SENBONZAKURA, Legacy.RAIKIRI);
            default -> EnumSet.noneOf(Legacy.class);
        };
    }

    public static boolean hasSingleInscription(ItemStack stack, Legacy legacy) {
        EnumSet<Legacy> souls = inscribed(stack);
        return souls.size() == 1 && souls.contains(legacy);
    }

    public static boolean isActive(ItemStack stack, Legacy legacy) {
        return legacy != Legacy.NONE
                && stack.getItem() instanceof ModularSlashBladeItem
                && !isBroken(stack)
                && inscribed(stack).contains(legacy)
                && isUnlocked(stack, legacy);
    }

    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        EnumSet<Legacy> souls = inscribed(stack);
        if (souls.isEmpty()) return;
        List<Legacy> ordered = souls.stream().filter(value -> value != Legacy.NONE).toList();
        Component names = ordered.size() == 2
                ? Component.translatable("tooltip.blade_tetra.soul.pair",
                        displayName(ordered.get(0)), displayName(ordered.get(1)))
                : displayName(ordered.get(0));
        tooltip.add(Component.translatable(ordered.size() == 2
                        ? "tooltip.blade_tetra.soul.fusion"
                        : "tooltip.blade_tetra.soul.single", names)
                .withStyle(ordered.size() == 2
                        ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_PURPLE));
        long awake = ordered.stream().filter(legacy -> isActive(stack, legacy)).count();
        if (awake < ordered.size()) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.soul.dormant")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static Component displayName(Legacy legacy) {
        return Component.translatable("tooltip.blade_tetra.soul."
                + legacy.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isUnlocked(ItemStack stack, Legacy legacy) {
        return switch (legacy) {
            case AKATSUKI -> AkatsukiAwakening.isUnlocked(stack);
            case KYOUKA -> KyoukaAwakening.isUnlocked(stack);
            case SENBONZAKURA -> SenbonzakuraAwakening.isUnlocked(stack);
            case RAIKIRI -> BladeLegacyEasterEggs.isRaikiriUnlocked(stack);
            default -> false;
        };
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.isBroken()).orElse(false);
    }

    private static EnumSet<Legacy> of(Legacy... values) {
        EnumSet<Legacy> result = EnumSet.noneOf(Legacy.class);
        java.util.Collections.addAll(result, values);
        return result;
    }

    private SoulLegacyState() {
    }
}
