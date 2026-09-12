package dev.bladetetra.compat;

import dev.bladetetra.forging.ForgingImprovements;
import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.item.ModularSlashBladeItem;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirement;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirementDeserializer;

/** Only exposes an attunement operation for its exact ordered fitting pair. */
public record LegacyFusionRequirement(String id) implements CraftingRequirement {
    public static final String TYPE = "blade_tetra:legacy_fusion";

    public static void register() {
        CraftingRequirementDeserializer.registerSupplier(TYPE,
                json -> new LegacyFusionRequirement(json.get("id").getAsString()));
    }

    @Override
    public boolean test(CraftingContext context) {
        LegacyFusion fusion = LegacyFusion.byId(id);
        return fusion != null
                && context != null
                && fusion.matches(dev.bladetetra.forging.NamedLegacyParts.fromStack(
                context.targetStack))
                && !ForgingImprovements.has(context.targetStack,
                ModularSlashBladeItem.BLADE_SLOT, fusion.improvement());
    }
}
