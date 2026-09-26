package dev.bladetetra.challenge;

import net.minecraft.world.entity.LivingEntity;

/** High-level technique selection, pacing and phase-protection state. */
final class MikageCombatDirector {
    int techniqueCooldown = 40;
    MikageEntity.Technique lastTechnique = MikageEntity.Technique.NONE;
    MikageEntity.Technique previousTechnique = MikageEntity.Technique.NONE;
    int signatureRecoveryTicks;
    MikageEntity.Technique preparedTechnique = MikageEntity.Technique.NONE;
    LivingEntity preparedTarget;
    int preparedTicks;
    int closePressureHits;
    int lastClosePressureTick = Integer.MIN_VALUE;
    int phaseProtectionTicks;
    double skillSpeedMultiplier = 1.0D;
    int scaledCooldown(int baseTicks) {
        return Math.max(1, net.minecraft.util.Mth.ceil(
                baseTicks / Math.max(1.0D, skillSpeedMultiplier)));
    }
    MikageEntity.Technique selectPhaseThreeRotation(
            MikageArenaController arena,
            MikageTechniqueRuntime techniques,
            boolean lowHealth,
            boolean guarding) {
        int slot = Math.floorMod(arena.boundaryFlashCharge, 3);
        int variant = Math.floorMod(arena.boundaryFlashCycle, 3);
        if (slot == 0) {
            if (variant != 1 && arena.toriiSweepCooldown <= 0) {
                return MikageEntity.Technique.TORII_SWEEP;
            }
            if (techniques.boundarySealCooldown <= 0) {
                return MikageEntity.Technique.BOUNDARY_SEAL;
            }
            if (arena.toriiSweepCooldown <= 0) {
                return MikageEntity.Technique.TORII_SWEEP;
            }
            return MikageEntity.Technique.DANGAKU_CLEAVE;
        }
        if (slot == 1) {
            if (variant == 0 && arena.toriiCageCooldown <= 0 && !lowHealth) {
                return MikageEntity.Technique.TORII_CAGE;
            }
            if (variant == 1 && techniques.moonEchoCooldown <= 0) {
                return MikageEntity.Technique.MOON_ECHO;
            }
            if (techniques.mirrorDuelCooldown <= 0) {
                return MikageEntity.Technique.MIRROR_DUEL;
            }
            if (techniques.moonEchoCooldown <= 0) {
                return MikageEntity.Technique.MOON_ECHO;
            }
            if (arena.toriiCageCooldown <= 0 && !lowHealth) {
                return MikageEntity.Technique.TORII_CAGE;
            }
            return guarding
                    ? MikageEntity.Technique.FLASH_COUNTER
                    : MikageEntity.Technique.STEP_IAIDO;
        }
        return switch (variant) {
            case 1 -> MikageEntity.Technique.AERIAL_RAIN;
            case 2 -> MikageEntity.Technique.SUMMONED_VOLLEY;
            default -> MikageEntity.Technique.SUPER_JUDGEMENT;
        };
    }

    static void addWeighted(java.util.List<MikageEntity.Technique> choices,
            MikageEntity.Technique technique, int weight) {
        for (int i = 0; i < weight; i++) {
            choices.add(technique);
        }
    }
}
