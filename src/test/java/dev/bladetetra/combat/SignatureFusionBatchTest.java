package dev.bladetetra.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureFusionBatchTest {
    @Test
    void customDamageStagesUseBoundedSoftAttackGrowth() {
        assertEquals(24.0D, SignatureFusionBalance.effectiveAttack(24.0D), 0.0001D);
        assertEquals(30.4D, SignatureFusionBalance.effectiveAttack(40.0D), 0.0001D);
        assertEquals(64.0D, SignatureFusionBalance.effectiveAttack(500.0D), 0.0001D);
        assertEquals(0.0D, SignatureFusionBalance.effectiveAttack(-1.0D), 0.0001D);
    }

    @Test
    void witheredDriveAndPiercingVoidMoonRespectHardCaps() {
        assertEquals(9.0F, SignatureFusionBalance.witheredFirstDrive(500.0D), 0.0001F);
        assertEquals(6.5F, SignatureFusionBalance.witheredSecondDrive(500.0D), 0.0001F);
        assertEquals(9.0F, SignatureFusionBalance.piercingHit(500.0D), 0.0001F);
        assertEquals(11.5F, SignatureFusionBalance.voidClosure(500.0D), 0.0001F);
        assertTrue(SignatureFusionBalance.witheredFirstDrive(24.0D)
                > SignatureFusionBalance.witheredSecondDrive(24.0D));
    }

    @Test
    void tsukumoCrossPreservesNativeSlashBladeProjectileParameters() {
        assertEquals(0.40D, TsukumoCrossNativeHandler.WAVE_EDGE_DAMAGE, 0.0001D);
        assertEquals(1.50D, TsukumoCrossNativeHandler.DRIVE_HORIZONTAL_DAMAGE, 0.0001D);
        assertEquals(2L, TsukumoCrossNativeHandler.VERTICAL_DELAY);
        assertEquals(6L, TsukumoCrossNativeHandler.HORIZONTAL_DELAY);
        assertEquals(9L, TsukumoCrossNativeHandler.CLOSE_DELAY);
    }

    @Test
    void delayedPathValidationUsesFiniteSegmentDistance() {
        Vec3 start = new Vec3(0.0D, 0.0D, 0.0D);
        Vec3 end = new Vec3(4.0D, 0.0D, 0.0D);

        assertEquals(1.0D,
                SignatureFusionBatchHandler.distanceToSegment(
                        new Vec3(2.0D, 0.0D, 1.0D), start, end),
                0.0001D);
        assertEquals(2.0D,
                SignatureFusionBatchHandler.distanceToSegment(
                        new Vec3(6.0D, 0.0D, 0.0D), start, end),
                0.0001D);
    }
}
