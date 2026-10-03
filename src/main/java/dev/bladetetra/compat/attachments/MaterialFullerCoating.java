package dev.bladetetra.compat.attachments;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.resources.ResourceLocation;
import se.mickelus.tetra.module.schematic.MaterialOutcomeDefinition;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;
import se.mickelus.tetra.module.schematic.SchematicDefinition;
import se.mickelus.tetra.module.schematic.SchematicType;

import java.util.Arrays;
import java.util.Map;

/** MMT's sword fuller coating mounted as its native shared improvement, not a replacement blade. */
public final class MaterialFullerCoating {
    public static final String IMPROVEMENT_PREFIX = "improvements_material_fuller/";
    public static final ResourceLocation RECIPE = new ResourceLocation("tetra", "slashblade/attachments/material_coating");
    private static final ResourceLocation SOURCE = new ResourceLocation("tetra", "sword/more_mod_tetra/material_fuller");

    static void adapt(Map<ResourceLocation, SchematicDefinition> definitions) {
        var source = definitions.get(SOURCE);
        // Opt in only when the provider's actual sword recipe is loaded.
        if (source == null || source.outcomes == null || source.displayType != SchematicType.major) return;
        if (Arrays.stream(source.outcomes).anyMatch(outcome -> !(outcome instanceof MaterialOutcomeDefinition)
                || !"sword/more_mod_tetra/material_fuller".equals(outcome.moduleKey))) return;
        var alias = SwordAttachmentSchematics.copy(source);
        alias.key = RECIPE.getPath();
        alias.localizationKey = source.localizationKey == null ? source.key : source.localizationKey;
        alias.displayType = SchematicType.improvement;
        alias.slots = new String[]{ModularSlashBladeItem.BLADE_SLOT};
        alias.keySuffixes = new String[]{""};
        alias.outcomes = Arrays.stream(source.outcomes).map(outcome -> {
            var original = (MaterialOutcomeDefinition) outcome;
            var mapped = new MaterialOutcomeDefinition();
            mapped.materials = original.materials;
            mapped.countOffset = original.countOffset;
            mapped.countFactor = original.countFactor;
            mapped.toolOffset = original.toolOffset;
            mapped.toolFactor = original.toolFactor;
            mapped.experienceOffset = original.experienceOffset;
            mapped.experienceFactor = original.experienceFactor;
            mapped.material = original.material;
            mapped.materialSlot = original.materialSlot;
            mapped.experienceCost = original.experienceCost;
            mapped.requiredTools = original.requiredTools;
            mapped.hidden = original.hidden;
            mapped.improvements = Map.of(IMPROVEMENT_PREFIX, 0);
            return mapped;
        }).toArray(OutcomeDefinition[]::new);
        var nativeRequirement = source.requirement;
        alias.requirement = context -> (nativeRequirement == null || nativeRequirement.test(context))
                && BladeCoatingPolicy.canApply(context.targetStack, alias);
        definitions.put(RECIPE, alias);
    }

    private MaterialFullerCoating() {}
}
