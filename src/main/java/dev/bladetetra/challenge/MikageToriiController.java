package dev.bladetetra.challenge;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.BladeCombatVfxPacket;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;

import static dev.bladetetra.challenge.MikageArenaController.CageState;
import static dev.bladetetra.challenge.MikageArenaController.ToriiScissorState;

/**
 * Owns Mikage's torii sweep and cage encounter mechanics.
 *
 * <p>Arena state remains in {@link MikageArenaController}; this class turns
 * that state into the two multi-player signature sequences. Keeping the
 * timelines here prevents {@link MikageEntity} from owning both entity
 * lifecycle and authored encounter scripting.</p>
 */
final class MikageToriiController {
    private final MikageEntity owner;

    MikageToriiController(MikageEntity owner) {
        this.owner = owner;
    }

    void beginSweep(ServerLevel server) {
        MikageArenaController arena = owner.arenaController();
        MikageCombatDirector combat = owner.combatDirector();
        owner.attackTimeline().clear();
        arena.toriiSweepCenter = ChallengeManager.arenaCenter(owner);
        arena.toriiSweepTicks = MikageEntity.TORII_SWEEP_TOTAL_TICKS;
        arena.toriiSweepCooldown = combat.scaledCooldown(620);
        arena.toriiScissorStates.clear();
        arena.toriiSweepFocusTarget = null;
        arena.toriiScissorCountered = false;
        owner.getNavigation().stop();
        owner.setAction(
                MikageEntity.MikageAction.RITUAL,
                MikageEntity.TORII_SWEEP_TOTAL_TICKS);
        owner.teleportWithinArena(
                arena.toriiSweepCenter.x,
                arena.toriiSweepCenter.y,
                arena.toriiSweepCenter.z);
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) {
                continue;
            }
            Vec3 aim = player.position().subtract(arena.toriiSweepCenter)
                    .multiply(1.0D, 0.0D, 1.0D);
            float baseYaw = aim.lengthSqr() < 0.0001D
                    ? owner.getYRot()
                    : (float) Math.toDegrees(Math.atan2(aim.z, aim.x));
            arena.toriiScissorStates.put(
                    player.getUUID(), new ToriiScissorState(baseYaw));
            Vec3 target = player.position().add(0.0D, 1.0D, 0.0D);
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.TORII_SWEEP,
                    arena.toriiSweepCenter.x,
                    arena.toriiSweepCenter.y + 1.0D,
                    arena.toriiSweepCenter.z,
                    target.x, target.y, target.z,
                    baseYaw, 1.0F, owner.getId(), player.getId(),
                    MikageEntity.TORII_SWEEP_TOTAL_TICKS,
                    player.getId()), target);
            if (arena.toriiSweepFocusTarget == null) {
                arena.toriiSweepFocusTarget = player.getUUID();
            }
            server.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE,
                    SoundSource.HOSTILE, 0.9F, 0.66F);
        }
        server.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.35F, 0.62F);
        if (!arena.toriiSweepVoiced) {
            arena.toriiSweepVoiced = true;
            ChallengeManager.tryVoice(owner, MikageDialogue.TORII_SWEEP);
        }
    }

    void tickSweep(ServerLevel server) {
        MikageArenaController arena = owner.arenaController();
        MikageCombatDirector combat = owner.combatDirector();
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        if (owner.position().distanceToSqr(arena.toriiSweepCenter) > 0.04D) {
            owner.teleportWithinArena(
                    arena.toriiSweepCenter.x,
                    arena.toriiSweepCenter.y,
                    arena.toriiSweepCenter.z);
        }
        ServerPlayer focus = arena.toriiSweepFocusTarget == null ? null
                : server.getServer().getPlayerList()
                        .getPlayer(arena.toriiSweepFocusTarget);
        if (focus == null || !focus.isAlive()
                || !ChallengeManager.isParticipant(owner, focus)) {
            focus = null;
            for (UUID playerId : arena.toriiScissorStates.keySet()) {
                ServerPlayer candidate = server.getServer()
                        .getPlayerList().getPlayer(playerId);
                if (candidate != null && candidate.isAlive()
                        && ChallengeManager.isParticipant(owner, candidate)
                        && (focus == null
                        || owner.distanceToSqr(candidate) < owner.distanceToSqr(focus))) {
                    focus = candidate;
                }
            }
            arena.toriiSweepFocusTarget =
                    focus == null ? null : focus.getUUID();
        }
        if (focus != null) {
            owner.lookAt(focus, 180.0F, 180.0F);
        }

        int age = MikageEntity.TORII_SWEEP_TOTAL_TICKS - arena.toriiSweepTicks;
        for (Map.Entry<UUID, ToriiScissorState> entry
                : arena.toriiScissorStates.entrySet()) {
            ServerPlayer player = server.getServer().getPlayerList()
                    .getPlayer(entry.getKey());
            if (player == null || !player.isAlive()
                    || !ChallengeManager.isParticipant(owner, player)) {
                continue;
            }
            ToriiScissorState state = entry.getValue();
            if (owner.isBladeGuarding(player)) {
                state.consecutiveGuardTicks++;
            } else {
                state.consecutiveGuardTicks = 0;
            }

            if (age == MikageEntity.TORII_SWEEP_FAKE_IMPACT_AGE
                    && !state.feintPlayed) {
                state.feintPlayed = true;
                Vec3 feint = player.position().add(0.0D, 1.0D, 0.0D);
                owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                        BladeTechniqueVfxPacket.SCISSOR_FEINT,
                        feint.x, feint.y, feint.z,
                        feint.x, feint.y, feint.z,
                        state.baseYaw, 0.82F, -1, player.getId(), 12,
                        owner.getRandom().nextInt()), feint);
                server.playSound(null, player.blockPosition(),
                        SoundEvents.IRON_TRAPDOOR_CLOSE,
                        SoundSource.HOSTILE, 0.75F, 1.65F);
            }
            if (age == MikageEntity.TORII_SWEEP_FIRST_IMPACT_AGE
                    || age == MikageEntity.TORII_SWEEP_SECOND_IMPACT_AGE
                    || age == MikageEntity.TORII_SWEEP_FINAL_IMPACT_AGE) {
                applyScissorImpact(
                        player, state, server,
                        age == MikageEntity.TORII_SWEEP_FINAL_IMPACT_AGE);
            }
        }

        if (--arena.toriiSweepTicks <= 0) {
            arena.toriiSweepTicks = 0;
            combat.signatureRecoveryTicks = arena.toriiScissorCountered
                    ? GameplayConfig.MIKAGE_CAGE_STAGGER_TICKS.get()
                    : 28;
            combat.techniqueCooldown = Math.max(
                    combat.techniqueCooldown, combat.signatureRecoveryTicks);
            arena.toriiScissorStates.clear();
            arena.toriiSweepFocusTarget = null;
            server.playSound(
                    null, owner.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.HOSTILE, 1.0F,
                    arena.toriiScissorCountered ? 0.72F : 1.4F);
            if (arena.toriiScissorCountered) {
                Vec3 breakPoint = owner.position().add(0.0D, 1.0D, 0.0D);
                owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                        BladeTechniqueVfxPacket.SCISSOR_BREAK,
                        breakPoint.x, breakPoint.y, breakPoint.z,
                        breakPoint.x, breakPoint.y, breakPoint.z,
                        owner.getYRot(), 1.25F, -1, -1, 22,
                        owner.getRandom().nextInt()), breakPoint);
                owner.sendCombatVfx(
                        server, BladeCombatVfxPacket.STAGGER,
                        breakPoint, owner.getYRot(), 1.16F, -1);
                owner.setAction(
                        MikageEntity.MikageAction.STAGGERED,
                        combat.signatureRecoveryTicks);
            } else {
                owner.setAction(MikageEntity.MikageAction.IDLE, 1);
            }
        }
    }

    void beginCages(ServerLevel server) {
        MikageArenaController arena = owner.arenaController();
        MikageCombatDirector combat = owner.combatDirector();
        MikageTechniqueRuntime techniques = owner.techniqueRuntime();
        owner.attackTimeline().clear();
        arena.toriiCages.clear();
        if (techniques.pursuitRainFinalSword != null) {
            Entity finalSword = server.getEntity(techniques.pursuitRainFinalSword);
            if (finalSword != null) {
                finalSword.discard();
            }
        }
        techniques.pursuitRainTicks = 0;
        techniques.pursuitRainFinalTicks = 0;
        techniques.pursuitRainTarget = null;
        techniques.pursuitRainFinalSword = null;
        arena.cagePerfectCountered = false;
        arena.toriiCageTicks = MikageEntity.TORII_CAGE_TOTAL_TICKS;
        arena.toriiCageCooldown = combat.scaledCooldown(500);
        owner.getNavigation().stop();
        owner.setAction(
                MikageEntity.MikageAction.RITUAL,
                MikageEntity.TORII_CAGE_TOTAL_TICKS);
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) {
                continue;
            }
            arena.toriiCages.put(
                    player.getUUID(), new CageState(player.position()));
            Vec3 cageCenter = player.position();
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.TORII_CAGE,
                    cageCenter.x, cageCenter.y, cageCenter.z,
                    cageCenter.x, cageCenter.y + 1.0D, cageCenter.z,
                    player.getYRot(), 1.0F, -1, player.getId(),
                    MikageEntity.TORII_CAGE_TOTAL_TICKS,
                    owner.getRandom().nextInt()), cageCenter);
            server.playSound(
                    null, player.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE,
                    SoundSource.HOSTILE, 1.1F, 0.55F);
        }
        if (!arena.toriiCageVoiced) {
            arena.toriiCageVoiced = true;
            ChallengeManager.tryVoice(owner, MikageDialogue.TORII_CAGE);
        }
    }

    void tickCages(ServerLevel server) {
        MikageArenaController arena = owner.arenaController();
        MikageCombatDirector combat = owner.combatDirector();
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        int warningStart = MikageEntity.TORII_CAGE_TOTAL_TICKS
                - MikageEntity.TORII_CAGE_WARNING_TICKS;
        boolean active = arena.toriiCageTicks <= warningStart
                && arena.toriiCageTicks > MikageEntity.TORII_CAGE_RECOVERY_TICKS;
        for (Map.Entry<UUID, CageState> entry : arena.toriiCages.entrySet()) {
            ServerPlayer player = server.getServer().getPlayerList()
                    .getPlayer(entry.getKey());
            if (player == null || !player.isAlive()
                    || !ChallengeManager.isParticipant(owner, player)) {
                continue;
            }
            CageState cage = entry.getValue();
            ToriiParticles.renderCage(
                    server, cage.center, MikageEntity.TORII_CAGE_RADIUS, active);
            if (!active) {
                continue;
            }
            if (owner.isBladeGuarding(player)) {
                cage.guardedTicks++;
                cage.lastGuardTick = owner.tickCount;
                if (owner.tickCount % 6 == 0) {
                    server.sendParticles(
                            ParticleTypes.ENCHANT,
                            player.getX(), player.getY() + 1.0D, player.getZ(),
                            5, 0.35D, 0.55D, 0.35D, 0.0D);
                }
            }
            confineToCage(player, cage.center, server);
            if (arena.toriiCageTicks % 12 == 0) {
                applyCagePulse(
                        player, cage, server,
                        arena.toriiCageTicks
                                <= MikageEntity.TORII_CAGE_RECOVERY_TICKS + 12);
            }
        }

        if (--arena.toriiCageTicks <= 0) {
            arena.toriiCageTicks = 0;
            combat.signatureRecoveryTicks = arena.cagePerfectCountered
                    ? GameplayConfig.MIKAGE_CAGE_STAGGER_TICKS.get()
                    : 0;
            combat.techniqueCooldown = Math.max(
                    combat.techniqueCooldown,
                    arena.cagePerfectCountered
                            ? combat.signatureRecoveryTicks
                            : 18);
            server.playSound(
                    null, owner.blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.HOSTILE, 1.25F, 0.7F);
            arena.toriiCages.clear();
            owner.setAction(
                    arena.cagePerfectCountered
                            ? MikageEntity.MikageAction.STAGGERED
                            : MikageEntity.MikageAction.IDLE,
                    Math.max(1, combat.signatureRecoveryTicks));
            if (arena.cagePerfectCountered) {
                owner.sendCombatVfx(
                        server, BladeCombatVfxPacket.STAGGER,
                        owner.position().add(0.0D, 1.0D, 0.0D),
                        owner.getYRot(), 1.18F, -1);
                server.playSound(
                        null, owner.blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.HOSTILE, 1.15F, 0.82F);
                for (ServerPlayer participant : server.players()) {
                    if (ChallengeManager.isParticipant(owner, participant)) {
                        participant.displayClientMessage(Component.translatable(
                                "message.blade_tetra.mikage.cage_stagger"), true);
                    }
                }
            }
        }
    }

    private void applyScissorImpact(
            ServerPlayer player,
            ToriiScissorState state,
            ServerLevel server,
            boolean finalImpact) {
        MikageArenaController arena = owner.arenaController();
        boolean guarding = owner.isBladeGuarding(player);
        boolean stable = guarding
                && state.consecutiveGuardTicks
                >= MikageEntity.TORII_SWEEP_STABLE_GUARD_TICKS;
        Vec3 impact = player.position()
                .add(player.getLookAngle().normalize().scale(0.65D))
                .add(0.0D, 1.0D, 0.0D);
        if (stable) {
            state.stableGuards++;
            boolean completed = finalImpact && state.stableGuards >= 3;
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SCISSOR_GUARD,
                    impact.x, impact.y, impact.z,
                    impact.x, impact.y, impact.z,
                    player.getYRot(),
                    completed ? 1.22F : 0.92F,
                    -1, player.getId(),
                    completed ? 20 : 12,
                    state.stableGuards), impact);
            owner.sendCombatVfx(
                    server,
                    completed
                            ? BladeCombatVfxPacket.PERFECT_GUARD
                            : BladeCombatVfxPacket.PARRY,
                    impact, player.getYRot(),
                    completed ? 1.18F : 0.82F,
                    player.getId());
            owner.playBladeParrySound(
                    server, impact, SoundSource.PLAYERS, completed);
            if (completed) {
                arena.toriiScissorCountered = true;
            }
        } else {
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * 0.80F;
            owner.dealTrialDamage(player, damage, guarding);
            int result = guarding
                    ? BladeTechniqueVfxPacket.SCISSOR_GUARD
                    : BladeTechniqueVfxPacket.SCISSOR_FAILURE;
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    result,
                    impact.x, impact.y, impact.z,
                    impact.x, impact.y, impact.z,
                    player.getYRot(), guarding ? 0.58F : 1.0F,
                    -1, player.getId(), 14,
                    owner.getRandom().nextInt()), impact);
            if (guarding) {
                owner.playBladeParrySound(
                        server, impact, SoundSource.PLAYERS, false);
            } else {
                server.playSound(
                        null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.HOSTILE, 1.15F, 0.72F);
            }
        }
        server.sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                stable ? 4 : 9,
                0.42D, 0.55D, 0.42D, 0.0D);
    }

    private void confineToCage(
            ServerPlayer player,
            Vec3 center,
            ServerLevel server) {
        Vec3 offset = player.position()
                .subtract(center).multiply(1.0D, 0.0D, 1.0D);
        double distance = offset.length();
        if (distance <= MikageEntity.TORII_CAGE_RADIUS - 0.25D
                || distance < 0.001D) {
            return;
        }
        Vec3 inward = offset.normalize().reverse();
        if (distance > MikageEntity.TORII_CAGE_RADIUS + 1.0D) {
            Vec3 edge = center.add(
                    offset.normalize().scale(
                            MikageEntity.TORII_CAGE_RADIUS - 0.55D));
            player.teleportTo(
                    server, edge.x, player.getY(), edge.z,
                    player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(
                player.getDeltaMovement().scale(0.35D)
                        .add(inward.scale(0.72D)));
        player.hurtMarked = true;
    }

    private void applyCagePulse(
            ServerPlayer player,
            CageState cage,
            ServerLevel server,
            boolean finalPulse) {
        MikageArenaController arena = owner.arenaController();
        boolean guarding = owner.tickCount - cage.lastGuardTick <= 4;
        boolean perfectGuard = finalPulse && guarding
                && cage.guardedTicks
                >= GameplayConfig.MIKAGE_CAGE_PERFECT_GUARD_TICKS.get();
        float amount = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * GameplayConfig.MIKAGE_CAGE_PULSE_DAMAGE.get().floatValue();
        Vec3 pulseTarget = player.position().add(0.0D, 1.0D, 0.0D);
        owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.CAGE_PULSE,
                cage.center.x, cage.center.y + 0.08D, cage.center.z,
                pulseTarget.x, pulseTarget.y, pulseTarget.z,
                player.getYRot(),
                finalPulse ? 1.18F : 0.82F,
                -1, player.getId(), 12,
                owner.getRandom().nextInt()), cage.center);
        if (perfectGuard) {
            amount = 0.0F;
            arena.cagePerfectCountered = true;
            Vec3 impact = player.position()
                    .add(player.getLookAngle().normalize().scale(0.72D))
                    .add(0.0D, 1.1D, 0.0D);
            owner.sendCombatVfx(
                    server, BladeCombatVfxPacket.PERFECT_GUARD,
                    impact, player.getYRot(), 1.15F, player.getId());
            owner.playBladeParrySound(
                    server, impact, SoundSource.PLAYERS, true);
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.CAGE_SUCCESS,
                    impact.x, impact.y, impact.z,
                    impact.x, impact.y, impact.z,
                    player.getYRot(), 1.2F, -1, player.getId(), 18,
                    owner.getRandom().nextInt()), impact);
            if (!arena.cageGuardPraised) {
                arena.cageGuardPraised = true;
                ChallengeManager.tryDialogue(owner, MikageDialogue.CAGE_GUARD);
            }
        } else if (guarding) {
            amount *= GameplayConfig.MIKAGE_CAGE_GUARD_MULTIPLIER
                    .get().floatValue();
            Vec3 impact = player.position()
                    .add(player.getLookAngle().normalize().scale(0.68D))
                    .add(0.0D, 1.05D, 0.0D);
            owner.sendCombatVfx(
                    server, BladeCombatVfxPacket.PARRY,
                    impact, player.getYRot(), 0.78F, player.getId());
            owner.playBladeParrySound(
                    server, impact, SoundSource.PLAYERS, false);
        }
        if (finalPulse && !perfectGuard) {
            amount += (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * GameplayConfig.MIKAGE_CAGE_FINAL_DAMAGE.get().floatValue();
            owner.sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.CAGE_FAILURE,
                    pulseTarget.x, pulseTarget.y, pulseTarget.z,
                    pulseTarget.x, pulseTarget.y, pulseTarget.z,
                    player.getYRot(), 1.15F, -1, player.getId(), 18,
                    owner.getRandom().nextInt()), pulseTarget);
        }
        if (amount > 0.0F) {
            owner.dealAdjustedTrialDamage(player, amount, guarding);
        }
        server.sendParticles(new DustParticleOptions(
                        new Vector3f(0.82F, 0.025F, 0.08F), 1.0F),
                player.getX(), player.getY() + 1.0D, player.getZ(),
                18, 1.4D, 0.8D, 1.4D, 0.04D);
    }
}
