package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammaticFusionPlanTest {
    private static final List<NativeArt> NATIVE_ARTS = List.of(
            new NativeArt("judgement_cut",
                    ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                    ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 3),
            new NativeArt("sakura_end",
                    ProgrammaticFusionProfile.Entry.SAKURA_END,
                    ProgrammaticFusionProfile.Response.SAKURA_CROSS, 2),
            new NativeArt("void_slash",
                    ProgrammaticFusionProfile.Entry.VOID_SLASH,
                    ProgrammaticFusionProfile.Response.VOID_TRIDENT, 3),
            new NativeArt("circle_slash",
                    ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                    ProgrammaticFusionProfile.Response.CIRCLE_RING, 4),
            new NativeArt("drive_vertical",
                    ProgrammaticFusionProfile.Entry.DRIVE_VERTICAL,
                    ProgrammaticFusionProfile.Response.VERTICAL_DRIVE, 1),
            new NativeArt("drive_horizontal",
                    ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                    ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 1),
            new NativeArt("wave_edge",
                    ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                    ProgrammaticFusionProfile.Response.WAVE_EDGE, 4),
            new NativeArt("piercing",
                    ProgrammaticFusionProfile.Entry.PIERCING,
                    ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 1));

    @Test
    void nativeSlashBladeDictionaryCoversEveryBaseSlashArt() {
        for (NativeArt expected : NATIVE_ARTS) {
            ResourceLocation ability = new ResourceLocation("slashblade", expected.id());
            ProgrammaticFusionProfile profile = ProgrammaticFusionProfiles.resolve(ability);
            assertEquals(expected.entry(), profile.entry(), expected.id());
            assertEquals(expected.response(), profile.response(), expected.id());
            assertEquals(expected.responseCount(), profile.response().projectileCount(),
                    expected.id());
            assertEquals(ProgrammaticFusionPresentation.FULL,
                    ProgrammaticFusionPresentations.resolve(ability), expected.id());
        }
    }

    @Test
    void everyOrderedPairOfNativeBaseArtsProducesAPlan() {
        for (int sayaIndex = 0; sayaIndex < NATIVE_ARTS.size(); sayaIndex++) {
            for (int hiltIndex = 0; hiltIndex < NATIVE_ARTS.size(); hiltIndex++) {
                if (sayaIndex == hiltIndex) {
                    continue;
                }
                NativeArt sayaArt = NATIVE_ARTS.get(sayaIndex);
                NativeArt hiltArt = NATIVE_ARTS.get(hiltIndex);
                LegacyImprintKind saya = kind("saya_" + sayaIndex,
                        "slashblade:" + sayaArt.id());
                LegacyImprintKind hilt = kind("hilt_" + hiltIndex,
                        "slashblade:" + hiltArt.id());

                ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(
                        new NamedLegacyParts(saya, hilt, hilt));

                assertNotNull(plan, sayaArt.id() + " -> " + hiltArt.id());
                assertEquals(sayaArt.entry(), plan.release().entry());
                assertEquals(hiltArt.response(), plan.response().response());
                assertEquals(hiltArt.responseCount(), plan.responseCount());
                assertEquals(ProgrammaticFusionPresentation.FULL,
                        plan.releasePresentation());
                assertEquals(ProgrammaticFusionPresentation.FULL,
                        plan.responsePresentation());
                assertTrue(plan.hasNativePrimary());
                assertTrue(plan.hasDelegatedPrimary());
                assertEquals(0.0D, plan.primaryDriveDamage(), 0.0001D);
            }
        }
    }

    @Test
    void mixedFittingsRemainOrdered() {
        LegacyImprintKind piercing = kind("test_piercing", "slashblade:piercing");
        LegacyImprintKind judgement = kind("test_judgement", "slashblade:judgement_cut");

        ProgrammaticFusionPlan forward = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(piercing, judgement, judgement));
        ProgrammaticFusionPlan reverse = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(judgement, piercing, piercing));

        assertEquals("test_piercing->test_judgement", forward.key());
        assertEquals(ProgrammaticFusionProfile.Entry.PIERCING, forward.release().entry());
        assertEquals(ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO,
                forward.response().response());
        assertEquals("test_judgement->test_piercing", reverse.key());
        assertEquals(ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                reverse.release().entry());
        assertEquals(ProgrammaticFusionProfile.Response.PIERCING_FOCUS,
                reverse.response().response());
    }

    @Test
    void sameSourceAndAuthoredPairsNeverFallThroughToGenericFusion() {
        LegacyImprintKind source = kind("same_source", "slashblade:circle_slash");
        assertNull(ProgrammaticFusionPlan.from(
                new NamedLegacyParts(source, source, source)));

        assertNull(ProgrammaticFusionPlan.from(new NamedLegacyParts(
                LegacyImprintKind.BLACK_FOX,
                LegacyImprintKind.WHITE_FOX,
                LegacyImprintKind.WHITE_FOX)));
    }

    @Test
    void generatedDamageBudgetStaysBoundedForLargestNativeResponse() {
        LegacyImprintKind direct = kind("direct", "example:plain_cut");
        LegacyImprintKind circle = kind("circle", "slashblade:circle_slash");
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(direct, circle, circle));

        double total = plan.primaryDriveDamage()
                + plan.responseDriveDamage() * plan.responseCount();
        assertTrue(total <= 1.20D);
        assertTrue(plan.primaryDriveDamage() > 0.0D);
        assertEquals(4, plan.responseCount());
        assertFalse(plan.hasNativePrimary());
    }

    @Test
    void nativeResponsesHaveDistinctBoundedGeometry() {
        assertEquals(0.0D, ProgrammaticFusionHandler.responseYaw(
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 0), 0.0001D);
        assertEquals(90.0D, ProgrammaticFusionHandler.responseYaw(
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 1), 0.0001D);
        assertEquals(270.0D, ProgrammaticFusionHandler.responseYaw(
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 3), 0.0001D);

        assertEquals(0, ProgrammaticFusionHandler.responseDelay(
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0));
        assertEquals(6, ProgrammaticFusionHandler.responseDelay(
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 2));

        assertTrue(ProgrammaticFusionHandler.responseSpeed(
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 3)
                > ProgrammaticFusionHandler.responseSpeed(
                        ProgrammaticFusionProfile.Response.WAVE_EDGE, 0));
        assertEquals(-90.0F, ProgrammaticFusionHandler.responseRoll(
                ProgrammaticFusionProfile.Response.VERTICAL_DRIVE, 0), 0.0001F);
        assertEquals(0.0F, ProgrammaticFusionHandler.responseRoll(
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0), 0.0001F);

        Vec3 rotated = ProgrammaticFusionHandler.rotateHorizontal(
                new Vec3(0.0D, 0.0D, 1.0D), 90.0D);
        assertEquals(1.0D, rotated.length(), 0.0001D);
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

    private record NativeArt(String id, ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response, int responseCount) {
    }
}
