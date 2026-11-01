package dev.bladetetra.forging;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.data.MaterialVariantData;
import se.mickelus.tetra.module.data.VariantData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One native Tetra material, evaluated from the installed source snapshot per part. */
public final class NamedBladeMaterial {
    public static final String KEY = "named_blade_ingot";
    private static final ResourceLocation MATERIAL = new ResourceLocation("tetra", "named_blade/ingot");
    private static final Map<String, MaterialVariantData> TEMPLATES = new ConcurrentHashMap<>();
    private static final Cache<ResolvedKey, VariantData> RESOLVED = CacheBuilder.newBuilder().maximumSize(512).build();

    public static boolean supportedSlot(String slot) {
        return NamedLegacyImprintStorage.partForSlot(slot) != null;
    }

    public static boolean namedVariant(String variant) {
        return variant != null && variant.endsWith("/" + KEY);
    }

    public static void clearTemplates() {
        TEMPLATES.clear();
        RESOLVED.invalidateAll();
    }

    public static void register(MaterialVariantData template) {
        for (ResourceLocation material : template.materials) {
            if (material.getNamespace().equals("tetra") && material.getPath().equals("named_blade/")) {
                TEMPLATES.put(template.key + KEY, template);
            }
        }
    }

    /** The catalog has no source: preserve presentation metadata, contribute no stats. */
    public static VariantData catalogPreview(VariantData original) {
        if (!namedVariant(original.key)) return original;
        VariantData result = new se.mickelus.tetra.module.data.UniqueVariantData();
        result.key = original.key;
        result.category = original.category;
        result.glyph = original.glyph;
        result.models = original.models;
        result.namePriority = original.namePriority;
        result.prefixPriority = original.prefixPriority;
        result.tags = original.tags;
        result.rarity = original.rarity;
        result.attributes = com.google.common.collect.ArrayListMultimap.create();
        result.tools = new se.mickelus.tetra.module.data.ToolData();
        result.effects = new se.mickelus.tetra.module.data.EffectData();
        result.aspects = new se.mickelus.tetra.module.data.AspectData();
        return result;
    }

    /** Called for module replacement, including Holo's direct applyOutcome preview path. */
    public static void clearReplacedSource(ItemStack upgraded, String slot) {
        String part = NamedLegacyImprintStorage.partForSlot(slot);
        if (part == null || upgraded.getTag() == null) return;
        CompoundTag tag = upgraded.getTag();
        boolean cleared = NamedLegacyImprintStorage.clearSource(tag, part);
        if (cleared || namedVariant(tag.getString(tag.getString(slot) + "_material")))
            se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(upgraded);
    }

    public static VariantData resolve(ItemStack stack, String module, String slot, VariantData original) {
        if (stack == null || stack.getTag() == null || !supportedSlot(slot)) return original;
        String installed = stack.getTag().getString(module + "_material");
        if (!namedVariant(installed)) return original;
        String part = NamedLegacyImprintStorage.partForSlot(slot);
        var storedAttack = NamedLegacyImprintStorage.snapshotAttack(stack, part);
        double sourceAttack;
        if (storedAttack.isPresent()) sourceAttack = storedAttack.getAsDouble();
        else {
            LegacyImprintKind source = NamedBladeIngotItem.sourceFromId(NamedLegacyImprintStorage.sourceId(stack, part));
            if (source == null) return original;
            sourceAttack = source.baseAttack();
        }
        MaterialVariantData template = TEMPLATES.get(installed);
        MaterialData material = DataManager.instance.materialData.getData(MATERIAL);
        if (template == null || material == null) return original;
        ResolvedKey key = new ResolvedKey(template, material, sourceAttack);
        VariantData result = RESOLVED.getIfPresent(key);
        if (result == null) {
            NamedMaterialStats stats = NamedMaterialStats.of(sourceAttack);
            MaterialData dynamic = material.shallowCopy();
            dynamic.primary = (float) stats.attack();
            dynamic.secondary = (float) stats.secondary();
            dynamic.tertiary = (float) stats.tertiary();
            dynamic.durability = stats.durability();
            dynamic.magicCapacity = stats.capacity();
            dynamic.integrityCost = stats.integrityCost();
            dynamic.integrityGain = stats.integrityGain();
            // The generic catalog entry has zero tool properties; real sources retain
            // the same fixed tool properties as before the catalog-only change.
            dynamic.toolLevel = 3;
            dynamic.toolEfficiency = 4;
            result = template.combine(dynamic);
            // Native material expansion rounds damage to 0.5 per module. That would
            // distort the 25% fittings (and their combined source total), so retain
            // precise damage while leaving Tetra's other stat rounding unchanged.
            var raw = se.mickelus.tetra.properties.AttributeHelper.merge(java.util.Arrays.asList(
                    template.attributes, dynamic.attributes,
                    se.mickelus.tetra.properties.AttributeHelper.multiplyModifiers(template.extract.primaryAttributes, dynamic.primary),
                    se.mickelus.tetra.properties.AttributeHelper.multiplyModifiers(template.extract.secondaryAttributes, dynamic.secondary),
                    se.mickelus.tetra.properties.AttributeHelper.multiplyModifiers(template.extract.tertiaryAttributes, dynamic.tertiary)));
            var attributes = com.google.common.collect.ArrayListMultimap.create(result.attributes);
            var attack = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
            attributes.replaceValues(attack, se.mickelus.tetra.properties.AttributeHelper.collapse(raw.get(attack)));
            result.attributes = attributes;
            RESOLVED.put(key, result);
        }
        return result;
    }

