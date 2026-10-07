package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageLegacyTiming.*;

import static dev.bladetetra.challenge.MikageArenaController.*;

import dev.bladetetra.network.BladeCombatVfxPacket;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Packets, sounds, HUD and tracking snapshots; never makes combat decisions. */
final class MikageEncounterPresentation {
    private final MikageEntity owner;
    private final MikageArenaController arena;

    MikageEncounterPresentation(MikageEntity owner) {
        this.owner = owner;
        arena = owner.arenaController();
    }

    void sendCombatVfx(ServerLevel server, int type, Vec3 position,
            float yaw, float intensity, int focusEntityId) {
        BladeCombatVfxPacket packet = new BladeCombatVfxPacket(type,
                position.x, position.y, position.z, yaw, intensity, focusEntityId);
        for (ServerPlayer viewer : server.players()) {
            if (viewer.level() == server
                    && ChallengeManager.isParticipant(owner, viewer)
                    && viewer.distanceToSqr(position) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    void sendTechniqueVfx(ServerLevel server, BladeTechniqueVfxPacket packet,
            Vec3 center) {
        for (ServerPlayer viewer : server.players()) {
            if (viewer.level() == server
                    && ChallengeManager.isParticipant(owner, viewer)
                    && viewer.distanceToSqr(center) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    void playBladeParrySound(ServerLevel server, Vec3 position,
            SoundSource source, boolean perfect) {
        BlockPos soundPos = BlockPos.containing(position);
        server.playSound(null, soundPos, SoundEvents.TRIDENT_HIT, source,
                perfect ? 1.35F : 1.05F, perfect ? 1.28F : 1.48F);
        server.playSound(null, soundPos, SoundEvents.PLAYER_ATTACK_CRIT, source,
                perfect ? 1.05F : 0.72F, perfect ? 0.72F : 0.92F);
        server.playSound(null, soundPos, SoundEvents.ANVIL_LAND, source,
                perfect ? 0.42F : 0.24F, perfect ? 1.72F : 1.92F);
    }

    void startSeenByPlayer(ServerPlayer player) {
        if (!owner.isVisitorGuide()) {
            owner.bossBar().addPlayer(player);
            if (owner.level() instanceof ServerLevel
                    && ChallengeManager.isParticipant(owner, player)) {
                for (BoundaryWallState wall : arena.boundaryWalls) {
                    Vec3 edge = wall.center.add(
                            wall.direction.scale(BOUNDARY_FLASH_LENGTH));
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new BladeTechniqueVfxPacket(
                                    BladeTechniqueVfxPacket.BOUNDARY_WALL,
                                    wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                    edge.x, wall.center.y + 0.05D, edge.z,
                                    owner.getYRot(), 1.0F, -1, -1,
                                    BOUNDARY_WALL_VISUAL_TICKS, wall.id));
                    if (wall.gapTicks > 0) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new BladeTechniqueVfxPacket(
                                        BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP,
                                        wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                        edge.x, wall.center.y + 0.05D, edge.z,
                                        owner.getYRot(), (float) wall.gapAlong, -1, -1,
                                        wall.gapTicks, wall.id));
                    } else if (wall.gapWarningTicks > 0) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new BladeTechniqueVfxPacket(
                                        BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP_WARNING,
                                        wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                        edge.x, wall.center.y + 0.05D, edge.z,
                                        owner.getYRot(), (float) wall.pendingGapAlong, -1, -1,
                                        wall.gapWarningTicks, wall.id));
                    }
                }
            }
        }
    }

    void stopSeenByPlayer(ServerPlayer player) {
        owner.bossBar().removePlayer(player);
    }

    void syncHud() {
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

    void defeated() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        boolean reminiscence = owner.getPersistentData().getBoolean("blade_tetra_reminiscence");
        sendCombatVfx(level, BladeCombatVfxPacket.MIKAGE_DEFEAT,
                owner.position().add(0, 1, 0), owner.getYRot(),
                reminiscence ? 1.15F : 1, reminiscence ? 1 : 0);
    }

    void phaseName(int phase) {
        owner.bossBar().setName(Component.translatable(phase == 1
                ? "entity.blade_tetra.mikage" : "entity.blade_tetra.mikage.phase" + phase));
    }

    void phaseShift(ServerLevel server, int phase) {
        Vec3 center = owner.position().add(0.0D, 1.0D, 0.0D);
        sendCombatVfx(server, BladeCombatVfxPacket.PHASE_SHIFT, center,
                owner.getYRot(), phase == 3 ? 1.18F : 1.0F, phase);
        server.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 0.9F, phase == 3 ? 0.62F : 0.74F);
        server.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_THUNDER,
                SoundSource.HOSTILE, 0.55F, phase == 3 ? 1.18F : 1.32F);
    }
}
