package dev.bladetetra.combat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoidScatteringBalanceTest {
    @Test
    void effectiveAttackUsesSharedSoftScalingFamily() {
        assertEquals(10.0D, VoidScatteringBalance.effectiveAttack(10.0D), 1.0E-6D);
        assertEquals(24.0D, VoidScatteringBalance.effectiveAttack(24.0D), 1.0E-6D);
        assertEquals(26.4D, VoidScatteringBalance.effectiveAttack(30.0D), 1.0E-6D);
        assertEquals(54.4D, VoidScatteringBalance.effectiveAttack(100.0D), 1.0E-6D);
        assertEquals(64.0D, VoidScatteringBalance.effectiveAttack(1000.0D), 1.0E-6D);
    }

    @Test
    void returnAndResidualSwordsStayBounded() {
        assertEquals(4.4F, VoidScatteringBalance.returnSwordDamage(20.0D), 1.0E-4F);
        assertEquals(10.0F, VoidScatteringBalance.returnSwordDamage(100.0D), 1.0E-4F);
        assertEquals(10.0F, VoidScatteringBalance.returnSwordDamage(1000.0D), 1.0E-4F);

        assertEquals(3.0F, VoidScatteringBalance.residualSwordDamage(20.0D), 1.0E-4F);
        assertEquals(7.0F, VoidScatteringBalance.residualSwordDamage(100.0D), 1.0E-4F);
        assertEquals(7.0F, VoidScatteringBalance.residualSwordDamage(1000.0D), 1.0E-4F);
    }

    @Test
    void domainLeavesFortyFivePercentOfEligibleDamage() {
        assertEquals(9.0F, VoidScatteringBalance.reducedDamage(20.0F), 1.0E-4F);
        assertEquals(0.0F, VoidScatteringBalance.reducedDamage(0.0F), 1.0E-4F);
        assertEquals(0.0F, VoidScatteringBalance.reducedDamage(-3.0F), 1.0E-4F);
    }

    @Test
    void runtimeConstantsMatchApprovedFirstPass() {
        assertEquals(60, VoidScatteringFusionHandler.DOMAIN_DURATION_TICKS);
        assertEquals(280, VoidScatteringFusionHandler.DOMAIN_COOLDOWN_TICKS);
        assertEquals(5, VoidScatteringFusionHandler.MAX_STORED_SWORDS);
        assertEquals(8, VoidScatteringFusionHandler.SOURCE_CAPTURE_INTERVAL_TICKS);
        assertEquals(5, VoidScatteringFusionHandler.RESIDUAL_DURATION_TICKS);
        assertEquals(36, VoidScatteringFusionHandler.RESIDUAL_COOLDOWN_TICKS);
        assertEquals(2, VoidScatteringFusionHandler.RETURN_DAMAGE_MAX_ATTEMPTS);
    }

    @Test
    void failedReturnDamageGetsOnlyOneRetry() {
        assertTrue(VoidScatteringFusionHandler.shouldRetryReturnDamage(false, 1));
        assertFalse(VoidScatteringFusionHandler.shouldRetryReturnDamage(false, 2));
        assertFalse(VoidScatteringFusionHandler.shouldRetryReturnDamage(true, 1));
    }

    @Test
    void visualSourceAndRuntimeIconArePackaged() {
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/textures/gui/void_scattering_ready.png"));
        assertTrue(Files.isRegularFile(Path.of(
                "art/void_scattering/void_ready_icon.svg")));
        assertTrue(Files.isRegularFile(Path.of(
                "art/void_scattering/void_slot_empty.svg")));
        assertTrue(Files.isRegularFile(Path.of(
                "art/void_scattering/void_slot_filled.svg")));
        assertTrue(Files.isRegularFile(Path.of(
                "art/void_scattering/void_bloom_rift.svg")));
    }
}
