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

    /**
     * True when this generic fusion contains at least one non-native Slash Art and
     * therefore currently uses Blade Tetra's bounded presentation instead of the
     * source add-on's own runtime effect executor.
     */
    public static boolean hasUnadaptedThirdPartyArt(ItemStack stack) {
        return stack != null && hasUnadaptedThirdPartyArt(NamedLegacyParts.fromStack(stack));
    }

    static boolean hasUnadaptedThirdPartyArt(NamedLegacyParts parts) {
        if (!eligible(parts)) {
            return false;
        }
        return isThirdPartyArt(parts.saya()) || isThirdPartyArt(parts.tsuba());
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

    private static boolean isThirdPartyArt(LegacyImprintKind kind) {
        ResourceLocation ability = semanticAbility(kind);
        if (ability == null) {
            return false;
        }
        String namespace = ability.getNamespace();
        return !"slashblade".equals(namespace) && !BladeTetra.MOD_ID.equals(namespace);
    }

    private static ResourceLocation semanticAbility(LegacyImprintKind kind) {
        if (kind == null) {
            return null;
        }
        if (kind.slashArt() != null) {
            return kind.slashArt();
        }
        if (!kind.specialEffects().isEmpty()) {
            return kind.specialEffects().get(0);
        }
        return kind.name();
    }
}
