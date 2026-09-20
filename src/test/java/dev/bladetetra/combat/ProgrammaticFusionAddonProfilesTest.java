package dev.bladetetra.combat;

import dev.bladetetra.compat.fusion.LastSmithFusionProfiles;
import dev.bladetetra.compat.fusion.RecastingFusionProfiles;
import dev.bladetetra.compat.fusion.SjapFusionProfiles;
import dev.bladetetra.compat.fusion.YakumoFusionProfiles;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgrammaticFusionAddonProfilesTest {
    @Test
    void popularAddonDictionariesResolveKnownSlashArtsExactly() {
        SjapFusionProfiles.register();
        YakumoFusionProfiles.register();
        LastSmithFusionProfiles.register();
        RecastingFusionProfiles.register();

        assertProfile("slashblade_addon:spiral_edge",
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING);
        assertProfile("slashblade_addon:rapid_blistering_swords",
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE);

        assertProfile("yakumoblade:gigantjudgement_cut",
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO);
        assertProfile("yakumoblade:spiral_sword_ex",
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING);
        assertProfile("yakumoblade:thrust_swords",
                ProgrammaticFusionProfile.Entry.PIERCING,
                ProgrammaticFusionProfile.Response.PIERCING_FOCUS);

        assertProfile("last_smith:iai_cross",
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS);
        assertProfile("last_smith:sakura_blistering_swords",
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE);

        assertProfile("recasting:void_hole_pitch_black",
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                ProgrammaticFusionProfile.Response.VOID_TRIDENT);
        assertProfile("recasting:lightning_chain_3_lambda",
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO);
        assertProfile("recasting:blade_storm_lambda",
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE);
    }

    @Test
    void unknownAddonStillGetsLazyConservativeSemantics() {
        assertProfile("closed_addon:stellar_sword_rain",
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE);
        assertProfile("closed_addon:wither_soul",
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                ProgrammaticFusionProfile.Response.VOID_TRIDENT);
        assertProfile("closed_addon:lightning_verdict",
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO);
    }

    private static void assertProfile(String id,
            ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response) {
        ProgrammaticFusionProfile profile = ProgrammaticFusionProfiles.resolve(
                new ResourceLocation(id));
        assertEquals(entry, profile.entry(), id);
        assertEquals(response, profile.response(), id);
    }
}
