package dev.bladetetra.challenge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Explicit cancellation for old effects until each is replaced by an owned skill. */
final class MikageEncounterCleanup {
    static void foreground(MikageEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        var t = owner.techniqueRuntime();
        var a = owner.arenaController();
        var d = owner.defenseController();
        var c = owner.combatDirector();
        if (t.pursuitRainFinalSword != null) {
            var sword = level.getEntity(t.pursuitRainFinalSword);
            if (sword != null) sword.discard();
        }
        owner.legacyEffects().clearBoundarySeals(level);
        owner.legacyEffects().clearMoonEchoes(level);
        t.aerialTicks = t.pursuitRainTicks = t.pursuitRainFinalTicks = 0;
        t.mirrorDuelTicks = t.boundarySealTicks = t.moonEchoTicks = 0;
        t.aerialTarget = null;
        t.pursuitRainTarget = t.pursuitRainFinalSword = t.mirrorDuelTarget = null;
        t.pursuitRainCountered = false;
        t.pursuitRainFinalLaunched = false;
        t.interactionOpeningTicks = 0;
        t.interactionOpeningMultiplier = 1;
        a.boundarySlashDelay = a.toriiSweepTicks = a.toriiCageTicks = 0;
        a.toriiCages.clear();
        a.toriiScissorStates.clear();
        a.boundaryGuardHoldTicks.clear();
        a.cagePerfectCountered = a.toriiScissorCountered = false;
        d.zanshinCounterTicks = 0;
        d.zanshinCounterTarget = null;
        d.stepIaidoCommitted = false;
        c.signatureRecoveryTicks = 0;
        owner.setNoGravity(false);
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
    }

    static void encounter(MikageEntity owner) {
        owner.duel().clear();
        foreground(owner);
        owner.attackTimeline().clear();
        if (owner.level() instanceof ServerLevel level) {
            owner.legacyEffects().clearBoundaryWalls(level, true);
            owner.swordWheel().recallSwordWheel(level, 35);
        }
        var a = owner.arenaController();
        a.boundaryFlashPending = false;
        a.boundaryFlashCharge = a.boundaryFlashReadyTicks = 0;
        var d = owner.defenseController();
        d.saPatterns.clear();
        d.judgementPatterns.clear();
        d.hurtCooldownUntil.clear();
        d.lockBreakUntil.clear();
        d.pursuitPressure.clear();
        d.movementSamples.clear();
        d.shadowCrossPatterns.clear();
        d.playerDefenseProfiles.clear();
        d.swordWheelDeployTicks = d.swordWheelCounterCooldown = d.swordWheelBreakTicks = 0;
    }

    private MikageEncounterCleanup() {}
}
