package dev.bladetetra.combat;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presentation policy registry. Exact audits use pinned ComboState routes; known
 * add-on dictionary entries may use bounded soft overlap. Unknown
 * abilities never execute third-party runtime code.
 */
public final class ProgrammaticFusionPresentations {
    private static final Map<ResourceLocation, ProgrammaticFusionPresentation> EXPLICIT =
            new ConcurrentHashMap<>();

    public static void register(ResourceLocation ability,
            ProgrammaticFusionPresentation presentation) {
        if (ability == null || presentation == null) {
            throw new IllegalArgumentException(
                    "Programmatic fusion presentation requires id and presentation");
        }
        EXPLICIT.put(ability, presentation);
    }

    public static ProgrammaticFusionPresentation resolve(ResourceLocation ability) {
        if (ability == null) {
            return ProgrammaticFusionPresentation.NONE;
        }
        return EXPLICIT.getOrDefault(ability, ProgrammaticFusionPresentation.NONE);
    }

    private ProgrammaticFusionPresentations() {
    }
}
