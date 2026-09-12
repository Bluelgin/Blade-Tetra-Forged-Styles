package dev.bladetetra.forging;

import net.minecraft.resources.ResourceLocation;

/** Data-owned fitting match; Java handlers still own the actual ability behavior. */
record LegacyFusionDefinition(String id, String sayaId, String hiltId,
        String improvement, AbilityType abilityType, ResourceLocation ability,
        int priority) {
    boolean matches(NamedLegacyParts parts) {
        return parts != null
                && parts.saya() != null
                && parts.tsuba() != null
                && sayaId.equals(parts.saya().id())
                && hiltId.equals(parts.tsuba().id());
    }

    enum AbilityType {
        SLASH_ART,
        SPECIAL_EFFECT
    }
}
