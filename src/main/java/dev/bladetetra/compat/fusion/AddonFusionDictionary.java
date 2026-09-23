package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionPresentation;
import dev.bladetetra.combat.ProgrammaticFusionPresentations;
import dev.bladetetra.combat.ProgrammaticFusionProfile;
import dev.bladetetra.combat.ProgrammaticFusionProfiles;
import dev.bladetetra.combat.FusionHandoff;
import dev.bladetetra.combat.NativeFusionPresentationAudit;
import net.minecraft.resources.ResourceLocation;

/** Small data helper shared by optional SlashBlade add-on dictionaries. */
final class AddonFusionDictionary {
    /**
     * Exact dictionary knowledge is enough to attempt the real source through
     * conservative dynamic lifecycle observation. Exact presentation audits are
     * still registered separately and replace the dynamic policy when available.
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
                    ability, ProgrammaticFusionPresentation.dynamic());
        }
    }

    private AddonFusionDictionary() {
    }

    static void signature(String namespace, String ability, String combo,
            int start, int end, float speed, int safeAfterTicks) {
        ProgrammaticFusionPresentations.register(new ResourceLocation(namespace, ability),
                ProgrammaticFusionPresentation.audited(
                        NativeFusionPresentationAudit.route(new FusionHandoff.Stage(
                                namespace + ":" + combo, start, end, speed, 0, safeAfterTicks)),
                        NativeFusionPresentationAudit.superRoute()));
    }
}
