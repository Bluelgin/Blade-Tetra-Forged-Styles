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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammaticFusionPlanTest {
    @Test
    void mixedFittingsProduceOrderedPlanWithoutPairCatalogEntry() {
        LegacyImprintKind piercing = kind("test_piercing", "slashblade:piercing");
        LegacyImprintKind storm = kind("test_storm", "example:judgement_storm");

        ProgrammaticFusionPlan forward = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(piercing, storm, storm));
        ProgrammaticFusionPlan reverse = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(storm, piercing, piercing));

        assertEquals("test_piercing->test_storm", forward.key());
        assertEquals(ProgrammaticFusionProfile.Entry.DASH, forward.release().entry());
        assertEquals(3, forward.responseCount());
        assertEquals("test_storm->test_piercing", reverse.key());
        assertEquals(ProgrammaticFusionProfile.Entry.DIRECT, reverse.release().entry());
        assertEquals(1, reverse.responseCount());
    }

    @Test
    void sameSourceAndAuthoredPairsNeverFallThroughToGenericFusion() {
        LegacyImprintKind source = kind("same_source", "example:slash");
        assertNull(ProgrammaticFusionPlan.from(
                new NamedLegacyParts(source, source, source)));

        assertNull(ProgrammaticFusionPlan.from(new NamedLegacyParts(
                LegacyImprintKind.BLACK_FOX,
                LegacyImprintKind.WHITE_FOX,
                LegacyImprintKind.WHITE_FOX)));
    }

    @Test
    void generatedDamageBudgetStaysBoundedAsProjectileCountGrows() {
        LegacyImprintKind direct = kind("direct", "example:plain_cut");
        LegacyImprintKind barrage = kind("barrage", "example:sword_rain_barrage");
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(
                new NamedLegacyParts(direct, barrage, barrage));

        double total = plan.primaryDriveDamage()
                + plan.responseDriveDamage() * plan.responseCount();
        assertTrue(total <= 1.20D);
        assertTrue(plan.primaryDriveDamage() > 0.0D);
        assertFalse(plan.hasNativePrimary());
    }

    @Test
    void responseSpreadIsSymmetricAndKeepsUnitDirection() {
        assertEquals(-16.0D, ProgrammaticFusionHandler.spreadOffset(0, 3), 0.0001D);
        assertEquals(0.0D, ProgrammaticFusionHandler.spreadOffset(1, 3), 0.0001D);
        assertEquals(16.0D, ProgrammaticFusionHandler.spreadOffset(2, 3), 0.0001D);

        Vec3 rotated = ProgrammaticFusionHandler.rotateHorizontal(
                new Vec3(0.0D, 0.0D, 1.0D), 16.0D);
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
}
