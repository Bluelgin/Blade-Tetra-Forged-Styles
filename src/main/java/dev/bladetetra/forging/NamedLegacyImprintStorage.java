package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Stable storage for named-imprint identity.
 *
 * <p>Tetra owns the generic module variant. Blade Tetra owns the soft reference
 * to the source named blade. This keeps addon ids out of Tetra's variant table
 * while preserving enough information to render and restore orthodox abilities.</p>
 */
public final class NamedLegacyImprintStorage {
    public static final String ROOT = "blade_tetra_named_imprints";
    private static final String SAYA = "saya";
    private static final String TSUBA = "tsuba";

    public static String genericVariant(String part) {
        return "legacy_" + part + "/imprinted";
    }

    public static String partForSlot(String slot) {
        return switch (slot) {
            case "slashblade/saya" -> SAYA;
            case "slashblade/tsuba" -> TSUBA;
            default -> null;
        };
    }

    public static String sourceId(ItemStack stack, String part) {
        return stack == null ? null : sourceId(stack.getTag(), part);
    }

    public static String sourceId(CompoundTag tag, String part) {
        if (tag == null || !validPart(part)) {
            return null;
        }
        String module = tag.getString("slashblade/" + part);
        if (module.isEmpty()) {
            return null;
        }
        String material = tag.getString(module + "_material");
        String prefix = "legacy_" + part + "/";
        if (material.equals(genericVariant(part))) {
            if (!tag.contains(ROOT, Tag.TAG_COMPOUND)) {
                return null;
            }
            String value = tag.getCompound(ROOT).getString(part);
            return value.isBlank() ? null : value;
        }
        if (material.startsWith(prefix)) {
            String value = material.substring(prefix.length());
            return value.isBlank() || value.equals("imprinted") ? null : value;
        }
        return null;
    }

    public static boolean migrateStack(ItemStack stack) {
        return stack != null && migrateStackTag(stack.getTag());
    }

    /**
     * Migrates 1.5.x per-blade Tetra variants to the two generic V2 variants.
     * Unknown/removed addon ids are intentionally retained as soft references.
     */
    public static boolean migrateStackTag(CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        boolean changed = false;
        for (String part : new String[]{SAYA, TSUBA}) {
            String module = tag.getString("slashblade/" + part);
            if (module.isEmpty()) {
                changed |= clearSource(tag, part);
                continue;
            }
            String materialKey = module + "_material";
            String material = tag.getString(materialKey);
            String prefix = "legacy_" + part + "/";
            if (material.startsWith(prefix) && !material.equals(genericVariant(part))) {
                String source = material.substring(prefix.length());
                if (!source.isBlank()) {
                    changed |= putSource(tag, part, source);
                    tag.putString(materialKey, genericVariant(part));
                    changed = true;
                }
            } else if (!material.equals(genericVariant(part))) {
                changed |= clearSource(tag, part);
            }
        }
        return changed;
    }

    public static boolean applyCraftResult(ItemStack stack, String slot, String schematicKey) {
        if (stack == null) {
            return false;
        }
        return applyCraftResultTag(stack.getOrCreateTag(), slot, schematicKey);
    }

    static boolean applyCraftResultTag(CompoundTag tag, String slot, String schematicKey) {
        String part = partForSlot(slot);
        if (part == null || tag == null) {
            return false;
        }

        // Tetra invokes crafting effects after the schematic has produced the upgraded
        // stack. Use that final module state as the source of truth. An unrelated
        // refinement/enchantment schematic may operate on the same saya/tsuba slot
        // without replacing our generic module; such a craft must retain the existing
        // source identity. Only an actual replacement clears it.
        boolean genericInstalled = genericVariantInstalled(tag, part);
        String source = sourceFromSchematic(schematicKey, part);
        if (source != null) {
            return genericInstalled ? putSource(tag, part, source) : clearSource(tag, part);
        }
        return genericInstalled ? false : clearSource(tag, part);
    }

    public static String sourceFromSchematic(String schematicKey, String part) {
        if (schematicKey == null || !validPart(part)) {
            return null;
        }
        String key = schematicKey.startsWith("tetra:")
                ? schematicKey.substring("tetra:".length()) : schematicKey;
        String prefix = "slashblade/legacy_auto/";
        String suffix = "/" + part;
        if (!key.startsWith(prefix) || !key.endsWith(suffix)
                || key.length() <= prefix.length() + suffix.length()) {
            return null;
        }
        String source = key.substring(prefix.length(), key.length() - suffix.length());
        return source.isBlank() ? null : source;
    }

    public static boolean putSource(CompoundTag tag, String part, String source) {
        if (tag == null || !validPart(part) || source == null || source.isBlank()) {
            return false;
        }
        CompoundTag root = tag.contains(ROOT, Tag.TAG_COMPOUND)
                ? tag.getCompound(ROOT) : new CompoundTag();
        if (source.equals(root.getString(part))) {
            return false;
        }
        root.putString(part, source);
        tag.put(ROOT, root);
        return true;
    }

    public static boolean clearSource(CompoundTag tag, String part) {
        if (tag == null || !validPart(part) || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag root = tag.getCompound(ROOT);
        if (!root.contains(part, Tag.TAG_STRING)) {
            return false;
        }
        root.remove(part);
        if (root.isEmpty()) {
            tag.remove(ROOT);
        } else {
            tag.put(ROOT, root);
        }
        return true;
    }

    private static boolean genericVariantInstalled(CompoundTag tag, String part) {
        String module = tag.getString("slashblade/" + part);
        return !module.isEmpty()
                && genericVariant(part).equals(tag.getString(module + "_material"));
    }

    private static boolean validPart(String part) {
        return SAYA.equals(part) || TSUBA.equals(part);
    }

    private NamedLegacyImprintStorage() {
    }
}
