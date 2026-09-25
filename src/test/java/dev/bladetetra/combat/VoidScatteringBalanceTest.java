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
    void returnAndResidualCountersStayBounded() {
        assertEquals(6.6F,
                VoidScatteringBalance.returnSwordDamage(20.0D, 12, false), 1.0E-4F);
        assertEquals(7.59F,
                VoidScatteringBalance.returnSwordDamage(20.0D, 12, true), 1.0E-4F);
        assertEquals(12.0F,
                VoidScatteringBalance.returnSwordDamage(100.0D, 12, false), 1.0E-4F);
        assertEquals(13.8F,
                VoidScatteringBalance.returnSwordDamage(1000.0D, 12, true), 1.0E-4F);

        assertEquals(3.4F,
                VoidScatteringBalance.residualCounterSwordDamage(20.0D), 1.0E-4F);
        assertEquals(8.0F,
                VoidScatteringBalance.residualCounterSwordDamage(100.0D), 1.0E-4F);
        assertEquals(2.6F,
                VoidScatteringBalance.residualFallbackSwordDamage(20.0D), 1.0E-4F);
        assertEquals(6.0F,
                VoidScatteringBalance.residualFallbackSwordDamage(100.0D), 1.0E-4F);
    }

    @Test
    void defensiveWindowsKeepTheirApprovedMultipliers() {
        assertEquals(9.0F, VoidScatteringBalance.reducedDamage(20.0F), 1.0E-4F);
        assertEquals(12.0F, VoidScatteringBalance.residualReducedDamage(20.0F), 1.0E-4F);
        assertEquals(0.0F, VoidScatteringBalance.reducedDamage(0.0F), 1.0E-4F);
        assertEquals(0.0F, VoidScatteringBalance.residualReducedDamage(-3.0F), 1.0E-4F);
    }

    @Test
    void incomingDamageMapsToBoundedVoidCharge() {
        assertEquals(0, VoidScatteringBalance.voidChargeForIncomingDamage(0.0F));
        assertEquals(1, VoidScatteringBalance.voidChargeForIncomingDamage(1.0F));
        assertEquals(1, VoidScatteringBalance.voidChargeForIncomingDamage(4.0F));
        assertEquals(2, VoidScatteringBalance.voidChargeForIncomingDamage(5.0F));
        assertEquals(2, VoidScatteringBalance.voidChargeForIncomingDamage(10.0F));
        assertEquals(3, VoidScatteringBalance.voidChargeForIncomingDamage(11.0F));
        assertEquals(3, VoidScatteringBalance.voidChargeForIncomingDamage(20.0F));
        assertEquals(4, VoidScatteringBalance.voidChargeForIncomingDamage(21.0F));
        assertEquals(4, VoidScatteringBalance.voidChargeForIncomingDamage(200.0F));
    }

    @Test
    void runtimeConstantsMatchCounterRedesign() {
        assertEquals(200, VoidScatteringFusionHandler.DOMAIN_DURATION_TICKS);
        assertEquals(400, VoidScatteringFusionHandler.DOMAIN_COOLDOWN_TICKS);
        assertEquals(6, VoidScatteringFusionHandler.MAX_STORED_SWORDS);
        assertEquals(12, VoidScatteringFusionHandler.MAX_VOID_CHARGE);
        assertEquals(2, VoidScatteringFusionHandler.VOID_CHARGE_PER_SWORD);
        assertEquals(5, VoidScatteringFusionHandler.AUTO_BREAK_CAPTURE_COUNT);
        assertEquals(8, VoidScatteringFusionHandler.SOURCE_CAPTURE_INTERVAL_TICKS);
        assertEquals(8, VoidScatteringFusionHandler.RESIDUAL_DURATION_TICKS);
        assertEquals(60, VoidScatteringFusionHandler.RESIDUAL_COOLDOWN_TICKS);
        assertEquals(10, VoidScatteringFusionHandler.RESIDUAL_DOMAIN_REFUND_TICKS);
        assertEquals(7, VoidScatteringFusionHandler.COUNTER_WINDUP_TICKS);
        assertEquals(3, VoidScatteringFusionHandler.COUNTER_STAGGER_TICKS);
        assertEquals(2, VoidScatteringFusionHandler.RETURN_DAMAGE_MAX_ATTEMPTS);
    }

    @Test
    void fifthEffectiveCaptureBreaksDomain() {
        assertFalse(VoidScatteringFusionHandler.shouldAutoRelease(0));
        assertFalse(VoidScatteringFusionHandler.shouldAutoRelease(4));
        assertTrue(VoidScatteringFusionHandler.shouldAutoRelease(5));
        assertTrue(VoidScatteringFusionHandler.shouldAutoRelease(6));
    }

    @Test
    void failedReturnDamageGetsOnlyOneRetry() {
        assertTrue(VoidScatteringFusionHandler.shouldRetryReturnDamage(false, 1));
        assertFalse(VoidScatteringFusionHandler.shouldRetryReturnDamage(false, 2));
        assertFalse(VoidScatteringFusionHandler.shouldRetryReturnDamage(true, 1));
    }

    @Test
    void returnSwordUsesRealVisualFlightWithoutNativeAttachmentBurst()
            throws Exception {
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/bladetetra/combat/VoidScatteringReturnRuntime.java"));
        assertFalse(runtime.contains("sword.setHitEntity(target)"),
                "Pre-attaching a summoned sword skips flight and enters SlashBlade's burst path");
        assertTrue(runtime.contains("pending.visualEntityId = flight.entityId()"));
        assertTrue(runtime.contains("discardVisualSword(level, pending);"));
        assertTrue(runtime.contains("LegacyFusionCombatSupport.markVisualOnly(sword)"));
    }

    @Test
    void visualSourceAndRuntimeShaderResourcesArePackaged() {
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/textures/gui/void_scattering_ready.png"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_rift.json"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_rift.vsh"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_rift.fsh"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_dome.json"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_dome.vsh"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_dome.fsh"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_veil.json"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_veil.vsh"));
        assertNotNull(getClass().getResource(
                "/assets/blade_tetra/shaders/core/void_scattering_veil.fsh"));
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
