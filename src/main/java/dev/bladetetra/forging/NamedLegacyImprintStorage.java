package dev.bladetetra.forging;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
    private static final String SNAPSHOT_SUFFIX = "_snapshot";
    private static final String NAME = "name";
    private static final String MODEL = "model";
    private static final String TEXTURE = "texture";
    private static final String MATERIAL = "material";
    private static final String PROFILE = "profile";
    private static final String BASE_ATTACK = "base_attack";
    private static final String MAX_DAMAGE = "max_damage";
    private static final String SLASH_ART = "slash_art";
    private static final String SPECIAL_EFFECTS = "special_effects";

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

    /**
     * Returns the self-contained semantic identity captured when this fitting was
     * imprinted. New stacks can therefore use their named-blade semantics without
     * consulting addon data again during gameplay or player loading.
     */
    public static LegacyImprintKind snapshot(ItemStack stack, String part) {
        return stack == null ? null : snapshot(stack.getTag(), part);
    }

    static LegacyImprintKind snapshot(CompoundTag tag, String part) {
        String source = sourceId(tag, part);
        if (source == null || tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag root = tag.getCompound(ROOT);
        String key = snapshotKey(part);
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag value = root.getCompound(key);
        if (!source.equals(value.getString("id"))
                || !value.contains(NAME, Tag.TAG_STRING)
                || !value.contains(MODEL, Tag.TAG_STRING)
                || !value.contains(TEXTURE, Tag.TAG_STRING)
                || !value.contains(MATERIAL, Tag.TAG_STRING)
                || !value.contains(PROFILE, Tag.TAG_COMPOUND)
                || !value.contains(BASE_ATTACK, Tag.TAG_DOUBLE)
                || !value.contains(MAX_DAMAGE, Tag.TAG_INT)) {
            return null;
        }

        ResourceLocation name = ResourceLocation.tryParse(value.getString(NAME));
        ResourceLocation model = ResourceLocation.tryParse(value.getString(MODEL));
        ResourceLocation texture = ResourceLocation.tryParse(value.getString(TEXTURE));
        double baseAttack = value.getDouble(BASE_ATTACK);
        int maxDamage = value.getInt(MAX_DAMAGE);
        if (name == null || model == null || texture == null
                || !Double.isFinite(baseAttack) || maxDamage <= 0) {
            return null;
        }

        ResourceLocation slashArt = null;
        String slashArtValue = value.getString(SLASH_ART);
        if (!slashArtValue.isBlank()) {
            slashArt = ResourceLocation.tryParse(slashArtValue);
        }
        List<ResourceLocation> specialEffects = new ArrayList<>();
        ListTag storedEffects = value.getList(SPECIAL_EFFECTS, Tag.TAG_STRING);
        for (int i = 0; i < storedEffects.size(); i++) {
            ResourceLocation effect = ResourceLocation.tryParse(storedEffects.getString(i));
            if (effect != null) {
                specialEffects.add(effect);
            }
        }

        return new LegacyImprintKind(source, name, model, texture,
                value.getString(MATERIAL),
                LegacyCalibrationProfile.read(value.getCompound(PROFILE)),
                baseAttack, maxDamage, slashArt, specialEffects);
    }

    public static boolean putSnapshot(ItemStack stack, String part, LegacyImprintKind kind) {
        return stack != null && putSnapshot(stack.getOrCreateTag(), part, kind);
    }

    static boolean putSnapshot(CompoundTag tag, String part, LegacyImprintKind kind) {
        if (tag == null || !validPart(part) || kind == null) {
            return false;
        }
        String source = sourceId(tag, part);
        if (source == null || !source.equals(kind.id())) {
            return false;
        }

        CompoundTag value = new CompoundTag();
        value.putString("id", kind.id());
        value.putString(NAME, kind.name().toString());
        value.putString(MODEL, kind.model().toString());
        value.putString(TEXTURE, kind.texture().toString());
        value.putString(MATERIAL, kind.material() == null ? "" : kind.material());
        value.put(PROFILE, kind.defaultProfile().write());
        value.putDouble(BASE_ATTACK, kind.baseAttack());
        value.putInt(MAX_DAMAGE, kind.maxDamage());
        if (kind.slashArt() != null) {
            value.putString(SLASH_ART, kind.slashArt().toString());
        }
        ListTag effects = new ListTag();
        for (ResourceLocation effect : kind.specialEffects()) {
            if (effect != null) {
                effects.add(StringTag.valueOf(effect.toString()));
            }
        }
        value.put(SPECIAL_EFFECTS, effects);

        CompoundTag root = tag.contains(ROOT, Tag.TAG_COMPOUND)
                ? tag.getCompound(ROOT) : new CompoundTag();
        String key = snapshotKey(part);
        if (root.contains(key, Tag.TAG_COMPOUND) && value.equals(root.getCompound(key))) {
            return false;
        }
        root.put(key, value);
        tag.put(ROOT, root);
        return true;
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
        root.remove(snapshotKey(part));
        tag.put(ROOT, root);
        return true;
    }

    public static boolean clearSource(CompoundTag tag, String part) {
        if (tag == null || !validPart(part) || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag root = tag.getCompound(ROOT);
        boolean changed = false;
        if (root.contains(part, Tag.TAG_STRING)) {
            root.remove(part);
            changed = true;
        }
        String snapshotKey = snapshotKey(part);
        if (root.contains(snapshotKey)) {
            root.remove(snapshotKey);
            changed = true;
        }
        if (!changed) {
            return false;
        }
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

    private static String snapshotKey(String part) {
        return part + SNAPSHOT_SUFFIX;
    }

    private static boolean validPart(String part) {
        return SAYA.equals(part) || TSUBA.equals(part);
    }

    private NamedLegacyImprintStorage() {
    }
}
