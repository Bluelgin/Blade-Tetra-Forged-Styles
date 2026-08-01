package dev.bladetetra.item;

import dev.bladetetra.combat.ComponentEffectResolver;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.visual.MaterialAppearance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Builds the visible blade name from the installed Tetra construction.
 *
 * <p>The blade material and module provide the stable base name. The remaining
 * modules contribute at most one epithet, preferring authored component
 * synergies over single-component descriptors. Nothing is persisted as name
 * NBT, so replacing a module immediately changes the generated name while an
 * anvil custom name continues to take precedence through {@link ItemStack}.</p>
 */
public final class BladeNameResolver {
    private static final String BASE_NAME = "item.blade_tetra.modular_slashblade";
    private static final String BASIC_PATTERN = "name.blade_tetra.pattern.basic";
    private static final String TITLED_PATTERN = "name.blade_tetra.pattern.titled";

    public static Component resolve(ItemStack stack) {
        switch (SoulLegacyState.active(stack)) {
            case AKATSUKI -> {
                return Component.translatable("name.blade_tetra.akatsuki");
            }
            case KYOUKA -> {
                return Component.translatable("name.blade_tetra.kyouka");
            }
            case SENBONZAKURA -> {
                return Component.translatable("name.blade_tetra.senbonzakura");
            }
            default -> {
            }
        }
        if (BladeLegacyEasterEggs.isRaikiriUnlocked(stack)) {
            return Component.translatable("name.blade_tetra.raikiri");
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ModularSlashBladeItem.BLADE_SLOT, Tag.TAG_STRING)) {
            return Component.translatable(BASE_NAME);
        }

        String material = MaterialAppearance.fromStack(stack).blade();
        Component materialName = Component.translatableWithFallback(
                "tetra.material." + material + ".prefix",
                humanize(material));
        Component bladeType = Component.translatable(resolveBladeType(tag));
        String epithet = resolveEpithet(stack);

