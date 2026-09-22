package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Ordered, runtime-generated fusion plan for mixed named-blade fittings.
 * Authored LegacyFusion entries always take precedence over this fallback.
 */
public record ProgrammaticFusionPlan(String key,
        ResourceLocation releaseAbility,
        ResourceLocation responseAbility,
        ProgrammaticFusionProfile release,
        ProgrammaticFusionProfile response,
        ProgrammaticFusionPresentation releasePresentation,
        ProgrammaticFusionPresentation responsePresentation,
        double primaryDriveDamage,
        double responseDriveDamage,
        int responseCount) {
    private static final double DIRECT_PRIMARY_BUDGET = 0.65D;
    private static final double RESPONSE_BUDGET_WITH_NATIVE_PRIMARY = 0.55D;
    private static final double RESPONSE_BUDGET_WITH_DIRECT_PRIMARY = 0.55D;

    public static ProgrammaticFusionPlan from(ItemStack stack) {
        return stack == null ? null : from(NamedLegacyParts.fromStack(stack));
    }

    /**
     * True when at least one third-party side of this generic fusion lacks an exact
     * source-presentation executor and therefore falls back to Blade Tetra semantics.
     */
    public static boolean hasUnadaptedThirdPartyArt(ItemStack stack) {
        return stack != null && hasUnadaptedThirdPartyArt(NamedLegacyParts.fromStack(stack));
    }

    static boolean hasUnadaptedThirdPartyArt(NamedLegacyParts parts) {
        ProgrammaticFusionPlan plan = from(parts);
        if (plan == null) {
            return false;
        }
        return isThirdPartyArt(plan.releaseAbility())
                && !plan.releasePresentation().delegateRelease()
                || isThirdPartyArt(plan.responseAbility())
                && !plan.responsePresentation().delegateResponse();
    }

    static ProgrammaticFusionPlan from(NamedLegacyParts parts) {
        if (!eligible(parts)) {
            return null;
        }
        LegacyImprintKind saya = parts.saya();
        LegacyImprintKind hilt = parts.tsuba();
        ResourceLocation releaseAbility = ProgrammaticFusionProfiles.abilityOf(saya);
        ResourceLocation responseAbility = ProgrammaticFusionProfiles.abilityOf(hilt);
        ProgrammaticFusionProfile release = ProgrammaticFusionProfiles.resolve(releaseAbility);
        ProgrammaticFusionProfile response = ProgrammaticFusionProfiles.resolve(responseAbility);
        ProgrammaticFusionPresentation releasePresentation =
                ProgrammaticFusionPresentations.resolve(releaseAbility);
        ProgrammaticFusionPresentation responsePresentation =
                ProgrammaticFusionPresentations.resolve(responseAbility);
        int count = response.response().projectileCount();
        boolean nativePrimary = release.entry() != ProgrammaticFusionProfile.Entry.DIRECT;
        boolean delegatedPrimary = releasePresentation.delegateRelease();
        // Reserve the direct fallback even when exact release was advertised: runtime
        // registry/linkage failure must not silently remove the primary attack.
        double primaryBudget = nativePrimary ? 0.0D : DIRECT_PRIMARY_BUDGET;
        double responseBudget = nativePrimary || delegatedPrimary
                ? RESPONSE_BUDGET_WITH_NATIVE_PRIMARY
                : RESPONSE_BUDGET_WITH_DIRECT_PRIMARY;
        double intensity = 0.75D + 0.25D * ((release.intensity() + response.intensity()) * 0.5D);
        return new ProgrammaticFusionPlan(
                saya.id() + "->" + hilt.id(),
                releaseAbility,
                responseAbility,
                release,
                response,
                releasePresentation,
                responsePresentation,
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

    boolean hasDelegatedPrimary() {
        return releasePresentation.delegateRelease();
    }

    private static boolean isThirdPartyArt(ResourceLocation ability) {
        if (ability == null) {
            return false;
        }
        String namespace = ability.getNamespace();
        return !"slashblade".equals(namespace) && !BladeTetra.MOD_ID.equals(namespace);
    }
}

