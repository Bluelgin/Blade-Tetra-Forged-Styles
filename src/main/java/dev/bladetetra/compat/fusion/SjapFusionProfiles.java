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
        // Independent swords are spawned at timeline tick 3; all four spiral
        // slashes must run through tick 7. Two ticks cover first-inventory-tick offset.
        AddonFusionDictionary.signature(MOD_ID, "rapid_blistering_swords",
                "rapid_blistering_swords", 400, 459, 1F, 5);
        AddonFusionDictionary.signature(MOD_ID, "spiral_edge",
                "spiral_edge", 400, 459, 1F, 9);
    }

    private SjapFusionProfiles() {
    }
}
