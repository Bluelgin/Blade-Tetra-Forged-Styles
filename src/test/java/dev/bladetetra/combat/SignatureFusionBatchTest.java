package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignatureFusionBatchTest {
    @Test
    void witheredDriveUsesNativeVerticalDriveAndKosekiWitherSignature() {
        assertEquals(1.50D, SignatureFusionBatchHandler.WITHERED_DRIVE_DAMAGE, 0.0001D);
        assertEquals(100, SignatureFusionBatchHandler.WITHERED_DURATION_TICKS);
        assertEquals(1, SignatureFusionBatchHandler.WITHERED_AMPLIFIER);
    }

    @Test
    void piercingVoidMoonUsesNativePiercingAndThreeBoundedSwords() {
        assertEquals(24L, SignatureFusionBatchHandler.PIERCING_TRIGGER_WINDOW);
        assertEquals(3, SignatureFusionBatchHandler.PIERCING_VOID_SWORD_COUNT);
        assertEquals(0.32D, SignatureFusionBatchHandler.PIERCING_VOID_SWORD_DAMAGE,
                0.0001D);
    }

    @Test
    void tsukumoCrossPreservesNativeSlashBladeProjectileParameters() {
        assertEquals(0.40D, TsukumoCrossNativeHandler.WAVE_EDGE_DAMAGE, 0.0001D);
        assertEquals(1.50D, TsukumoCrossNativeHandler.DRIVE_HORIZONTAL_DAMAGE, 0.0001D);
        assertEquals(2L, TsukumoCrossNativeHandler.VERTICAL_DELAY);
        assertEquals(6L, TsukumoCrossNativeHandler.HORIZONTAL_DELAY);
        assertEquals(9L, TsukumoCrossNativeHandler.CLOSE_DELAY);
    }

}
