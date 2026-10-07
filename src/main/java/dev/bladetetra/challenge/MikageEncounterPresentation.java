package dev.bladetetra.challenge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import dev.bladetetra.network.BladeCombatVfxPacket;
import static dev.bladetetra.challenge.MikageEntity.*;

/** Presentation consumes server state and never makes combat decisions. */
final class MikageEncounterPresentation {
    static void syncHud(MikageEntity owner) {
        var techniques = owner.techniqueRuntime();
        var arena = owner.arenaController();
        if (owner.tickCount % 2 == 0) {
            int technique = techniques.boundarySealTicks > 0 ? 6
                    : techniques.moonEchoTicks > 0 ? 7
                    : techniques.mirrorDuelTicks > 0 ? 8
                    : arena.toriiSweepTicks > 0 ? 2
                    : arena.toriiCageTicks > 0 ? 3
                    : techniques.pursuitRainFinalTicks > 0 ? 5
                    : techniques.pursuitRainTicks > 0 ? 4
                    : arena.boundarySlashDelay > 0 ? 1 : 0;
            int remaining = technique == 6 ? techniques.boundarySealTicks
                    : technique == 7 ? techniques.moonEchoTicks
                    : technique == 8 ? techniques.mirrorDuelTicks
                    : technique == 2 ? arena.toriiSweepTicks
                    : technique == 3 ? arena.toriiCageTicks
                    : technique == 5 ? techniques.pursuitRainFinalTicks
                    : technique == 4 ? techniques.pursuitRainTicks : arena.boundarySlashDelay;
            int total = technique == 6 ? BOUNDARY_SEAL_TOTAL_TICKS
                    : technique == 7 ? MOON_ECHO_TOTAL_TICKS
                    : technique == 8 ? MIRROR_DUEL_TOTAL_TICKS
                    : technique == 2 ? TORII_SWEEP_TOTAL_TICKS
                    : technique == 3 ? TORII_CAGE_TOTAL_TICKS
                    : technique == 5 ? PURSUIT_RAIN_FINAL_TICKS
                    : technique == 4 ? techniques.pursuitRainActiveTicks + PURSUIT_RAIN_WARNING_TICKS
                    : technique == 1 ? BOUNDARY_FLASH_TOTAL_TICKS : 0;
            ChallengeManager.syncHud(owner, technique, remaining, total);
        }
    }

    static void defeated(MikageEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        boolean reminiscence = owner.getPersistentData().getBoolean("blade_tetra_reminiscence");
        owner.sendCombatVfx(level, BladeCombatVfxPacket.MIKAGE_DEFEAT,
                owner.position().add(0, 1, 0), owner.getYRot(),
                reminiscence ? 1.15F : 1, reminiscence ? 1 : 0);
    }

    private MikageEncounterPresentation() {}
}
