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
}
