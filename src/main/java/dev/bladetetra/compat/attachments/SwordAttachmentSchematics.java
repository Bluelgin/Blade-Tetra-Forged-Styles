package dev.bladetetra.compat.attachments;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.resources.ResourceLocation;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;
import se.mickelus.tetra.module.schematic.MaterialOutcomeDefinition;
import se.mickelus.tetra.module.schematic.SchematicDefinition;
import se.mickelus.tetra.module.schematic.SchematicType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Slot adaptation only: never copies a provider's material numbers into our resources. */
public final class SwordAttachmentSchematics {
    private static volatile long revision;
    public static long revision() { return revision; }
    public static final String SOCKET_MODULE = "slashblade/socket_kashira";
    public static final ResourceLocation SOCKET_SCHEMATIC =
            new ResourceLocation("tetra", "slashblade/attachments/socket_kashira");

    public static void adapt(Map<ResourceLocation, SchematicDefinition> definitions) {
        revision++;
        MaterialFullerCoating.adapt(definitions);
        // Iterate a snapshot: the socket alias is a separate recipe, leaving swords untouched.
        for (var entry : new LinkedHashMap<>(definitions).entrySet()) {
            var source = entry.getValue();
            if (source == null || source.slots == null || source.outcomes == null) continue;
            if (entry.getKey().equals(new ResourceLocation("tetra", "sword/socket"))) {
                var alias = copy(source);
                alias.key = SOCKET_SCHEMATIC.getPath();
                alias.localizationKey = alias.key;
                alias.slots = new String[]{ModularSlashBladeItem.KASHIRA_SLOT};
                alias.keySuffixes = new String[]{""};
                alias.outcomes = Arrays.stream(source.outcomes).map(outcome -> {
                    OutcomeDefinition mapped;
                    if (outcome instanceof MaterialOutcomeDefinition materialOutcome) {
                        var materialCopy = new MaterialOutcomeDefinition();
                        materialCopy.materials = materialOutcome.materials;
                        materialCopy.countOffset = materialOutcome.countOffset;
                        materialCopy.countFactor = materialOutcome.countFactor;
                        materialCopy.toolOffset = materialOutcome.toolOffset;
                        materialCopy.toolFactor = materialOutcome.toolFactor;
                        materialCopy.experienceOffset = materialOutcome.experienceOffset;
                        materialCopy.experienceFactor = materialOutcome.experienceFactor;
                        mapped = materialCopy;
                    } else {
                        mapped = new se.mickelus.tetra.module.schematic.UniqueOutcomeDefinition();
                    }
                    mapped.material = outcome.material;
                    mapped.materialSlot = outcome.materialSlot;
                    mapped.experienceCost = outcome.experienceCost;
                    mapped.hidden = outcome.hidden;
                    mapped.requiredTools = outcome.requiredTools;
                    mapped.improvements = outcome.improvements;
                    mapped.moduleKey = "sword/socket".equals(outcome.moduleKey)
                            ? SOCKET_MODULE : outcome.moduleKey;
                    mapped.moduleVariant = outcome.moduleVariant;
                    return mapped;
                }).toArray(OutcomeDefinition[]::new);
                definitions.put(SOCKET_SCHEMATIC, alias);
                continue;
            }
            String target = null;
            if (hasSlot(source, "sword/hilt") && hasWrapOutcome(source)) {
                target = ModularSlashBladeItem.TSUKA_SLOT;
            } else if (hasSlot(source, "sword/blade") && isCoating(source)) {
                target = ModularSlashBladeItem.BLADE_SLOT;
            }
            if (target == null) continue;
            var mapped = copy(source);
            if (!hasSlot(source, target)) {
                mapped.slots = Arrays.copyOf(source.slots, source.slots.length + 1);
                mapped.slots[source.slots.length] = target;
                // Improvement recipes have no per-slot module suffix. Do not map other recipe types.
            }
            if (isCoating(source)) {
                var nativeRequirement = source.requirement;
                mapped.requirement = context -> (nativeRequirement == null || nativeRequirement.test(context))
                        && (!ModularSlashBladeItem.BLADE_SLOT.equals(context.slot)
                        || BladeCoatingPolicy.canApply(context.targetStack, source));
            }
            definitions.put(entry.getKey(), mapped);
        }
    }

    public static boolean isCoating(SchematicDefinition definition) {
        return definition != null && definition.outcomes != null
                && definition.displayType == SchematicType.improvement
                && Arrays.stream(definition.outcomes).anyMatch(outcome -> outcome.improvements != null
                && outcome.improvements.keySet().stream().anyMatch(SwordAttachmentSchematics::isCoatingKey));
    }

    public static boolean isCoatingKey(String key) {
        return key != null && (key.toLowerCase(Locale.ROOT).contains("coating")
                || key.startsWith(MaterialFullerCoating.IMPROVEMENT_PREFIX));
    }

    private static boolean hasWrapOutcome(SchematicDefinition definition) {
        return definition.displayType == SchematicType.improvement
                && Arrays.stream(definition.outcomes).anyMatch(outcome -> outcome.improvements != null
                && outcome.improvements.keySet().stream().anyMatch(key -> key.startsWith("hilt/wrap/")));
    }

    private static boolean hasSlot(SchematicDefinition definition, String slot) {
        return Arrays.asList(definition.slots).contains(slot);
    }

    static SchematicDefinition copy(SchematicDefinition source) {
        var result = new SchematicDefinition();
        // Let the running Tetra version also carry newer metadata (e.g. preview visibility).
        SchematicDefinition.copyFields(source, result);
        // Tetra's merge helper has conditional copy semantics; explicit shallow copying
        // preserves source requirements, locks, tool levels, costs and translation exactly.
        result.replace = source.replace;
        result.key = source.key;
        result.localizationKey = source.localizationKey;
        result.slots = source.slots;
        result.keySuffixes = source.keySuffixes;
        result.materialSlotCount = source.materialSlotCount;
        result.materialRevealSlot = source.materialRevealSlot;
        result.repair = source.repair;
        result.hone = source.hone;
        result.requirement = source.requirement;
        result.displayType = source.displayType;
        result.rarity = source.rarity;
        result.glyph = source.glyph;
        result.translation = source.translation;
        result.applicableMaterials = source.applicableMaterials;
        result.outcomes = source.outcomes;
        result.sources = source.sources;
        return result;
    }

    private SwordAttachmentSchematics() {}
}
