package dev.bladetetra.forging;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Data-owned fitting match; Java handlers still own the actual ability behavior. */
record LegacyFusionDefinition(String id, String sayaId, String hiltId,
        String improvement, ResourceLocation slashArt,
        List<ResourceLocation> specialEffects, int priority) {
    LegacyFusionDefinition {
        specialEffects = specialEffects == null ? List.of() : List.copyOf(specialEffects);
    }

    boolean matches(NamedLegacyParts parts) {
        return parts != null
                && parts.saya() != null
                && parts.tsuba() != null
                && sayaId.equals(parts.saya().id())
                && hiltId.equals(parts.tsuba().id());
    }

    /** Backward-compatible primary ability view used by older guide/test callers. */
    AbilityType abilityType() {
        return slashArt != null ? AbilityType.SLASH_ART : AbilityType.SPECIAL_EFFECT;
    }

    /** Backward-compatible primary ability view; coupled fusions prefer the SA. */
    ResourceLocation ability() {
        return slashArt != null ? slashArt
                : specialEffects.isEmpty() ? null : specialEffects.get(0);
    }

    enum AbilityType {
        SLASH_ART,
        SPECIAL_EFFECT
    }
}
