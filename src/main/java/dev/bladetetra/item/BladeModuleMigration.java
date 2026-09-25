package dev.bladetetra.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;

import java.util.ArrayList;
import java.util.List;

/** Owns one-way migration of legacy Alpha module/NBT layouts. */
final class BladeModuleMigration {
    private static final String MODULE_SCHEMA_KEY = "blade_tetra_module_schema";
    private static final int MODULE_SCHEMA_VERSION = 2;

    private static final LegacyModuleMapping[] LEGACY_MAPPINGS = {
            new LegacyModuleMapping(
                    "sword/blade", ModularSlashBladeItem.BLADE_SLOT,
                    ModularSlashBladeItem.BLADE_MODULE, "katana_blade/", "iron"),
            new LegacyModuleMapping(
                    "sword/hilt", ModularSlashBladeItem.TSUKA_SLOT,
                    ModularSlashBladeItem.TSUKA_MODULE, "wrapped_tsuka/", "stick"),
            new LegacyModuleMapping(
                    "sword/guard", ModularSlashBladeItem.TSUBA_SLOT,
                    ModularSlashBladeItem.TSUBA_MODULE, "simple_tsuba/", "iron"),
            new LegacyModuleMapping(
                    "sword/pommel", ModularSlashBladeItem.KASHIRA_SLOT,
                    ModularSlashBladeItem.KASHIRA_MODULE, "simple_kashira/", "iron"),
            new LegacyModuleMapping(
                    "sword/fuller", ModularSlashBladeItem.FULLER_SLOT,
                    ModularSlashBladeItem.FULLER_MODULE, "reinforced_fuller/", "iron")
    };

    static void markCurrent(ItemStack stack) {
        stack.getOrCreateTag().putInt(MODULE_SCHEMA_KEY, MODULE_SCHEMA_VERSION);
    }

    static boolean migrate(ItemStack stack, Runnable invalidateCaches) {
        CompoundTag tag = stack.getOrCreateTag();
        boolean changed = false;

        for (LegacyModuleMapping mapping : LEGACY_MAPPINGS) {
            if (tag.contains(mapping.oldSlot(), Tag.TAG_STRING)) {
                String oldModule = tag.getString(mapping.oldSlot());
                if (!tag.contains(mapping.newSlot(), Tag.TAG_STRING)) {
                    String material = getLegacyMaterial(
                            tag, oldModule, mapping.fallbackMaterial());
                    installModule(stack, mapping.newSlot(), mapping.newModule(),
                            mapping.newVariantPrefix() + material);
                }
                migrateSlotData(tag, mapping.oldSlot(), mapping.newSlot());
                tag.remove(mapping.oldSlot());
                tag.remove(oldModule + "_material");
                changed = true;
            }
        }

        changed |= installMissingModule(
                stack, ModularSlashBladeItem.BLADE_SLOT,
                ModularSlashBladeItem.BLADE_MODULE, "katana_blade/iron");
        changed |= installMissingModule(
                stack, ModularSlashBladeItem.TSUKA_SLOT,
                ModularSlashBladeItem.TSUKA_MODULE, "wrapped_tsuka/stick");
        changed |= installMissingModule(
                stack, ModularSlashBladeItem.TSUBA_SLOT,
                ModularSlashBladeItem.TSUBA_MODULE, "simple_tsuba/iron");
        changed |= installMissingModule(
                stack, ModularSlashBladeItem.SAYA_SLOT,
                ModularSlashBladeItem.SAYA_MODULE, "basic_saya/oak");
        changed |= installMissingModule(
                stack, ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.HABAKI_MODULE, "basic_habaki/iron");
        changed |= installMissingModule(
                stack, ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.KASHIRA_MODULE, "simple_kashira/iron");

        changed |= migrateEnchantmentMappings(tag);
        if (tag.getInt(MODULE_SCHEMA_KEY) != MODULE_SCHEMA_VERSION) {
            tag.putInt(MODULE_SCHEMA_KEY, MODULE_SCHEMA_VERSION);
            changed = true;
        }

        if (changed) {
            IModularItem.updateIdentifier(stack);
            invalidateCaches.run();
        }
        return changed;
    }

    static void installModule(ItemStack stack, String slot, String module, String variant) {
        IModularItem.putModuleInSlot(stack, slot, module, module + "_material", variant);
    }

    private static boolean installMissingModule(
            ItemStack stack, String slot, String module, String variant) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(slot, Tag.TAG_STRING)) {
            return false;
        }
        installModule(stack, slot, module, variant);
        return true;
    }

    private static String getLegacyMaterial(
            CompoundTag tag, String oldModule, String fallback) {
        String variantKey = oldModule + "_material";
        if (!tag.contains(variantKey, Tag.TAG_STRING)) {
            return fallback;
        }
        String oldVariant = tag.getString(variantKey);
        int separator = oldVariant.lastIndexOf('/');
        String material = separator >= 0 ? oldVariant.substring(separator + 1) : oldVariant;
        return material.isBlank() ? fallback : material;
    }

    private static void migrateSlotData(
            CompoundTag tag, String oldSlot, String newSlot) {
        List<String> keys = new ArrayList<>(tag.getAllKeys());
        for (String key : keys) {
            String migratedKey = null;
            if (key.startsWith(oldSlot + ":")) {
                migratedKey = newSlot + key.substring(oldSlot.length());
            } else if (key.startsWith(oldSlot + "_tweak:")) {
                migratedKey = newSlot + key.substring(oldSlot.length());
            } else if (key.equals(oldSlot + "/settle_progress")) {
                migratedKey = newSlot + "/settle_progress";
            }

            if (migratedKey != null && tag.get(key) != null) {
                tag.put(migratedKey, tag.get(key).copy());
                tag.remove(key);
            }
        }
    }

    private static boolean migrateEnchantmentMappings(CompoundTag tag) {
        if (!tag.contains("EnchantmentMapping", Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag mappings = tag.getCompound("EnchantmentMapping");
        boolean changed = false;
        for (String enchantment : mappings.getAllKeys()) {
            String oldSlot = mappings.getString(enchantment);
            for (LegacyModuleMapping mapping : LEGACY_MAPPINGS) {
                if (mapping.oldSlot().equals(oldSlot)) {
                    mappings.putString(enchantment, mapping.newSlot());
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private record LegacyModuleMapping(
            String oldSlot,
            String newSlot,
            String newModule,
            String newVariantPrefix,
            String fallbackMaterial) {
    }

    private BladeModuleMigration() {
    }
}
