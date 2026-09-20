package dev.bladetetra.compat.fusion;

import dev.bladetetra.combat.ProgrammaticFusionProfile;

/**
 * Exact ID dictionary for YakumoBlade. The source SA itself is never invoked;
 * each entry is reduced to a bounded native Blade Tetra semantic.
 */
public final class YakumoFusionProfiles {
    public static final String MOD_ID = "yakumoblade";

    public static void register() {
        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0.90D,
                "gigantjudgement_cut", "randomjudgement_cut",
                "judgement_cut", "judgement_cut_low",
                "sword_rain_lightning", "sword_rain_lightning_ex",
                "reincarnation_heaven", "star_rider");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.84D,
                "wither_summond_sword", "ten_drive", "ten_drive_ex",
                "soul_galegwords", "retreat_flying_knife",
                "sword_rain_ride_on",
                "hex_gram_summon_sword_yellow",
                "hex_gram_summon_sword_red",
                "hex_gram_summon_sword_blue",
                "crison_swords", "wave_and_tinys");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 0.82D,
                "spiral_sword_ex");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.PIERCING,
                ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 0.84D,
                "high_slash", "thrust_swords", "hot_drive");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                ProgrammaticFusionProfile.Response.VOID_TRIDENT, 0.86D,
                "wither_attack", "soul_edge", "csoul_edge",
                "combo_a5", "dead_or_life",
                "fox_justices", "dragon_justices",
                "fox_justices_ex", "dragon_justices_ex");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.82D,
                "let_slash", "sakura_slash");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.76D,
                "fire_boost", "fire_boost2", "self_noall_fire_boost2");

        AddonFusionDictionary.register(MOD_ID,
                ProgrammaticFusionProfile.Entry.DIRECT,
                ProgrammaticFusionProfile.Response.FOCUSED_DRIVE, 0.68D,
                "thoo", "tho2", "self_noall");
    }

    private YakumoFusionProfiles() {
    }
}
