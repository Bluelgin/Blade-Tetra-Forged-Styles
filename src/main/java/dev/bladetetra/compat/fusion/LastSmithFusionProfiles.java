package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionProfile;

/** Exact SA semantics for The Last Smith: Resharpened. */
public final class LastSmithFusionProfiles {
    public static final String MOD_ID = "last_smith";

    public static void register() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.88D,
                "transmigration_slash", "fushigiri", "iai_cross");
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.86D,
                "sakura_blistering_swords");
    }

    private LastSmithFusionProfiles() {
    }
}
