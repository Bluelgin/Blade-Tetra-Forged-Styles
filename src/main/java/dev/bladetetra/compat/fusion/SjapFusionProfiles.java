package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionProfile;

/** Exact SA semantics for SlashBlade Japanese Addon Pack (1.20.1). */
public final class SjapFusionProfiles {
    public static final String MOD_ID = "slashblade_addon";

    public static void register() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.86D,
                "rapid_blistering_swords", "gale_swords");
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 0.84D,
                "spiral_edge", "fire_spiral");
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.82D,
                "water_drive");
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0.86D,
                "lighting_swords");
    }

    private SjapFusionProfiles() {
    }
}
