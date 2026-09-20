package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionProfile;
import dev.bladetetra.combat.ProgrammaticFusionProfiles;
import net.minecraft.resources.ResourceLocation;

/** Small data helper shared by optional SlashBlade add-on dictionaries. */
final class AddonFusionDictionary {
    static void register(String namespace,
            ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response,
            double intensity,
            String... paths) {
        ProgrammaticFusionProfile profile =
                new ProgrammaticFusionProfile(entry, response, intensity);
        for (String path : paths) {
            ProgrammaticFusionProfiles.register(
                    new ResourceLocation(namespace, path), profile);
        }
    }

    private AddonFusionDictionary() {
    }
}
