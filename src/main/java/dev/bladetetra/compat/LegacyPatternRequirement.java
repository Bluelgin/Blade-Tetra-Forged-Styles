package dev.bladetetra.compat;

import dev.bladetetra.forging.*;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.requirement.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;

public record LegacyPatternRequirement(String id, String part) implements CraftingRequirement {
    public static void register() {
        CraftingRequirementDeserializer.registerSupplier("blade_tetra:legacy_pattern",
                json -> new LegacyPatternRequirement(json.get("id").getAsString(), json.get("part").getAsString()));
    }
    @Override public boolean test(CraftingContext context) {
        if (part.equals("tsuka")) return false;
        LegacyImprintKind kind = NamedLegacyCatalog.get(id);
        if (kind == null || context == null || context.unlocks == null) return false;
        if (!Arrays.asList(context.unlocks).contains(new ResourceLocation("tetra", kind.schematic(part)))) return false;
        NamedLegacyParts installed = NamedLegacyParts.fromStack(context.targetStack);
        return !kind.equals(part.equals("saya") ? installed.saya() : installed.tsuba());
    }
}
