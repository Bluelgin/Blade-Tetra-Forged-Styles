package dev.bladetetra.challenge;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runtime state for multi-tick Mikage techniques outside arena geometry. */
final class MikageTechniqueRuntime {
    int aerialTicks;
    LivingEntity aerialTarget;

    UUID pursuitRainTarget;
    int pursuitRainTicks;
    int pursuitRainActiveTicks;
    int pursuitRainCooldown;
    int pursuitRainWave;
    int pursuitRainFinalTicks;
    UUID pursuitRainFinalSword;
    Vec3 pursuitRainFinalOrigin = Vec3.ZERO;
    Vec3 pursuitRainFinalAim = Vec3.ZERO;
    boolean pursuitRainFinalLaunched;
    boolean pursuitRainCountered;

    int mirrorDuelTicks;
    int mirrorDuelCooldown;
    UUID mirrorDuelTarget;
    Vec3 mirrorDuelStart = Vec3.ZERO;
    Vec3 mirrorDuelEnd = Vec3.ZERO;

    int boundarySealTicks;
    int boundarySealCooldown = 220;
    int boundarySealsBroken;
    final List<UUID> boundarySealEntities = new ArrayList<>();

    int moonEchoTicks;
    int moonEchoCooldown = 180;
    final List<UUID> moonEchoEntities = new ArrayList<>();

    int interactionOpeningTicks;
    float interactionOpeningMultiplier = 1.0F;
    boolean isSignatureActive(MikageArenaController arena,
            MikageDefenseController defense) {
        return interactionOpeningTicks > 0 || aerialTicks > 0
                || arena.boundarySlashDelay > 0
                || arena.toriiSweepTicks > 0 || arena.toriiCageTicks > 0
                || pursuitRainTicks > 0 || pursuitRainFinalTicks > 0
                || mirrorDuelTicks > 0 || boundarySealTicks > 0 || moonEchoTicks > 0
                || defense.zanshinCounterTicks > 0;
    }

    boolean isAnyActive(MikageArenaController arena,
            MikageDefenseController defense, MikageCombatDirector combat) {
        return isSignatureActive(arena, defense)
                || ((arena.cagePerfectCountered || arena.toriiScissorCountered
                        || pursuitRainCountered)
                    && combat.signatureRecoveryTicks > 0)
                || combat.preparedTicks > 0;
    }
}
