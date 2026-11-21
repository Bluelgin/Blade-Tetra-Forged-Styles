package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Confirmed source pair. Registry availability can change without changing the stored pair. */
public record ProgrammaticFusionPlan(String key, ResourceLocation releaseAbility,
        ResourceLocation responseAbility) {

    public static ProgrammaticFusionPlan from(ItemStack stack) {
        ProgrammaticFusionPlan plan = candidate(stack);
        return ProgrammaticFusionAttunement.matches(stack, plan) ? plan : null;
    }

    public static ProgrammaticFusionPlan candidate(ItemStack stack) {
        return stack == null ? null : from(NamedLegacyParts.fromStack(stack));
    }

    static ProgrammaticFusionPlan from(NamedLegacyParts parts) {
        if (!eligible(parts)) return null;
        LegacyImprintKind saya = parts.saya();
        LegacyImprintKind tsuba = parts.tsuba();
        return new ProgrammaticFusionPlan(saya.id() + "->" + tsuba.id(),
                saya.slashArt(), tsuba.slashArt());
    }

    static boolean eligible(NamedLegacyParts parts) {
        if (parts == null || parts.saya() == null || parts.tsuba() == null) {
            return false;
        }
        if (parts.saya().id().equals(parts.tsuba().id())) {
            return false;
        }
        // A deliberately authored pair owns its identity even before its attunement
        // improvement is present; never silently replace it with the generic fallback.
        return LegacyFusion.installed(parts) == null;
    }

}
