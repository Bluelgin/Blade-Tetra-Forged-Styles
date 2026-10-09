package dev.bladetetra.challenge;

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

    MikageEncounterPresentation(MikageEntity owner) {
        this.owner = owner;
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
        }
    }

    void stopSeenByPlayer(ServerPlayer player) {
        owner.bossBar().removePlayer(player);
    }

    void syncHud() {
        if (owner.tickCount % 2 != 0) return;
        var echo = owner.encounter().echo(); var boundary = owner.encounter().boundary();
        ChallengeManager.syncHud(owner, echo != null ? 7 : boundary != null ? 1 : 0,
                echo != null ? echo.remaining() : boundary != null ? boundary.remaining() : 0,
                echo != null ? 66 : boundary != null ? MikageBoundaryExecution.TOTAL : 0);
    }

    void defeated() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        boolean reminiscence = owner.getPersistentData().getBoolean("blade_tetra_reminiscence");
        sendCombatVfx(level, BladeCombatVfxPacket.MIKAGE_DEFEAT,
                owner.position().add(0, 1, 0), owner.getYRot(),
                reminiscence ? 1.15F : 1, reminiscence ? 1 : 0);
    }

    void swordContact(ServerPlayer player, boolean parry) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        Vec3 contact = owner.position().lerp(player.position(), .5).add(0, 1.1, 0);
        sendCombatVfx(level, parry ? BladeCombatVfxPacket.PERFECT_GUARD : BladeCombatVfxPacket.PARRY,
                contact, player.getYRot(), parry ? 1.12F : .9F, player.getId());
        playBladeParrySound(level, contact, SoundSource.HOSTILE, parry);
    }

    void balanceBroken() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        sendCombatVfx(level, BladeCombatVfxPacket.STAGGER, owner.position().add(0, 1, 0),
                owner.getYRot(), 1.25F, owner.getId());
        level.playSound(null, owner.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1, .7F);
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
