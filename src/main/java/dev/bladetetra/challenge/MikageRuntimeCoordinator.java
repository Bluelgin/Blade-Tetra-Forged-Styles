package dev.bladetetra.challenge;

import dev.bladetetra.config.GameplayConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Central per-tick orchestration for Mikage's composed combat subsystems. */
final class MikageRuntimeCoordinator {
    static void tick(MikageEntity owner, ServerLevel server, int phase) {
        MikageCombatDirector combat = owner.combatDirector();
        MikageDefenseController defense = owner.defenseController();
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        MikageArenaController arena = owner.arenaController();

        owner.sampleParticipantMovement(server);
        owner.attackTimeline().tick(server);
        if (combat.preparedTicks > 0) {
            owner.tickPreparedTechnique(server);
        }

        if (arena.toriiSweepCooldown > 0) arena.toriiSweepCooldown--;
        if (arena.toriiCageCooldown > 0) arena.toriiCageCooldown--;
        if (techniques.pursuitRainCooldown > 0) techniques.pursuitRainCooldown--;
        if (techniques.mirrorDuelCooldown > 0) techniques.mirrorDuelCooldown--;
        if (techniques.boundarySealCooldown > 0) techniques.boundarySealCooldown--;
        if (techniques.moonEchoCooldown > 0) techniques.moonEchoCooldown--;
        if (defense.mirrorCounterCooldown > 0) defense.mirrorCounterCooldown--;
        if (combat.phaseProtectionTicks > 0) combat.phaseProtectionTicks--;
        if (arena.boundaryFlashReadyTicks > 0) arena.boundaryFlashReadyTicks--;

        if (owner.tickCount % 100 == 0) {
            long now = owner.level().getGameTime();
            defense.hurtCooldownUntil.entrySet()
                    .removeIf(entry -> entry.getValue() <= now);
            defense.pursuitPressure.entrySet().removeIf(entry ->
                    entry.getValue().lastHit != Long.MIN_VALUE
                            && now - entry.getValue().lastHit > 160L);
        }

        owner.tickJudgementCutAdaptation(server);
        owner.tickBrokenLocks(server);
        owner.tickSwordWheel(server);

        if (combat.signatureRecoveryTicks > 0) {
            combat.signatureRecoveryTicks--;
            if (arena.cagePerfectCountered || arena.toriiScissorCountered
                    || techniques.pursuitRainCountered
                    || defense.swordWheelBreakTicks > 0) {
                owner.getNavigation().stop();
                owner.setDeltaMovement(Vec3.ZERO);
            }
            if (combat.signatureRecoveryTicks == 0
                    && (arena.cagePerfectCountered || arena.toriiScissorCountered
                        || techniques.pursuitRainCountered)
                    && defense.swordWheelBreakTicks <= 0) {
                owner.setAction(MikageEntity.MikageAction.IDLE, 1);
                owner.flashStepAwayFromNearestPlayer(server);
                arena.cagePerfectCountered = false;
                arena.toriiScissorCountered = false;
                techniques.pursuitRainCountered = false;
            }
        }

        if (techniques.interactionOpeningTicks > 0) {
            techniques.interactionOpeningTicks--;
            owner.getNavigation().stop();
            owner.setDeltaMovement(Vec3.ZERO);
            if (techniques.interactionOpeningTicks == 0) {
                techniques.interactionOpeningMultiplier = 1.0F;
                owner.setAction(MikageEntity.MikageAction.IDLE, 1);
                owner.flashStepAwayFromNearestPlayer(server);
            }
        }

        if (owner.shouldResetExpiredAction()) {
            owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        }

        if (arena.toriiSweepTicks > 0) owner.tickToriiSweep(server);
        if (arena.toriiCageTicks > 0) owner.tickToriiCages(server);
        if (techniques.pursuitRainTicks > 0) owner.tickPursuitRain(server);
        if (techniques.pursuitRainFinalTicks > 0) owner.tickPursuitRainFinal(server);
        if (techniques.mirrorDuelTicks > 0) owner.tickMirrorDuel(server);
        if (techniques.boundarySealTicks > 0) owner.tickBoundarySeal(server);
        if (techniques.moonEchoTicks > 0) owner.tickMoonEcho(server);
        if (defense.zanshinCounterTicks > 0) owner.tickZanshinCounter(server);
        if (techniques.aerialTicks > 0) owner.tickAerialTechnique();
        if (arena.boundarySlashDelay > 0) owner.tickBoundaryFlash(server);
        if (!arena.boundaryWalls.isEmpty()) owner.tickBoundaryWalls(server);

        if (arena.boundaryFlashPending && arena.boundaryFlashReadyTicks <= 0
                && phase == 3 && combat.phaseProtectionTicks <= 0
                && !owner.isUsingTechnique()) {
            ServerPlayer executionTarget = owner.nearestChallengeParticipant(server);
            if (executionTarget != null) {
                arena.boundaryFlashPending = false;
                owner.beginBoundaryFlash(executionTarget, server);
            }
        }

        if (!owner.isUsingTechnique() && techniques.pursuitRainCooldown <= 0) {
            ServerPlayer pursuitTarget = owner.selectPursuitTarget(server);
            if (pursuitTarget != null) {
                owner.beginPursuitRain(pursuitTarget, server);
            }
        }

        if (!owner.isUsingTechnique() && --combat.techniqueCooldown <= 0) {
            owner.useTechnique(phase);
            combat.techniqueCooldown = combat.scaledCooldown(
                    phase == 1 ? 48 : phase == 2 ? 36 : 26);
        }
        owner.enforceArenaBoundary();
    }

    private MikageRuntimeCoordinator() {
    }
}
