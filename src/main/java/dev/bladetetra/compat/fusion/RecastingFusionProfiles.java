package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionProfile;

/**
 * Semantic dictionary for Recasting2. Lambda variants intentionally share the
 * same grammar as their base art; intensity remains bounded by the fusion plan.
 */
public final class RecastingFusionProfiles {
    public static final String MOD_ID = "recasting";

    public static void register() {
        judgement();
        barrages();
        rings();
        voidArts();
        focused();
        crosses();
        drives();
        // ExtendedSlashArts tick 0 creates independent entities / actor timers.
        // The remaining entries have semantic knowledge only, not a lifecycle audit.
        for (String art : new String[] { "void_hole_pitch_black",
                "lightning_chain_3_lambda", "blade_storm_lambda" }) {
            AddonFusionDictionary.signature(MOD_ID, art, art, 1923, 1928, .5F, 2);
        }
    }

    private static void judgement() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0.92D,
                "multiple_judgement_cut", "infinite_judgement_cut",
                "lightning_call", "lightning_chain_1", "lightning_chain_2",
                "lightning_chain_3", "lightning_chain_3_lambda",
                "heaven_twelve_hit", "heaven_twelve_hit_lambda",
                "soul_sever", "soul_sever_lambda",
                "jade_domain", "jade_domain_lambda",
                "verdict", "starfall");
    }

    private static void barrages() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.86D,
                "storm_phantom_swords", "storm_phantom_swords_lambda",
                "sword_rain", "sword_rain_lambda",
                "star_1", "star_2", "star_3", "star_4", "star_4_lambda",
                "multiple_drive", "multiple_drive_lambda",
                "rapid_phantom_swords", "rapid_phantom_swords_dense",
                "rapid_phantom_swords_dense_lambda",
                "unlimited_blade_works", "unlimited_blade_works_lambda",
                "blade_storm", "blade_storm_lambda",
                "zantetsuden_row", "zantetsuden_row_lambda",
                "long_sky_sunset", "long_sky_sunset_lambda",
                "rift_gale", "rift_gale_lambda",
                "blistering_qi", "heavy_payload",
                "tidal_surge", "celestial_drive");
    }

    private static void rings() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 0.84D,
                "cloud_wheel", "cloud_wheel_storm",
                "stellar_rotation", "matrix", "matrix_lambda",
                "infinite_bloom");
    }

    private static void voidArts() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                ProgrammaticFusionProfile.Response.VOID_TRIDENT, 0.90D,
                "void_hole", "void_hole_pitch_black", "void_hole_fishy_red",
                "phantom_explosion", "phantom_explosion_lambda",
                "myriad_silence", "myriad_silence_lambda",
                "phenomenal_return", "phenomenal_return_lambda",
                "final_supernova", "final_supernova_lambda",
                "inferno", "inferno_lambda",
                "imprisonment", "phase_fracture",
                "otherworld_slash");
    }

    private static void focused() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.PIERCING,
                ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 0.84D,
                "fragment", "cyan_glow", "cyan_glow_lambda",
                "zantetsuden_max", "zantetsuden_max_lambda",
                "dog_bite", "divine_slash", "sky_seize");
    }

    private static void crosses() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.84D,
                "fanatical_dance", "fanatical_dance_lambda",
                "fleeting_shadow", "fleeting_shadow_lambda",
                "azure_haze", "mortal_dust");
    }

    private static void drives() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.80D,
                "laser_1", "laser_2", "laser_3", "laser_3_lambda",
                "time_beyond", "eternal_guard");
    }

    private RecastingFusionProfiles() {
    }
}
