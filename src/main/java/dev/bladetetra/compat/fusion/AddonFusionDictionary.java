package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionPresentation;
import dev.bladetetra.combat.ProgrammaticFusionPresentations;
import dev.bladetetra.combat.ProgrammaticFusionProfile;
import dev.bladetetra.combat.ProgrammaticFusionProfiles;
import net.minecraft.resources.ResourceLocation;

/** Small data helper shared by optional SlashBlade add-on dictionaries. */
final class AddonFusionDictionary {
    /**
     * Registers both semantic fallback data and an exact presentation allow-list.
     * Callers use this only for source registries whose SlashArts -> ComboState
     * contract has been inspected; unknown add-ons never reach source execution.
     */
    static void register(String namespace,
            ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response,
            double intensity,
            String... paths) {
        ProgrammaticFusionProfile profile =
                new ProgrammaticFusionProfile(entry, response, intensity);
        for (String path : paths) {
            ResourceLocation ability = new ResourceLocation(namespace, path);
            ProgrammaticFusionProfiles.register(ability, profile);
            ProgrammaticFusionPresentations.register(
                    ability, ProgrammaticFusionPresentation.FULL);
        }
    }

    private AddonFusionDictionary() {
    }
}
