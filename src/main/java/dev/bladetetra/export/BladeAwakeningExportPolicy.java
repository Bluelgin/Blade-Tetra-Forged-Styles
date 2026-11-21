package dev.bladetetra.export;

import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.SoulLegacyState;
import dev.bladetetra.easteregg.SoulSkillState;
import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.nbt.CompoundTag;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Intentionally excludes ONLY awakening-specific progression, equipment and visuals
 * from a PRIVATE snapshot. Ordinary non-portable SA/SE, traditions and other module
 * mechanics remain subject to their usual strict preflight; they are never lost.
 */
final class BladeAwakeningExportPolicy {
    record Projection(BladeExportSnapshot snapshot, ItemStack blade, List<String> omitted) {
        Projection { omitted = List.copyOf(omitted); }
        boolean omitsAwakening() { return !omitted.isEmpty(); }
    }

    private static final List<String> AWAKENING_PREFIXES = List.of(
            "blade_tetra_akatsuki_", "blade_tetra_kyouka_",
            "blade_tetra_senbonzakura_", "blade_tetra_raikiri_",
            "blade_tetra_awakened_", "blade_tetra_selected_soul");

    static Projection project(BladeExportSnapshot original, ItemStack blade) {
        ItemStack exported = blade.copy();
        CompoundTag tag = exported.getTag();
        if (tag == null) return new Projection(original, exported, List.of());

        boolean namedInscription = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE.equals(
                tag.getString(ModularSlashBladeItem.INSCRIPTION_SLOT));
        boolean unlockedAkatsuki = AkatsukiAwakening.isUnlocked(exported);
        var omitted = new ArrayList<String>();
        for (var legacy : SoulLegacyState.inscribed(exported)) omitted.add(legacy.name());

        // Dormant/unknown soul variants still must be recognized and removed.
        if (namedInscription && omitted.isEmpty()) omitted.add("AWAKENED_INSCRIPTION");
        boolean hasSavedAwakening = tag.getAllKeys().stream().anyMatch(
                key -> AWAKENING_PREFIXES.stream().anyMatch(key::startsWith));
        if (hasSavedAwakening && omitted.isEmpty()) omitted.add("AWAKENING_PROGRESS");
        if (omitted.isEmpty()) return new Projection(original, exported, List.of());

        // Some early awakened saves predate the explicit soul-contract
        // provenance marker. A magically bewitched state without that marker
        // cannot be presumed to have been an independent blade property.
        boolean hadPreviousDefault = tag.getBoolean("blade_tetra_soul_contract_previous_default");
        if (namedInscription && !hadPreviousDefault) {
            exported.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> state.setDefaultBewitched(false));
        }
        if (namedInscription) {
            tag.remove(ModularSlashBladeItem.INSCRIPTION_SLOT);
            String prefix = ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE;
            for (String key : List.copyOf(tag.getAllKeys())) {
                if (key.equals(prefix) || key.startsWith(prefix + "_")) tag.remove(key);
            }
        }
        // Strip only awakening-scoped data. Other build modules, custom styles,
        // names and named-part provenance are intentionally kept intact.
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (AWAKENING_PREFIXES.stream().anyMatch(key::startsWith)) tag.remove(key);
        }
        tag.remove(SoulSkillState.BLADE_ID);
        tag.remove(SoulSkillState.SELECTED);

        // Akatsuki's awakening grants an unmarked vanilla Power I enchantment.
        // Its provenance cannot be established afterwards; omit the ambiguous
        // level-I copy and explicitly warn rather than smuggling a soul perk.
        if (unlockedAkatsuki) {
            var enchantments = EnchantmentHelper.getEnchantments(exported);
            if (enchantments.getOrDefault(Enchantments.POWER_ARROWS, 0) == 1) {
                enchantments.remove(Enchantments.POWER_ARROWS);
                EnchantmentHelper.setEnchantments(enchantments, exported);
                omitted.add("AKATSUKI_POWER_I");
            }
        }

        // Runs normal Tetra -> SlashBlade state synchronization on a COPY,
        // including removal of soul-contract-derived enchantment/bewitching,
        // refresh of durability, base attack, combo and inherited abilities.
        BladeExportSnapshot normalized = BladeExportSnapshot.captureOwnedStack(exported);
        if (!normalized.inscriptions().isEmpty()) {
            throw new IllegalArgumentException("Awakening state remained after projection");
        }
        return new Projection(normalized, exported, omitted);
    }

    private BladeAwakeningExportPolicy() {}
}
