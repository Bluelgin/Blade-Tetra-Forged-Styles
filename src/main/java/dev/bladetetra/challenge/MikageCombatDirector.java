package dev.bladetetra.challenge;


/** Legacy pacing/protection state; selection lives in ReactiveCombatDirector. */
final class MikageCombatDirector {
    int techniqueCooldown = 40;
    int signatureRecoveryTicks;
    int closePressureHits;
    int lastClosePressureTick = Integer.MIN_VALUE;
    int phaseProtectionTicks;
    double skillSpeedMultiplier = 1.0D;
    int scaledCooldown(int baseTicks) {
        return Math.max(1, net.minecraft.util.Mth.ceil(
                baseTicks / Math.max(1.0D, skillSpeedMultiplier)));
    }



}