    /** Includes previews: source identity must exist before Tetra reads aggregate properties. */
    public static boolean capture(ItemStack upgraded, String slot, ItemStack[] ingredients) {
        String part = NamedLegacyImprintStorage.partForSlot(slot);
        if (part == null || upgraded == null || upgraded.getTag() == null) return false;
        CompoundTag tag = upgraded.getTag();
        String module = tag.getString(slot);
        if (!namedVariant(tag.getString(module + "_material"))) return false;
        if (ingredients != null) for (ItemStack ingredient : ingredients) {
            // Tetra may have already consumed the last ingot; its source NBT survives shrink(1).
            String id = NamedBladeIngotItem.sourceId(ingredient);
            if (id == null && ingredient != null && ingredient.isEmpty() && ingredient.getTag() != null)
                id = ingredient.getTag().getString("blade_tetra_ingot_source");
            LegacyImprintKind source = NamedBladeIngotItem.sourceFromId(id);
            if (source != null) {
                boolean changed = NamedLegacyImprintStorage.putSource(tag, part, source.id());
                changed |= NamedLegacyImprintStorage.putSnapshot(upgraded, part, source);
                if (changed) se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(upgraded);
                return changed;
            }
        }
        return false;
    }

    /** Migrate only unambiguous legacy layouts; keep unsupported addon records intact. */
    public static boolean migrate(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        boolean changed = false;
        for (String part : new String[]{"blade", "saya", "tsuba"}) {
            String slot = "slashblade/" + part;
            String oldModule = tag.getString(slot);
            LegacyImprintKind source;
            if (oldModule.equals("slashblade/legacy_" + part)) {
                source = NamedLegacyImprintStorage.snapshot(stack, part);
                if (source == null) source = NamedBladeIngotItem.sourceFromId(NamedLegacyImprintStorage.sourceId(stack, part));
            } else if (oldModule.equals("slashblade/fox_" + part) && !part.equals("blade")) {
                String variant = tag.getString(oldModule + "_material");
                source = variant.equals("fox_" + part + "/black") ? LegacyImprintKind.BLACK_FOX
                        : variant.equals("fox_" + part + "/white") ? LegacyImprintKind.WHITE_FOX : null;
            } else continue;
            if (source == null) continue;
            String name = switch (part) {
                case "blade" -> "katana_blade";
                case "saya" -> "basic_saya";
                default -> "simple_tsuba";
            };
            String next = "slashblade/" + name;
            // Unknown module-owned metadata may have semantics we cannot translate safely.
            if (tag.getAllKeys().stream().anyMatch(k -> k.startsWith(oldModule + "_")
                    && !k.equals(oldModule + "_material"))) continue;
            tag.putString(slot, next);
            tag.putString(next + "_material", name + "/" + KEY);
            tag.remove(oldModule + "_material");
            NamedLegacyImprintStorage.putSource(tag, part, source.id());
            NamedLegacyImprintStorage.putSnapshot(stack, part, source);
            changed = true;
        }
        return changed;
    }

    private record ResolvedKey(MaterialVariantData template, MaterialData material, double attack) {}
    private NamedBladeMaterial() {}
}