        if (epithet == null) {
            return Component.translatable(BASIC_PATTERN, materialName, bladeType);
        }
        return Component.translatable(
                TITLED_PATTERN,
                materialName,
                bladeType,
                Component.translatable(epithet));
    }

    private static String resolveBladeType(CompoundTag tag) {
        return switch (tag.getString(ModularSlashBladeItem.BLADE_SLOT)) {
            case ModularSlashBladeItem.WAKIZASHI_MODULE ->
                    "name.blade_tetra.type.wakizashi";
            case ModularSlashBladeItem.NODACHI_MODULE ->
                    "name.blade_tetra.type.nodachi";
            default -> "name.blade_tetra.type.katana";
        };
    }

    private static String resolveEpithet(ItemStack stack) {
        if (BladeLegacyEasterEggs.isBanshoUnlocked(stack)) {
            return "name.blade_tetra.epithet.bansho";
        }
        if (BladeLegacyEasterEggs.isBairenUnlocked(stack)) {
            return "name.blade_tetra.epithet.bairen";
        }
        if (BladeLegacyEasterEggs.isShoshinUnlocked(stack)) {
            return "name.blade_tetra.epithet.shoshin";
        }
        // Awakening fundamentally changes the blade's state, so it outranks all
        // ordinary component pairings in the generated construction name.
        if (has(stack, ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE)) {
            return "name.blade_tetra.epithet.soulbound";
        }

        // Authored pairings take priority over individual component labels.
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE)
                && has(stack, ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.PRECISION_HABAKI_MODULE)) {
            return "name.blade_tetra.epithet.flash";
        }
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE)
                && has(stack, ModularSlashBladeItem.TSUKA_SLOT,
                ModularSlashBladeItem.SWIFT_TSUKA_MODULE)) {
            return "name.blade_tetra.epithet.gale";
        }
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.SPIRIT_SAYA_MODULE)
                && has(stack, ModularSlashBladeItem.TSUBA_SLOT,
                ModularSlashBladeItem.GUARD_TSUBA_MODULE)) {
            return "name.blade_tetra.epithet.soulwarden";
        }
        if (has(stack, ModularSlashBladeItem.TSUKA_SLOT, ModularSlashBladeItem.STABLE_TSUKA_MODULE)
                && has(stack, ModularSlashBladeItem.TSUBA_SLOT,
                ModularSlashBladeItem.GUARD_TSUBA_MODULE)) {
            return "name.blade_tetra.epithet.immovable";
        }
        if (has(stack, ModularSlashBladeItem.TSUBA_SLOT, ModularSlashBladeItem.LIGHT_TSUBA_MODULE)
                && has(stack, ModularSlashBladeItem.TSUKA_SLOT,
                ModularSlashBladeItem.SWIFT_TSUKA_MODULE)) {
            return "name.blade_tetra.epithet.swallow";
        }
        if (has(stack, ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.HEAVY_KASHIRA_MODULE)
                && has(stack, ModularSlashBladeItem.FULLER_SLOT,
                ModularSlashBladeItem.FULLER_MODULE)) {
            return "name.blade_tetra.epithet.mountainbreaker";
        }
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.SPIRIT_SAYA_MODULE)
                && has(stack, ModularSlashBladeItem.FULLER_SLOT,
                ModularSlashBladeItem.LIGHTWEIGHT_FULLER_MODULE)) {
            return "name.blade_tetra.epithet.shadow";
        }
        if (has(stack, ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.PRECISION_HABAKI_MODULE)
                && has(stack, ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.LIGHT_KASHIRA_MODULE)) {
            return "name.blade_tetra.epithet.autumnwater";
        }

        // If there is no pairing, expose only the most gameplay-defining part.
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE)) {
            return "name.blade_tetra.epithet.quickdraw";
        }
        if (has(stack, ModularSlashBladeItem.SAYA_SLOT, ModularSlashBladeItem.SPIRIT_SAYA_MODULE)) {
            return "name.blade_tetra.epithet.spirit";
        }
        if (has(stack, ModularSlashBladeItem.TSUBA_SLOT, ModularSlashBladeItem.GUARD_TSUBA_MODULE)) {
            return "name.blade_tetra.epithet.guard";
        }
        if (has(stack, ModularSlashBladeItem.TSUKA_SLOT, ModularSlashBladeItem.SWIFT_TSUKA_MODULE)) {
            return "name.blade_tetra.epithet.swift";
        }
        if (has(stack, ModularSlashBladeItem.TSUKA_SLOT, ModularSlashBladeItem.STABLE_TSUKA_MODULE)) {
            return "name.blade_tetra.epithet.stable";
        }
        if (has(stack, ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.PRECISION_HABAKI_MODULE)) {
            return "name.blade_tetra.epithet.precision";
        }
        if (has(stack, ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.REINFORCED_HABAKI_MODULE)) {
            return "name.blade_tetra.epithet.enduring";
        }
        if (has(stack, ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.HEAVY_KASHIRA_MODULE)) {
            return "name.blade_tetra.epithet.heavy";
        }
        if (has(stack, ModularSlashBladeItem.TSUBA_SLOT, ModularSlashBladeItem.LIGHT_TSUBA_MODULE)
                || has(stack, ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.LIGHT_KASHIRA_MODULE)
                || has(stack, ModularSlashBladeItem.FULLER_SLOT,
                ModularSlashBladeItem.LIGHTWEIGHT_FULLER_MODULE)) {
            return "name.blade_tetra.epithet.light";
        }
        if (has(stack, ModularSlashBladeItem.FULLER_SLOT, ModularSlashBladeItem.FULLER_MODULE)) {
            return "name.blade_tetra.epithet.reinforced";
        }
        return null;
    }

    private static boolean has(ItemStack stack, String slot, String module) {
        return ComponentEffectResolver.hasModule(stack, slot, module);
    }

    private static String humanize(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("[_.\\-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.isEmpty() ? "Unknown" : result.toString();
    }

    private BladeNameResolver() {
    }
}
