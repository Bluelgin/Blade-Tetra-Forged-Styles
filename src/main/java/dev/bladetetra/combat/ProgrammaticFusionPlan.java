package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.world.item.ItemStack;

/**
 * Ordered, runtime-generated fusion plan for mixed named-blade fittings.
 * Authored LegacyFusion entries always take precedence over this fallback.
 */
public record ProgrammaticFusionPlan(String key,
        ProgrammaticFusionProfile release,
        ProgrammaticFusionProfile response,
        double primaryDriveDamage,
        double responseDriveDamage,
        int responseCount) {
    private static final double DIRECT_PRIMARY_BUDGET = 0.65D;
    private static final double RESPONSE_BUDGET_WITH_NATIVE_PRIMARY = 0.55D;
    private static final double RESPONSE_BUDGET_WITH_DIRECT_PRIMARY = 0.55D;

    public static ProgrammaticFusionPlan from(ItemStack stack) {
        return stack == null ? null : from(NamedLegacyParts.fromStack(stack));
    }

    static ProgrammaticFusionPlan from(NamedLegacyParts parts) {
        if (!eligible(parts)) {
            return null;
        }
        LegacyImprintKind saya = parts.saya();
        LegacyImprintKind hilt = parts.tsuba();
        ProgrammaticFusionProfile release = ProgrammaticFusionProfiles.resolve(saya);
        ProgrammaticFusionProfile response = ProgrammaticFusionProfiles.resolve(hilt);
        int count = response.response().projectileCount();
        boolean nativePrimary = release.entry() != ProgrammaticFusionProfile.Entry.DIRECT;
        double primaryBudget = nativePrimary ? 0.0D : DIRECT_PRIMARY_BUDGET;
        double responseBudget = nativePrimary
                ? RESPONSE_BUDGET_WITH_NATIVE_PRIMARY
                : RESPONSE_BUDGET_WITH_DIRECT_PRIMARY;
        double intensity = 0.75D + 0.25D * ((release.intensity() + response.intensity()) * 0.5D);
        return new ProgrammaticFusionPlan(
                saya.id() + "->" + hilt.id(),
                release,
                response,
                primaryBudget * intensity,
                responseBudget * intensity / count,
                count);
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

    boolean hasNativePrimary() {
        return release.entry() != ProgrammaticFusionProfile.Entry.DIRECT;
    }
}
