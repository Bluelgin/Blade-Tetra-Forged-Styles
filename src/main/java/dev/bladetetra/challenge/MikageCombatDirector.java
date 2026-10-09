package dev.bladetetra.challenge;

/** Shared encounter pacing/protection. Selection lives in ReactiveCombatDirector. */
final class MikageCombatDirector {
    int techniqueCooldown = 40;
    int phaseProtectionTicks;
    double skillSpeedMultiplier = 1;
    int scaledCooldown(int baseTicks) {
        return Math.max(1, net.minecraft.util.Mth.ceil(baseTicks / Math.max(1, skillSpeedMultiplier)));
    }
}
