package dev.bladetetra.combat;

import dev.bladetetra.compat.fusion.LastSmithFusionProfiles;
import dev.bladetetra.compat.fusion.RecastingFusionProfiles;
import dev.bladetetra.compat.fusion.SjapFusionProfiles;
import dev.bladetetra.compat.fusion.YakumoFusionProfiles;
import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammaticFusionAddonProfilesTest {
    @Test
    void popularAddonDictionariesResolveKnownSlashArtsExactly() {
        registerExactAddons();

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
    void knownAddonEntriesPreferRealSourceExecution() {
        registerExactAddons();

        assertPresentation("slashblade_addon:rapid_blistering_swords", true, true);
        assertPresentation("slashblade_addon:gale_swords", true, true);
        assertPresentation("yakumoblade:gigantjudgement_cut", true, true);
        assertPresentation("yakumoblade:spiral_sword_ex", true, true);
        assertPresentation("yakumoblade:thrust_swords", true, true);
        assertPresentation("yakumoblade:combo_a5", true, true);
        assertPresentation("last_smith:iai_cross", true, true);
        assertPresentation("last_smith:fushigiri", true, true);
        assertPresentation("recasting:blade_storm_lambda", true, true);
        assertPresentation("recasting:multiple_judgement_cut", true, true);

        // Exact add-on audits retain soft overlap as a version-drift fallback.
        assertSoftOverlap("slashblade_addon:rapid_blistering_swords", true);
        assertSoftOverlap("last_smith:iai_cross", true);
        assertSoftOverlap("recasting:blade_storm_lambda", true);
        // Known unaudited entries use the same bounded real-source overlap path.
        assertSoftOverlap("slashblade_addon:gale_swords", true);
        assertSoftOverlap("yakumoblade:gigantjudgement_cut", true);
        assertSoftOverlap("last_smith:fushigiri", true);
        assertSoftOverlap("recasting:multiple_judgement_cut", true);

        assertPresentation("closed_addon:stellar_sword_rain", false, false);
        assertSoftOverlap("closed_addon:stellar_sword_rain", false);
        assertPresentation("slashblade:wave_edge", true, true);
        assertSoftOverlap("slashblade:wave_edge", false);
    }

    @Test
    void softOverlapWindowIsQuarterTimeoutClampedToThreeSix() {
        assertEquals(3, ProgrammaticFusionPresentationRuntime.softOverlapTicks(1));
        assertEquals(3, ProgrammaticFusionPresentationRuntime.softOverlapTicks(12));
        assertEquals(5, ProgrammaticFusionPresentationRuntime.softOverlapTicks(20));
        assertEquals(6, ProgrammaticFusionPresentationRuntime.softOverlapTicks(24));
        assertEquals(6, ProgrammaticFusionPresentationRuntime.softOverlapTicks(200));
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

    @Test
    void easterEggMarkerOnlyAppliesToUnknownThirdPartyPresentation() {
        registerExactAddons();
        LegacyImprintKind nativePiercing = kind("native_piercing", "slashblade:piercing");
        LegacyImprintKind nativeWave = kind("native_wave", "slashblade:wave_edge");
        LegacyImprintKind yakumo = kind("yakumo_cut", "yakumoblade:judgement_cut");
        LegacyImprintKind sjap = kind("sjap_swords",
                "slashblade_addon:rapid_blistering_swords");
        LegacyImprintKind unknown = kind("unknown_star",
                "closed_addon:stellar_sword_rain");

        assertFalse(ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(
                new NamedLegacyParts(nativePiercing, nativeWave, nativeWave)));
        assertFalse(ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(
                new NamedLegacyParts(nativePiercing, yakumo, yakumo)));
        assertFalse(ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(
                new NamedLegacyParts(yakumo, sjap, sjap)));
        assertTrue(ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(
                new NamedLegacyParts(yakumo, unknown, unknown)));

        // A third-party named blade that actually uses a native SlashBlade SA is
        // already covered by the native presentation and should not get the joke.
        LegacyImprintKind addonBladeUsingNativeArt = kind("addon_native_wave",
                "slashblade:wave_edge");
        assertFalse(ProgrammaticFusionPlan.hasUnadaptedThirdPartyArt(
                new NamedLegacyParts(nativePiercing,
                        addonBladeUsingNativeArt, addonBladeUsingNativeArt)));
    }

    @Test
    void addonSignatureWindowsArePerSourceRatherThanPerPair() {
        registerExactAddons();
        assertWindow("slashblade_addon:rapid_blistering_swords", "slashblade_addon:rapid_blistering_swords", 5);
        assertWindow("slashblade_addon:spiral_edge", "slashblade_addon:spiral_edge", 9);
        assertWindow("last_smith:iai_cross", "last_smith:iai_cross_slash", 8);
        assertWindow("last_smith:sakura_blistering_swords", "last_smith:sakura_blistering_swords", 5);
        assertWindow("recasting:void_hole_pitch_black", "recasting:void_hole_pitch_black", 2);
        assertWindow("recasting:lightning_chain_3_lambda", "recasting:lightning_chain_3_lambda", 2);
        assertWindow("recasting:blade_storm_lambda", "recasting:blade_storm_lambda", 2);
    }

    private static void assertWindow(String art, String combo, int ticks) {
        var presentation = ProgrammaticFusionPresentations.resolve(new ResourceLocation(art));
        assertEquals(ticks, presentation.route(combo).stages().get(0).safeAfterTicks());
        assertEquals(FusionHandoff.Policy.SIGNATURE_WINDOW, presentation.route(combo).policy());
        assertEquals(null, presentation.route("changed_addon:unaudited_combo"));
    }

    private static void registerExactAddons() {
        SjapFusionProfiles.register();
        YakumoFusionProfiles.register();
        LastSmithFusionProfiles.register();
        RecastingFusionProfiles.register();
    }

    private static void assertProfile(String id,
            ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response) {
        ProgrammaticFusionProfile profile = ProgrammaticFusionProfiles.resolve(
                new ResourceLocation(id));
        assertEquals(entry, profile.entry(), id);
        assertEquals(response, profile.response(), id);
    }

    private static void assertPresentation(String id, boolean release, boolean response) {
        ProgrammaticFusionPresentation presentation =
                ProgrammaticFusionPresentations.resolve(new ResourceLocation(id));
        assertEquals(release, presentation.delegateRelease(), id + " release");
        assertEquals(response, presentation.delegateResponse(), id + " response");
    }

    private static void assertSoftOverlap(String id, boolean expected) {
        ProgrammaticFusionPresentation presentation =
                ProgrammaticFusionPresentations.resolve(new ResourceLocation(id));
        assertEquals(expected, presentation.softOverlap(), id + " soft overlap");
    }

    private static LegacyImprintKind kind(String id, String slashArt) {
        return new LegacyImprintKind(id,
                new ResourceLocation("example", id),
                new ResourceLocation("example", "model/" + id + ".obj"),
                new ResourceLocation("example", "texture/" + id + ".png"),
                "example:material",
                LegacyCalibrationProfile.DEFAULT,
                5.0D,
                70,
                ResourceLocation.tryParse(slashArt),
                List.of());
    }
}

