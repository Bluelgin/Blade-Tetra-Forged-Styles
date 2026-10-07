package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageLegacyTiming.*;

import static dev.bladetetra.challenge.MikageEntity.*;
import static dev.bladetetra.challenge.MikageArenaController.*;
import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.BladeCombatVfxPacket;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.slasharts.JudgementCut;
import mods.flammpfeil.slashblade.slasharts.SakuraEnd;
import mods.flammpfeil.slashblade.slasharts.WaveEdge;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Existing authored effects kept behind an explicit migration boundary.
 * New skills implement SkillExecution and must not grow this compatibility adapter.
 */
final class MikageLegacySkillEffects {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;
    private final MikageTechniqueRuntime techniques;
    private final MikageArenaController arena;
    private final MikageAttackTimeline attackTimeline;
    private final MikageToriiController torii;

    MikageLegacySkillEffects(MikageEntity owner) {
        this.owner = owner;
        combat = owner.combatDirector();
        defense = owner.defenseController();
        techniques = owner.techniqueRuntime();
        arena = owner.arenaController();
        attackTimeline = owner.attackTimeline();
        torii = new MikageToriiController(owner);
    }

    void stepIaido(LivingEntity target, ServerLevel server) {
        Vec3 toTarget = target.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        if (toTarget.lengthSqr() < 0.01D) return;
        Vec3 forward = toTarget.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x)
                .scale(owner.getRandom().nextBoolean() ? 0.95D : -0.95D);
        double advance = Math.max(0.0D, toTarget.length() - 1.75D);
        Vec3 destination = owner.position().add(forward.scale(advance)).add(side);
        Vec3 otherSide = destination.subtract(side.scale(2.0D));
        if (!canStandAt(destination) && canStandAt(otherSide)) {
            destination = otherSide;
        } else if (!canStandAt(destination)) {
            destination = owner.position().add(forward.scale(Math.max(0.0D,
                    toTarget.length() - 2.35D)));
        }
        owner.movement().teleportWithinArena(destination.x, target.getY(), destination.z);
        owner.lookAt(target, 180.0F, 180.0F);
        defense.stepIaidoCommitted = true;
        server.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 0.75F, 1.72F);
        server.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.15D, owner.getZ(),
                10, 0.22D, 0.08D, 0.22D, 0.025D);
    }

    boolean canStandAt(Vec3 destination) {
        Vec3 delta = destination.subtract(owner.position());
        return owner.level().noCollision(owner, owner.getBoundingBox().move(delta));
    }

    static MikageAction actionForStrike(Technique technique) {
        return switch (technique.style) {
            case IAIDO -> MikageAction.IAIDO_DRAW;
            case RENGEKI -> MikageAction.COMBO_SLASH;
            case DANGAKU -> MikageAction.HEAVY_CLEAVE;
            default -> technique == Technique.AERIAL_RAIN
                    ? MikageAction.AERIAL_CAST : MikageAction.CAST_SLASH;
        };
    }

    static int actionLength(Technique technique) {
        return switch (technique.style) {
            case IAIDO -> 18;
            case RENGEKI -> 22;
            case DANGAKU -> 24;
            default -> 18;
        };
    }

    void performTechnique(Technique technique, LivingEntity target, ServerLevel server) {
        double visualDamage = 0.0D;
        float baseDamage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 castOrigin = owner.position().add(0.0D, 0.8D, 0.0D);
        Vec3 forward = horizontalLook();
        switch (technique) {
            case CIRCLE_SLASH -> {
                AttackManager.doSlash(owner, 0.0F, true, false, visualDamage);
                AttackManager.doSlash(owner, 180.0F, false, false, visualDamage);
                attackTimeline.circle(4, owner.position(), 5.0D, 3.0D,
                        baseDamage * 0.68F, 0.75D);
            }
            case BLADE_COMBO -> {
                AttackManager.doSlash(owner, 25.0F, true, false, visualDamage);
                AttackManager.doSlash(owner, -155.0F, false, false, visualDamage);
                AttackManager.doSlash(owner, 205.0F, false, false, visualDamage);
                attackTimeline.cone(4, castOrigin, forward, 6.2D,
                        58.0D, 3.0D, baseDamage * 0.52F, 0.45D);
                attackTimeline.cone(15, castOrigin, forward, 6.8D,
                        72.0D, 3.0D, baseDamage * 0.58F, 0.65D);
            }
            case STEP_IAIDO -> {
                AttackManager.doSlash(owner, -12.0F, true, false, visualDamage);
                Vec3 origin = owner.position().add(0.0D, 0.8D, 0.0D);
                Vec3 direction = target.position().subtract(owner.position())
                        .multiply(1.0D, 0.0D, 1.0D);
                if (direction.lengthSqr() < 0.01D) direction = horizontalLook();
                owner.lookAt(target, 180.0F, 180.0F);
                attackTimeline.cone(1, origin, direction, 4.2D,
                        48.0D, 3.2D, baseDamage * 0.92F, 0.45D,
                        landed -> finishStepIaido(landed, target, server));
                server.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.HOSTILE, 1.1F, 1.85F);
            }
            case DANGAKU_CLEAVE -> {
                AttackManager.doSlash(owner, 92.0F, true, false, visualDamage);
                attackTimeline.cone(8, castOrigin, forward, 7.6D,
                        68.0D, 4.0D, baseDamage * 1.08F, 1.15D);
                server.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                        SoundSource.HOSTILE, 1.25F, 0.62F);
            }
            case FLASH_COUNTER -> {
                AttackManager.doSlash(owner, 0.0F, true, false, visualDamage);
                attackTimeline.circle(3, owner.position(), 4.3D, 3.0D,
                        baseDamage * 0.46F, 0.65D);
                flashStepAway(target, server, 8.2D);
                Drive.doSlash(owner, 0.0F, 28, Vec3.ZERO,
                        true, visualDamage, 1.85F);
                Vec3 start = owner.position().add(0.0D, 0.8D, 0.0D);
                attackTimeline.line(8, start, start.add(horizontalLook().scale(30.0D)),
                        2.0D, 3.5D, baseDamage * 0.72F, 0.7D);
            }
            case SAKURA_END -> {
                SakuraEnd.doSlash(owner, 30.0F, Vec3.ZERO,
                        false, true, visualDamage);
                SakuraEnd.doSlash(owner, -150.0F, Vec3.ZERO,
                        true, false, visualDamage);
                attackTimeline.circle(5, target.position(), 3.8D, 3.5D,
                        baseDamage * 0.56F, 0.35D);
                attackTimeline.circle(17, target.position(), 5.2D, 4.0D,
                        baseDamage * 0.68F, 0.75D);
            }
            case DRIVE_FAN -> {
                Drive.doSlash(owner, -18.0F, 28, Vec3.ZERO,
                        false, visualDamage, 1.65F);
                Drive.doSlash(owner, 0.0F, 30, Vec3.ZERO,
                        true, visualDamage, 1.85F);
                Drive.doSlash(owner, 18.0F, 28, Vec3.ZERO,
                        false, visualDamage, 1.65F);
                queueLineFan(castOrigin, forward, baseDamage * 0.62F);
            }
            case WAVE_EDGE -> WaveEdge.doSlash(owner, 0.0F, 32, Vec3.ZERO,
                    true, visualDamage, 1.15F, 1.85F, 4);
            case JUDGEMENT_CUT -> {
                JudgementCut.doJudgementCut(owner).setDamage(visualDamage);
                Vec3 center = target.position();
                attackTimeline.circle(9, center, 3.4D, 4.0D,
                        baseDamage * 0.58F, 0.25D);
                attackTimeline.circle(21, center, 4.6D, 4.5D,
                        baseDamage * 0.66F, 0.55D);
            }
            case SUPER_JUDGEMENT -> {
                JudgementCut.doJudgementCut(owner).setDamage(visualDamage);
                Vec3 center = target.position();
                for (int delay = 8; delay <= 32; delay += 12) {
                    attackTimeline.circle(delay, center, 5.2D, 5.0D,
                            baseDamage * 0.62F, 0.5D);
                }
            }
            case SUMMONED_VOLLEY -> fireSummonedVolley(target, server,
                    owner.getPhase() >= 3 ? 7 : 5, visualDamage);
            case AERIAL_RAIN -> beginAerialTechnique(target, server);
            case BOUNDARY_FLASH -> beginBoundaryFlash(target, server);
            case TORII_SWEEP -> beginToriiSweep(server);
            case TORII_CAGE -> beginToriiCages(server);
            case MIRROR_DUEL -> beginMirrorDuel(target, server);
            case BOUNDARY_SEAL -> beginBoundarySeal(server);
            case MOON_ECHO -> beginMoonEcho(target, server);
            case NONE -> {
            }
        }
        if (technique == Technique.WAVE_EDGE) {
            attackTimeline.line(8, castOrigin, castOrigin.add(forward.scale(34.0D)),
                    2.0D, 4.0D, baseDamage * 0.64F, 0.65D);
        } else if (technique == Technique.SUMMONED_VOLLEY) {
            attackTimeline.circle(13, target.position(), 3.1D, 4.5D,
                    baseDamage * 0.66F, 0.6D);
        }
    }

    void finishStepIaido(boolean landed, LivingEntity target, ServerLevel server) {
        if (!defense.stepIaidoCommitted || !owner.isAlive()) return;
        defense.stepIaidoCommitted = false;
        if (landed) {
            flashStepAway(target, server, 5.6D);
            return;
        }
        startInteractionOpening(server, GameplayConfig.MIKAGE_STEP_IAIDO_OPENING_TICKS.get(),
                GameplayConfig.MIKAGE_STEP_IAIDO_OPENING_DAMAGE_MULTIPLIER.get().floatValue(),
                "message.blade_tetra.mikage.iaido_evaded");
    }

    void beginMirrorDuel(LivingEntity target, ServerLevel server) {
        techniques.mirrorDuelTicks = MIRROR_DUEL_TOTAL_TICKS;
        techniques.mirrorDuelCooldown = combat.scaledCooldown(220);
        techniques.mirrorDuelTarget = target.getUUID();
        techniques.mirrorDuelStart = owner.position();
        Vec3 direction = target.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.01D) direction = horizontalLook();
        techniques.mirrorDuelEnd = ChallengeManager.clampMikagePosition(owner,
                target.position().add(direction.normalize().scale(2.2D)));
        owner.getNavigation().stop();
        owner.setAction(MikageAction.IAIDO_READY, MIRROR_DUEL_TOTAL_TICKS);
        server.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 0.9F, 1.75F);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.MIRROR_DUEL,
                techniques.mirrorDuelStart.x, techniques.mirrorDuelStart.y + 0.9D, techniques.mirrorDuelStart.z,
                techniques.mirrorDuelEnd.x, techniques.mirrorDuelEnd.y + 0.9D, techniques.mirrorDuelEnd.z,
                owner.getYRot(), 1.0F, owner.getId(), -1,
                MIRROR_DUEL_TOTAL_TICKS, owner.getRandom().nextInt()), techniques.mirrorDuelStart);
    }

    void tickMirrorDuel(ServerLevel server) {
        Entity found = techniques.mirrorDuelTarget == null ? null : server.getEntity(techniques.mirrorDuelTarget);
        LivingEntity target = found instanceof LivingEntity living ? living : null;
        if (target == null || !target.isAlive()) {
            finishMirrorDuel();
            return;
        }
        owner.getNavigation().stop();
        if (techniques.mirrorDuelTicks > MIRROR_DUEL_DASH_START) {
        } else if (techniques.mirrorDuelTicks >= 5) {
            double progress = (MIRROR_DUEL_DASH_START - techniques.mirrorDuelTicks + 1.0D) / 12.0D;
            Vec3 next = techniques.mirrorDuelStart.lerp(techniques.mirrorDuelEnd, Mth.clamp(progress, 0.0D, 1.0D));
            owner.movement().teleportWithinArena(next.x, next.y, next.z);
            owner.lookAt(target, 180.0F, 180.0F);
            if (techniques.mirrorDuelTicks % 2 == 0) {
                server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        owner.getX(), owner.getY() + 0.9D, owner.getZ(), 2,
                        0.18D, 0.28D, 0.18D, 0.0D);
            }
        }
        if (techniques.mirrorDuelTicks == 4) {
            boolean inPath = distanceToSegment(target.position(), techniques.mirrorDuelStart,
                    techniques.mirrorDuelEnd) <= 1.45D;
            if (inPath) {
                float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.88F;
                boolean guarded = target instanceof ServerPlayer player
                        && isBladeGuarding(player);
                if (guarded) {
                    ServerPlayer player = (ServerPlayer) target;
                    server.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                            SoundSource.PLAYERS, 1.0F, 1.5F);
                }
                if (target instanceof ServerPlayer player) {
                    owner.dealTrialDamage(player, damage, guarded);
                    if (!guarded) {
                        Vec3 failure = player.position().add(0.0D, 1.0D, 0.0D);
                        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                                BladeTechniqueVfxPacket.MIRROR_DUEL_FAILURE,
                                failure.x, failure.y, failure.z,
                                failure.x, failure.y, failure.z,
                                player.getYRot(), 1.0F, -1, player.getId(), 18,
                                owner.getRandom().nextInt()), failure);
                    }
                } else {
                    target.hurt(server.damageSources().mobAttack(owner), damage);
                }
            }
            AttackManager.doSlash(owner, 0.0F, true, false, 0.0D);
            server.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.3F, 1.55F);
        }
        if (--techniques.mirrorDuelTicks <= 0) finishMirrorDuel();
    }

    void counterMirrorDuel(ServerPlayer player) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        Vec3 clashStart = player.getEyePosition();
        Vec3 clashEnd = owner.getEyePosition();
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.COUNTER_CLASH,
                clashStart.x, clashStart.y, clashStart.z,
                clashEnd.x, clashEnd.y, clashEnd.z,
                player.getYRot(), 1.25F, -1, -1, 16, owner.getRandom().nextInt()), clashStart);
        techniques.mirrorDuelTicks = 0;
        techniques.mirrorDuelTarget = null;
        attackTimeline.clear();
        startInteractionOpening(server, GameplayConfig.MIKAGE_DUEL_OPENING_TICKS.get(),
                GameplayConfig.MIKAGE_DUEL_OPENING_DAMAGE_MULTIPLIER.get().floatValue(),
                "message.blade_tetra.mikage.duel_countered");
        server.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 1.1F, 1.8F);
    }

    void finishMirrorDuel() {
        techniques.mirrorDuelTicks = 0;
        techniques.mirrorDuelTarget = null;
        owner.setAction(MikageAction.IDLE, 1);
    }

    void beginBoundarySeal(ServerLevel server) {
        techniques.boundarySealTicks = BOUNDARY_SEAL_TOTAL_TICKS;
        techniques.boundarySealCooldown = combat.scaledCooldown(560);
        techniques.boundarySealsBroken = 0;
        techniques.boundarySealEntities.clear();
        Vec3 center = ChallengeManager.arenaCenter(owner);
        owner.movement().teleportWithinArena(center.x, center.y, center.z);
        owner.setAction(MikageAction.RITUAL, BOUNDARY_SEAL_TOTAL_TICKS);
        List<MikagePhantomSwordEntity> spawnedSeals = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            double angle = owner.getRandom().nextDouble() * 0.55D + slot * Math.PI * 2.0D / 3.0D;
            Vec3 pos = center.add(Math.cos(angle) * 7.5D, 0.15D, Math.sin(angle) * 7.5D);
            MikagePhantomSwordEntity seal = ModEntities.MIKAGE_PHANTOM_SWORD.get().create(server);
            if (seal == null) continue;
            seal.setPos(pos);
            seal.configureSeal(owner, slot);
            server.addFreshEntity(seal);
            techniques.boundarySealEntities.add(seal.getUUID());
            spawnedSeals.add(seal);
        }
        for (int i = 0; i < spawnedSeals.size(); i++) {
            MikagePhantomSwordEntity first = spawnedSeals.get(i);
            MikagePhantomSwordEntity second = spawnedSeals.get((i + 1) % spawnedSeals.size());
            Vec3 start = first.position().add(0.0D, 0.8D, 0.0D);
            Vec3 end = second.position().add(0.0D, 0.8D, 0.0D);
            owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SEAL_LINK,
                    start.x, start.y, start.z, end.x, end.y, end.z,
                    0.0F, 1.0F, first.getId(), second.getId(),
                    BOUNDARY_SEAL_TOTAL_TICKS, owner.getRandom().nextInt()), center);
        }
        server.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.2F, 0.75F);
    }

    void tickBoundarySeal(ServerLevel server) {
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        if (techniques.boundarySealsBroken >= 3) {
            Vec3 center = owner.position().add(0.0D, 1.0D, 0.0D);
            owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SEAL_SUCCESS,
                    center.x, center.y, center.z, center.x, center.y, center.z,
                    owner.getYRot(), 1.18F, -1, -1, 18, owner.getRandom().nextInt()), center);
            clearBoundarySeals(server);
            techniques.boundarySealTicks = 0;
            startInteractionOpening(server, GameplayConfig.MIKAGE_SEAL_OPENING_TICKS.get(),
                    GameplayConfig.MIKAGE_SEAL_OPENING_DAMAGE_MULTIPLIER.get().floatValue(),
                    "message.blade_tetra.mikage.seals_broken");
            return;
        }
        if (--techniques.boundarySealTicks <= 0) {
            float scale = 1.15F - techniques.boundarySealsBroken * 0.35F;
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
            Vec3 center = ChallengeManager.arenaCenter(owner);
            owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SEAL_FAILURE,
                    center.x, center.y, center.z, center.x, center.y, center.z,
                    owner.getYRot(), Math.max(0.45F, scale), -1, -1, 18, owner.getRandom().nextInt()), center);
            attackTimeline.circle(1, center, 32.0D, 8.0D, damage, 1.0D);
            AttackManager.doSlash(owner, 0.0F, true, false, 0.0D);
            clearBoundarySeals(server);
            owner.setAction(MikageAction.IDLE, 1);
        }
    }

    void breakBoundarySeal(MikagePhantomSwordEntity seal, Entity attacker) {
        if (!(owner.level() instanceof ServerLevel server) || techniques.boundarySealTicks <= 0
                || !(attacker instanceof ServerPlayer player)
                || !ChallengeManager.isParticipant(owner, player)) return;
        if (techniques.boundarySealEntities.remove(seal.getUUID())) {
            techniques.boundarySealsBroken++;
            Vec3 position = seal.position().add(0.0D, 0.8D, 0.0D);
            owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SEAL_BREAK,
                    position.x, position.y, position.z,
                    position.x, position.y, position.z,
                    seal.getYRot(), 1.0F, -1, -1, 14, owner.getRandom().nextInt()), position);
            seal.discard();
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.mikage.seal_progress", techniques.boundarySealsBroken, 3), true);
        }
    }

    void clearBoundarySeals(ServerLevel server) {
        for (UUID id : techniques.boundarySealEntities) {
            Entity entity = server.getEntity(id);
            if (entity != null) entity.discard();
        }
        techniques.boundarySealEntities.clear();
    }

    void beginMoonEcho(LivingEntity target, ServerLevel server) {
        techniques.moonEchoTicks = MOON_ECHO_TOTAL_TICKS;
        techniques.moonEchoCooldown = combat.scaledCooldown(480);
        techniques.moonEchoEntities.clear();
        for (ServerPlayer participant : server.players()) {
            if (ChallengeManager.isParticipant(owner, participant)) {
                owner.counters().clearPlayerLock(participant);
            }
        }
        Vec3 center = target.position();
        int realSlot = owner.getRandom().nextInt(4);
        for (int slot = 0; slot < 4; slot++) {
            double angle = slot * Math.PI * 0.5D + owner.getRandom().nextDouble() * 0.18D;
            Vec3 pos = center.add(Math.cos(angle) * 4.2D, 0.0D, Math.sin(angle) * 4.2D);
            pos = ChallengeManager.clampMikagePosition(owner, pos);
            if (slot == realSlot) {
                owner.movement().teleportWithinArena(pos.x, target.getY(), pos.z);
                owner.lookAt(target, 180.0F, 180.0F);
                continue;
            }
            MikageEchoEntity echo = ModEntities.MIKAGE_ECHO.get().create(server);
            if (echo == null) continue;
            echo.setPos(pos.x, target.getY(), pos.z);
            echo.configure(owner);
            server.addFreshEntity(echo);
            techniques.moonEchoEntities.add(echo.getUUID());
        }
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.MOON_ECHO_FIELD,
                center.x, center.y, center.z,
                owner.getX(), owner.getY() + 1.0D, owner.getZ(),
                owner.getYRot(), 1.0F, -1, target.getId(),
                MOON_ECHO_TOTAL_TICKS, realSlot), center);
        owner.setAction(MikageAction.IAIDO_READY, MOON_ECHO_TOTAL_TICKS);
        server.playSound(null, owner.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.HOSTILE, 1.1F, 1.15F);
    }

    void tickMoonEcho(ServerLevel server) {
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        if (owner.tickCount % 2 == 0) {
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.72F, 0.015F, 0.07F), 1.0F),
                    owner.getX(), owner.getY() + 1.0D, owner.getZ(), 4,
                    0.22D, 0.55D, 0.22D, 0.015D);
        }
        if (--techniques.moonEchoTicks <= 0) {
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.58F;
            attackTimeline.circle(1, owner.position(), 3.5D, 3.5D, damage, 0.55D);
            for (UUID id : techniques.moonEchoEntities) {
                Entity echo = server.getEntity(id);
                if (echo != null) {
                    attackTimeline.circle(1, echo.position(), 3.5D, 3.5D, damage, 0.55D);
                    Vec3 failure = echo.position().add(0.0D, 1.0D, 0.0D);
                    owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                            BladeTechniqueVfxPacket.MOON_ECHO_FAILURE,
                            failure.x, failure.y, failure.z,
                            failure.x, failure.y, failure.z,
                            0.0F, 0.8F, -1, -1, 18, owner.getRandom().nextInt()), failure);
                }
            }
            clearMoonEchoes(server);
            owner.setAction(MikageAction.IDLE, 1);
        }
    }

    void strikeMoonEcho(MikageEchoEntity echo, Entity attacker) {
        if (!(owner.level() instanceof ServerLevel server) || techniques.moonEchoTicks <= 0
                || !(attacker instanceof ServerPlayer player)
                || !ChallengeManager.isParticipant(owner, player)) return;
        float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.45F;
        Vec3 falseEcho = echo.position().add(0.0D, 1.0D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.MOON_ECHO_FALSE,
                falseEcho.x, falseEcho.y, falseEcho.z,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                player.getYRot(), 1.0F, echo.getId(), player.getId(), 18,
                owner.getRandom().nextInt()), falseEcho);
        attackTimeline.circle(1, echo.position(), 3.2D, 3.0D, damage, 0.65D);
        clearMoonEchoes(server);
        techniques.moonEchoTicks = 0;
        owner.setAction(MikageAction.IAIDO_DRAW, 12);
        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.mikage.echo_false"), true);
    }

    void solveMoonEcho(ServerPlayer player) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        Vec3 truth = owner.position().add(0.0D, 1.0D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.MOON_ECHO_TRUE,
                truth.x, truth.y, truth.z,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                player.getYRot(), 1.2F, owner.getId(), player.getId(), 20,
                owner.getRandom().nextInt()), truth);
        clearMoonEchoes(server);
        techniques.moonEchoTicks = 0;
        startInteractionOpening(server, GameplayConfig.MIKAGE_ECHO_OPENING_TICKS.get(),
                GameplayConfig.MIKAGE_ECHO_OPENING_DAMAGE_MULTIPLIER.get().floatValue(),
                "message.blade_tetra.mikage.echo_solved");
        server.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.0F, 1.35F);
    }

    void clearMoonEchoes(ServerLevel server) {
        for (UUID id : techniques.moonEchoEntities) {
            Entity entity = server.getEntity(id);
            if (entity != null) entity.discard();
        }
        techniques.moonEchoEntities.clear();
    }

    void startInteractionOpening(ServerLevel server, int ticks, float multiplier,
            String messageKey) {
        if (owner.isSwordWheelDeployed()) owner.swordWheel().recallSwordWheel(server, ticks + 40);
        techniques.interactionOpeningTicks = Math.max(techniques.interactionOpeningTicks, ticks);
        techniques.interactionOpeningMultiplier = Math.max(techniques.interactionOpeningMultiplier, multiplier);
        combat.techniqueCooldown = Math.max(combat.techniqueCooldown, ticks + 18);
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        owner.setAction(MikageAction.STAGGERED, ticks);
        server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                owner.getX(), owner.getY() + 1.0D, owner.getZ(), 36,
                0.72D, 0.9D, 0.72D, 0.10D);
        for (ServerPlayer participant : server.players()) {
            if (ChallengeManager.isParticipant(owner, participant)) {
                participant.displayClientMessage(Component.translatable(messageKey), true);
            }
        }
    }

    void renderWarningLine(ServerLevel server, Vec3 start, Vec3 end,
            float red, float green, float blue) {
        for (int i = 0; i <= 12; i++) {
            Vec3 point = start.lerp(end, i / 12.0D);
            server.sendParticles(new DustParticleOptions(new Vector3f(red, green, blue), 0.8F),
                    point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
        }
    }

    static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        Vec3 relative = point.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        double length = segment.lengthSqr();
        double t = length < 0.001D ? 0.0D
                : Mth.clamp(relative.dot(segment) / length, 0.0D, 1.0D);
        return relative.subtract(segment.scale(t)).length();
    }

    boolean isMoonEchoActive() {
        return techniques.moonEchoTicks > 0;
    }

    void lockBladeTarget(LivingEntity target) {
        owner.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> state.setTargetEntityId(target));
    }

    Vec3 horizontalLook() {
        Vec3 look = owner.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        return look.lengthSqr() < 0.001D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : look.normalize();
    }

    void queueLineFan(Vec3 origin, Vec3 forward, float damage) {
        for (double angle : new double[] {-18.0D, 0.0D, 18.0D}) {
            Vec3 direction = forward.yRot((float) Math.toRadians(angle));
            attackTimeline.line(8, origin, origin.add(direction.scale(31.0D)),
                    1.75D, 4.0D, damage, 0.55D);
        }
    }

    void flashStepAway(LivingEntity target, ServerLevel server, double distance) {
        Vec3 away = owner.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 0.01D) {
            away = target.getLookAngle().reverse().multiply(1.0D, 0.0D, 1.0D);
        }
        float sideAngle = owner.getRandom().nextBoolean() ? 0.48F : -0.48F;
        Vec3 destination = target.position().add(away.normalize().yRot(sideAngle).scale(distance));
        owner.movement().teleportWithinArena(destination.x, target.getY(), destination.z);
        owner.lookAt(target, 180.0F, 180.0F);
        server.playSound(null, owner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.75F, 1.5F);
        server.sendParticles(ParticleTypes.PORTAL, owner.getX(), owner.getY() + 0.9D, owner.getZ(),
                28, 0.3D, 0.65D, 0.3D, 0.12D);
    }

    void fireSummonedVolley(LivingEntity target, ServerLevel server,
            int count, double damage) {
        Vec3 aim = target.getEyePosition();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            Vec3 origin = owner.getEyePosition().add(Math.cos(angle) * 1.25D,
                    (i % 2) * 0.45D - 0.15D, Math.sin(angle) * 1.25D);
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                    SlashBlade.RegistryEvents.SummonedSword, server);
            sword.setPos(origin);
            sword.setOwner(owner);
            sword.setDamage(damage);
            sword.setColor(0xD51F3F);
            sword.setRoll((float) Math.toDegrees(angle));
            Vec3 direction = aim.subtract(origin).normalize();
            sword.shoot(direction.x, direction.y, direction.z, 2.6F, 1.5F);
            sword.getPersistentData().putBoolean("blade_tetra_mikage_attack", true);
            server.addFreshEntity(sword);
        }
        server.playSound(null, owner.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                SoundSource.HOSTILE, 0.55F, 1.55F);
    }

    void beginAerialTechnique(LivingEntity target, ServerLevel server) {
        techniques.aerialTarget = target;
        techniques.aerialTicks = 42;
        owner.setNoGravity(true);
        owner.getNavigation().stop();
        owner.setDeltaMovement(0.0D, 0.62D, 0.0D);
        owner.setAction(MikageAction.AERIAL_CAST, 42);
        server.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 0.9F, 1.7F);
        server.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.1D, owner.getZ(),
                18, 0.5D, 0.15D, 0.5D, 0.05D);
    }

    void tickAerialTechnique() {
        ServerLevel server = (ServerLevel) owner.level();
        LivingEntity target = techniques.aerialTarget != null && techniques.aerialTarget.isAlive()
                ? techniques.aerialTarget : owner.getTarget();
        owner.getNavigation().stop();
        owner.fallDistance = 0.0F;
        if (target != null) {
            lockBladeTarget(target);
            owner.lookAt(target, 180.0F, 180.0F);
        }
        if (techniques.aerialTicks > 25) {
            owner.setNoGravity(true);
            double desiredY = target == null ? owner.getY() + 0.15D
                    : Math.min(target.getY() + 6.5D, 76.0D);
            double rise = Mth.clamp((desiredY - owner.getY()) * 0.22D, -0.05D, 0.48D);
            owner.setDeltaMovement(0.0D, rise, 0.0D);
        } else if (techniques.aerialTicks == 25 && target != null) {
            owner.setDeltaMovement(Vec3.ZERO);
            castAerialRain(target, server);
        } else if (techniques.aerialTicks > 9) {
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
        } else {
            owner.setNoGravity(false);
            owner.setDeltaMovement(owner.getDeltaMovement().x, -0.58D, owner.getDeltaMovement().z);
        }
        if (--techniques.aerialTicks <= 0) {
            techniques.aerialTicks = 0;
            techniques.aerialTarget = null;
            owner.setNoGravity(false);
            owner.fallDistance = 0.0F;
        }
    }

    void castAerialRain(LivingEntity target, ServerLevel server) {
        int count = owner.getPhase() >= 3 ? 10 : 7;
        for (int i = 0; i < count; i++) {
            EntityHeavyRainSwords sword = new EntityHeavyRainSwords(
                    SlashBlade.RegistryEvents.HeavyRainSwords, server);
            double angle = Math.PI * 2.0D * i / count;
            Vec3 spread = new Vec3(Math.cos(angle) * (1.5D + i % 3),
                    0.0D, Math.sin(angle) * (1.5D + i % 3));
            Vec3 impact = target.position().add(spread);
            sword.setOwner(owner);
            sword.setColor(0xD51F3F);
            sword.setDamage(0.0D);
            sword.setRoll(i * (360.0F / count));
            sword.setPos(impact.add(0.0D, 7.5D, 0.0D));
            sword.setXRot(-90.0F);
            sword.shoot(0.0D, -1.0D, 0.0D, 2.75F, 1.0F);
            sword.doFire();
            sword.getPersistentData().putBoolean("blade_tetra_mikage_attack", true);
            server.addFreshEntity(sword);
            attackTimeline.circle(11 + i % 3, impact, 1.65D, 4.5D,
                    (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.48F,
                    0.35D);
        }
        WaveEdge.doSlash(owner, 0.0F, 28, new Vec3(0.0D, -0.25D, 0.0D),
                true, 0.0D, 1.05F, 1.55F, 3);
        server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER,
                SoundSource.HOSTILE, 0.7F, 1.65F);
    }

    void beginBoundaryFlash(LivingEntity target, ServerLevel server) {
        arena.boundaryFlashPending = false;
        arena.boundaryFlashReadyTicks = 0;
        arena.boundarySlashCenter = ChallengeManager.arenaCenter(owner);
        arena.boundarySlashReturn = owner.position();
        arena.boundarySlashHover = arena.boundarySlashCenter.add(0.0D, 8.0D, 0.0D);
        arena.boundarySlashDirection = horizontalDirection(arena.boundarySlashCenter, target.position());
        arena.boundarySlashTarget = target.getUUID();
        arena.boundarySlashLocked = false;
        arena.boundarySlashExecuted = false;
        arena.boundaryGuardHoldTicks.clear();
        arena.boundarySlashDelay = BOUNDARY_FLASH_TOTAL_TICKS;
        owner.getNavigation().stop();
        owner.setNoGravity(true);
        owner.setAction(MikageAction.RITUAL, BOUNDARY_FLASH_TOTAL_TICKS);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_FLASH,
                arena.boundarySlashHover.x, arena.boundarySlashHover.y + 1.0D, arena.boundarySlashHover.z,
                target.getX(), target.getY() + 1.0D, target.getZ(),
                owner.getYRot(), 1.0F, -1, target.getId(),
                BOUNDARY_FLASH_LOCK_AGE,
                owner.getRandom().nextInt()), arena.boundarySlashCenter);
        server.playSound(null, owner.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.HOSTILE, 1.35F, 0.48F);
        if (!arena.boundarySlashVoiced) {
            arena.boundarySlashVoiced = true;
            ChallengeManager.tryVoice(owner, MikageDialogue.BOUNDARY_SLASH);
        }
    }

    void tickBoundaryFlash(ServerLevel server) {
        owner.getNavigation().stop();
        owner.setDeltaMovement(Vec3.ZERO);
        owner.setNoGravity(true);
        int age = BOUNDARY_FLASH_TOTAL_TICKS - arena.boundarySlashDelay;

        if (!arena.boundarySlashLocked && age < BOUNDARY_FLASH_LOCK_AGE) {
            Entity tracked = arena.boundarySlashTarget == null ? null
                    : server.getEntity(arena.boundarySlashTarget);
            if (tracked instanceof LivingEntity living && living.isAlive()
                    && (!(living instanceof ServerPlayer player)
                    || ChallengeManager.isParticipant(owner, player))) {
                arena.boundarySlashDirection = horizontalDirection(
                        arena.boundarySlashCenter, living.position());
            }
        }
        float lockedYaw = (float) Math.toDegrees(Math.atan2(
                -arena.boundarySlashDirection.x, arena.boundarySlashDirection.z));
        owner.setYRot(lockedYaw);
        owner.setYHeadRot(lockedYaw);
        owner.yBodyRot = lockedYaw;

        Vec3 position;
        if (age < BOUNDARY_FLASH_ASCEND_TICKS) {
            double progress = smoothStep(age / (double) BOUNDARY_FLASH_ASCEND_TICKS);
            position = arena.boundarySlashReturn.lerp(arena.boundarySlashHover, progress);
        } else if (age < BOUNDARY_FLASH_DESCEND_AGE) {
            position = arena.boundarySlashHover;
        } else {
            double progress = smoothStep((age - BOUNDARY_FLASH_DESCEND_AGE)
                    / (double) Math.max(1,
                    BOUNDARY_FLASH_TOTAL_TICKS - BOUNDARY_FLASH_DESCEND_AGE));
            position = arena.boundarySlashHover.lerp(arena.boundarySlashReturn, progress);
        }
        owner.movement().teleportWithinArena(position.x, position.y, position.z);

        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) continue;
            arena.boundaryGuardHoldTicks.put(player.getUUID(), isBladeGuarding(player)
                    ? arena.boundaryGuardHoldTicks.getOrDefault(player.getUUID(), 0) + 1 : 0);
        }

        if (age == 24 || age == 50 || age == 76 || age == 102
                || age == 128 || age == 154) {
            server.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.HOSTILE, 0.88F, 0.54F + age * 0.004F);
        }
        if (!arena.boundarySlashLocked && age >= BOUNDARY_FLASH_LOCK_AGE) {
            arena.boundarySlashLocked = true;
            if (arena.boundaryWalls.size() < BOUNDARY_WALL_MAX_COUNT) {
                arena.boundarySlashDirection = separatedBoundaryDirection(arena.boundarySlashDirection);
                lockedYaw = (float) Math.toDegrees(Math.atan2(
                        -arena.boundarySlashDirection.x, arena.boundarySlashDirection.z));
                owner.setYRot(lockedYaw);
                owner.setYHeadRot(lockedYaw);
                owner.yBodyRot = lockedYaw;
            }
            Vec3 edge = arena.boundarySlashCenter.add(
                    arena.boundarySlashDirection.scale(BOUNDARY_FLASH_LENGTH));
            owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.BOUNDARY_FLASH_RELEASE,
                    arena.boundarySlashHover.x, arena.boundarySlashHover.y + 1.0D,
                    arena.boundarySlashHover.z,
                    edge.x, arena.boundarySlashCenter.y + 0.1D, edge.z,
                    lockedYaw, 1.2F, owner.getId(), -1,
                    BOUNDARY_FLASH_TOTAL_TICKS - BOUNDARY_FLASH_LOCK_AGE,
                    owner.getRandom().nextInt()), arena.boundarySlashCenter);
            server.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 1.2F, 0.62F);
        }
        if (age == BOUNDARY_FLASH_IMPACT_AGE - 12) {
            server.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.HOSTILE, 1.45F, 1.75F);
            for (ServerPlayer player : server.players()) {
                if (ChallengeManager.isParticipant(owner, player)) {
                    player.displayClientMessage(Component.translatable(
                            "message.blade_tetra.mikage.boundary_guard_now"), true);
                }
            }
        }
        if (!arena.boundarySlashExecuted && age >= BOUNDARY_FLASH_IMPACT_AGE) {
            arena.boundarySlashExecuted = true;
            executeBoundarySlash();
        }

        if (--arena.boundarySlashDelay <= 0) {
            arena.boundarySlashDelay = 0;
            owner.setNoGravity(false);
            owner.movement().teleportWithinArena(arena.boundarySlashReturn.x, arena.boundarySlashReturn.y,
                    arena.boundarySlashReturn.z);
            arena.boundarySlashTarget = null;
            arena.boundaryGuardHoldTicks.clear();
            arena.boundaryFlashCharge = 0;
            arena.boundaryFlashCycle++;
            arena.boundaryFlashReadyTicks = 0;
            combat.techniqueCooldown = Math.max(combat.techniqueCooldown, combat.scaledCooldown(50));
            owner.setAction(MikageAction.IDLE, 1);
        }
    }

    void executeBoundarySlash() {
        if (!(owner.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 edge = arena.boundarySlashCenter.add(
                arena.boundarySlashDirection.scale(BOUNDARY_FLASH_LENGTH));
        server.playSound(null, BlockPos.containing(arena.boundarySlashCenter),
                SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.55F, 0.58F);
        server.playSound(null, BlockPos.containing(arena.boundarySlashCenter),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.8F, 0.42F);
        server.playSound(null, BlockPos.containing(arena.boundarySlashCenter),
                SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.85F, 0.64F);
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) continue;
            if (isPlayerAboveBoundaryFlash(player)) continue;
            applyBoundaryHealthPressure(player);
        }
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_FLASH_IMPACT,
                arena.boundarySlashCenter.x, arena.boundarySlashCenter.y + 0.08D,
                arena.boundarySlashCenter.z,
                edge.x, arena.boundarySlashCenter.y + 0.08D, edge.z,
                owner.getYRot(), 1.25F, -1, -1, 18,
                owner.getRandom().nextInt()), arena.boundarySlashCenter);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                arena.boundarySlashCenter.x + arena.boundarySlashDirection.x * 6.0D,
                arena.boundarySlashCenter.y + 1.0D,
                arena.boundarySlashCenter.z + arena.boundarySlashDirection.z * 6.0D,
                32, 8.0D, 2.0D, 8.0D, 0.0D);
        createBoundaryWall(server);
    }

    boolean isPlayerAboveBoundaryFlash(ServerPlayer player) {
        Vec3 horizontal = player.position().subtract(owner.position())
                .multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() <= 3.2D * 3.2D
                && player.getY() >= owner.getY() + owner.getBbHeight() + 0.45D;
    }

    void applyBoundaryHealthPressure(ServerPlayer player) {
        int heldTicks = arena.boundaryGuardHoldTicks.getOrDefault(player.getUUID(), 0);
        boolean timedGuard = isBladeGuarding(player) && heldTicks > 0 && heldTicks <= 12;
        boolean heldTooEarly = isBladeGuarding(player) && heldTicks >= 40;
        float fraction = timedGuard ? 0.25F : heldTooEarly ? 0.75F : 0.50F;
        float healthAfter = Math.max(1.0F, player.getHealth() * (1.0F - fraction));
        player.setHealth(healthAfter);
        player.setAbsorptionAmount(player.getAbsorptionAmount() * (1.0F - fraction));
        player.invulnerableTime = 10;
        player.hurtMarked = true;
        Vec3 impact = player.position().add(0.0D, 1.0D, 0.0D);
        if (timedGuard) {
            owner.presentation().sendCombatVfx((ServerLevel) owner.level(), BladeCombatVfxPacket.PERFECT_GUARD,
                    impact, player.getYRot(), 1.12F, player.getId());
            owner.presentation().playBladeParrySound((ServerLevel) owner.level(), impact, SoundSource.PLAYERS, true);
        } else {
            owner.presentation().sendTechniqueVfx((ServerLevel) owner.level(), new BladeTechniqueVfxPacket(
                    BladeTechniqueVfxPacket.SCISSOR_FAILURE,
                    impact.x, impact.y, impact.z, impact.x, impact.y, impact.z,
                    player.getYRot(), heldTooEarly ? 1.28F : 1.0F,
                    -1, player.getId(), 18, owner.getRandom().nextInt()), impact);
        }
    }

    void createBoundaryWall(ServerLevel server) {
        if (arena.boundaryWalls.size() >= BOUNDARY_WALL_MAX_COUNT) {
            server.playSound(null, BlockPos.containing(arena.boundarySlashCenter),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.0F, 0.42F);
            return;
        }
        BoundaryWallState wall = new BoundaryWallState(++arena.boundaryWallSequence,
                arena.boundarySlashCenter, arena.boundarySlashDirection);
        arena.boundaryWalls.add(wall);
        sendBoundaryWall(server, wall);
        if (arena.boundaryWalls.size() == 3) arena.boundaryGapCycleTicks = 20;
        server.playSound(null, BlockPos.containing(wall.center),
                SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.25F, 0.58F);
    }

    void tickBoundaryWalls(ServerLevel server) {
        if (arena.boundaryWalls.size() >= 3 && --arena.boundaryGapCycleTicks <= 0) {
            for (BoundaryWallState wall : arena.boundaryWalls) {
                wall.pendingGapAlong = 14.0D + owner.getRandom().nextDouble() * 28.0D;
                wall.gapWarningTicks = BOUNDARY_GAP_WARNING_TICKS;
                sendBoundaryGapWarning(server, wall);
            }
            arena.boundaryGapCycleTicks = BOUNDARY_GAP_WARNING_TICKS
                    + BOUNDARY_GAP_OPEN_TICKS + 90 + owner.getRandom().nextInt(51);
            server.playSound(null, BlockPos.containing(ChallengeManager.arenaCenter(owner)),
                    SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.1F, 0.72F);
        }
        for (BoundaryWallState wall : arena.boundaryWalls) {
            if (wall.gapWarningTicks > 0 && --wall.gapWarningTicks == 0) {
                wall.gapAlong = wall.pendingGapAlong;
                wall.gapTicks = BOUNDARY_GAP_OPEN_TICKS;
                sendBoundaryGap(server, wall);
                Vec3 gap = wall.center.add(wall.direction.scale(wall.gapAlong));
                server.playSound(null, BlockPos.containing(gap),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.85F, 1.25F);
            }
            if (wall.gapTicks > 0) wall.gapTicks--;
        }
        if (owner.tickCount % BOUNDARY_FLAME_DAMAGE_INTERVAL != 0) return;
        // Defeat teleports remove players from server.players() inside owner.hurt().
        for (ServerPlayer player : List.copyOf(server.players())) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) continue;
            for (BoundaryWallState wall : arena.boundaryWalls) {
                if (touchesBoundaryFlame(player, wall)) {
                    applyBoundaryFlameDamage(server, player);
                    break;
                }
            }
        }
    }

    boolean touchesBoundaryFlame(ServerPlayer player, BoundaryWallState wall) {
        if (player.getBoundingBox().maxY < wall.center.y
                || player.getBoundingBox().minY > wall.center.y + BOUNDARY_FLAME_HEIGHT) {
            return false;
        }
        Vec3 relative = player.position().subtract(wall.center)
                .multiply(1.0D, 0.0D, 1.0D);
        double along = relative.dot(wall.direction);
        if (along < -0.45D || along > BOUNDARY_FLASH_LENGTH + 0.45D) return false;
        if (wall.gapTicks > 0
                && Math.abs(along - wall.gapAlong) <= BOUNDARY_GAP_HALF_WIDTH) {
            return false;
        }
        return Math.abs(relative.dot(wall.left())) <= BOUNDARY_FLAME_HALF_WIDTH;
    }

    void applyBoundaryFlameDamage(ServerLevel server, ServerPlayer player) {
        float armorReduction = Mth.clamp(player.getArmorValue() / 80.0F, 0.0F, 0.25F);
        float damage = Math.max(1.0F, player.getMaxHealth()
                * BOUNDARY_FLAME_HEALTH_FRACTION * (1.0F - armorReduction));
        DamageSource source = new DamageSource(server.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BOUNDARY_FLAME_DAMAGE), owner);
        if (!player.hurt(source, damage) || player.level() != server
                || !player.isAlive() || !ChallengeManager.isParticipant(owner, player)) return;
        Vec3 contact = player.position().add(0.0D, 0.65D, 0.0D);
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.015F, 0.025F), 1.35F),
                contact.x, contact.y, contact.z, 12,
                0.38D, 0.62D, 0.38D, 0.025D);
        server.sendParticles(ParticleTypes.LARGE_SMOKE,
                contact.x, contact.y + 0.25D, contact.z, 3,
                0.24D, 0.35D, 0.24D, 0.015D);
        if (owner.tickCount % 8 == 0) {
            server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_BURN,
                    SoundSource.HOSTILE, 0.72F, 0.62F + owner.getRandom().nextFloat() * 0.16F);
        }
    }

    void sendBoundaryWall(ServerLevel server, BoundaryWallState wall) {
        Vec3 edge = wall.center.add(wall.direction.scale(BOUNDARY_FLASH_LENGTH));
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_WALL,
                wall.center.x, wall.center.y + 0.05D, wall.center.z,
                edge.x, wall.center.y + 0.05D, edge.z,
                owner.getYRot(), 1.0F, -1, -1, BOUNDARY_WALL_VISUAL_TICKS,
                wall.id), wall.center);
    }

    void sendBoundaryGap(ServerLevel server, BoundaryWallState wall) {
        Vec3 edge = wall.center.add(wall.direction.scale(BOUNDARY_FLASH_LENGTH));
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP,
                wall.center.x, wall.center.y + 0.05D, wall.center.z,
                edge.x, wall.center.y + 0.05D, edge.z,
                owner.getYRot(), (float) wall.gapAlong, -1, -1,
                BOUNDARY_GAP_OPEN_TICKS, wall.id), wall.center);
    }

    void sendBoundaryGapWarning(ServerLevel server, BoundaryWallState wall) {
        Vec3 edge = wall.center.add(wall.direction.scale(BOUNDARY_FLASH_LENGTH));
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP_WARNING,
                wall.center.x, wall.center.y + 0.05D, wall.center.z,
                edge.x, wall.center.y + 0.05D, edge.z,
                owner.getYRot(), (float) wall.pendingGapAlong, -1, -1,
                BOUNDARY_GAP_WARNING_TICKS, wall.id), wall.center);
    }

    void clearBoundaryWalls(ServerLevel server, boolean playEffect) {
        if (playEffect) {
            for (BoundaryWallState wall : arena.boundaryWalls) {
                Vec3 edge = wall.center.add(wall.direction.scale(BOUNDARY_FLASH_LENGTH));
                owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                        BladeTechniqueVfxPacket.BOUNDARY_WALL_BREAK,
                        wall.center.x, wall.center.y + 0.05D, wall.center.z,
                        edge.x, wall.center.y + 0.05D, edge.z,
                        owner.getYRot(), 1.0F, -1, -1, 24,
                        wall.id), wall.center);
            }
            server.playSound(null, owner.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.HOSTILE, 1.25F, 0.58F);
        }
        arena.boundaryWalls.clear();
        arena.boundaryGapCycleTicks = 0;
    }

    Vec3 separatedBoundaryDirection(Vec3 candidate) {
        return arena.separatedBoundaryDirection(candidate);
    }

    Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        return arena.horizontalDirection(from, to, owner.getLookAngle());
    }

    static double smoothStep(double value) {
        return MikageArenaController.smoothStep(value);
    }

    void beginToriiSweep(ServerLevel server) {
        torii.beginSweep(server);
    }

    void tickToriiSweep(ServerLevel server) {
        torii.tickSweep(server);
    }

    void beginToriiCages(ServerLevel server) {
        torii.beginCages(server);
    }

    void tickToriiCages(ServerLevel server) {
        torii.tickCages(server);
    }

    boolean isBladeGuarding(ServerPlayer player) {
        ItemStack blade = player.getMainHandItem();
        return blade.getItem() instanceof ItemSlashBlade
                && player.getCapability(CapabilityInputState.INPUT_STATE)
                        .map(state -> state.getCommands(player).contains(InputCommand.R_DOWN))
                        .orElse(false);
    }

    void flashStepAwayFromNearestPlayer(ServerLevel server) {
        LivingEntity target = owner.getTarget();
        if (target != null && target.isAlive()) {
            flashStepAway(target, server, 10.5D);
        }
    }

    void advanceBoundaryFlashCharge(Technique technique, ServerLevel server) {
        if (owner.getPhase() != 3 || technique == Technique.NONE
                || technique == Technique.BOUNDARY_FLASH || arena.boundaryFlashPending) return;
        arena.boundaryFlashCharge = Math.min(3, arena.boundaryFlashCharge + 1);
        Vec3 arenaCenter = ChallengeManager.arenaCenter(owner);
        Vec3 focus = owner.position().add(0.0D, 1.0D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_CHARGE,
                arenaCenter.x, arenaCenter.y + 0.08D, arenaCenter.z,
                focus.x, focus.y, focus.z,
                owner.getYRot(), arena.boundaryFlashCharge, -1, owner.getId(), 60,
                owner.getRandom().nextInt()), arenaCenter);
        server.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 0.58F + arena.boundaryFlashCharge * 0.14F,
                0.72F + arena.boundaryFlashCharge * 0.16F);
        if (arena.boundaryFlashCharge >= 3) {
            arena.boundaryFlashPending = true;
            arena.boundaryFlashReadyTicks = 56;
        }
    }
}
