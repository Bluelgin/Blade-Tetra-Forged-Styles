package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirement;
import se.mickelus.tetra.module.schematic.requirement.CraftingRequirementDeserializer;

import java.util.Arrays;

/** Validates a dynamic unfolded scroll at the workbench before showing the upgrade. */
public record CustomStyleScrollRequirement(ResourceLocation key) implements CraftingRequirement {
    public static void register() {
        CraftingRequirementDeserializer.registerSupplier("blade_tetra:custom_style_scroll",
                json -> new CustomStyleScrollRequirement(
                        new ResourceLocation(json.get("key").getAsString())));
    }

    @Override
    public boolean test(CraftingContext context) {
        return context != null && context.unlocks != null && context.world != null
                && context.pos != null
                && Arrays.asList(context.unlocks).contains(key)
                && CustomStyleScrollLookup.unique(context.world, context.pos) != null;
    }
}
