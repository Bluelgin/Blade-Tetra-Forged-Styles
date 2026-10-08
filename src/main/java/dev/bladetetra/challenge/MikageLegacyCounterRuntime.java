package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageEntity.*;
import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Existing repeated-attack, lock-break and shadow-cross counters; shares the legacy state holders. */
final class MikageLegacyCounterRuntime {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;
    private final MikageTechniqueRuntime techniques;
    private final MikageArenaController arena;
    private final MikageAttackTimeline attackTimeline;

    MikageLegacyCounterRuntime(MikageEntity owner) {
        this.owner = owner;
        combat = owner.combatDirector();
        defense = owner.defenseController();
        techniques = owner.techniqueRuntime();
        arena = owner.arenaController();
        attackTimeline = owner.attackTimeline();
    }

    void clearQueuedPlayerBladeAttacks(ServerLevel server) {
        AABB area = owner.getBoundingBox().inflate(72.0D);
        server.getEntitiesOfClass(EntityJudgementCut.class, area,
                        cut -> cut.getOwner() instanceof Player)
                .forEach(Entity::discard);
        server.getEntitiesOfClass(EntitySlashEffect.class, area,
                        slash -> slash.getShooter() instanceof Player)
                .forEach(Entity::discard);
        server.getEntitiesOfClass(EntityAbstractSummonedSword.class, area,
                        sword -> sword.getShooter() instanceof Player)
                .forEach(Entity::discard);
    }

    float handleSlashArtPressure(LivingEntity attacker) {
        if (owner.duel().staggered() || owner.encounter().portalSkill()) return 1.0F;
        long now = owner.level().getGameTime();
        if (attacker.getPersistentData().getLong(MikageCounterEvents.SA_UNTIL) < now) {
            return 1.0F;
        }
        String kind = attacker.getPersistentData().getString(MikageCounterEvents.SA_KIND);
        int serial = attacker.getPersistentData().getInt(MikageCounterEvents.SA_SERIAL);
        SaPattern pattern = defense.saPatterns.computeIfAbsent(attacker.getUUID(), id -> new SaPattern());
        if (serial == pattern.lastSerial) {
            return pattern.currentMultiplier;
        }
        pattern.lastSerial = serial;
        if (!kind.equals(pattern.kind) || now - pattern.lastHit > 80L) {
            pattern.kind = kind;
            pattern.hits = 0;
        }
        pattern.lastHit = now;
        pattern.hits++;

        // Intentional stagger windows are the reward for solving mechanics; never adapt
        // to or counter a player's burst during them.
        if (owner.duel().staggered() || techniques.interactionOpeningTicks > 0 || defense.swordWheelBreakTicks > 0
                || ((arena.cagePerfectCountered || arena.toriiScissorCountered || techniques.pursuitRainCountered)
                && combat.signatureRecoveryTicks > 0)) {
            pattern.hits = 0;
            pattern.currentMultiplier = 1.0F;
            return 1.0F;
        }

        if (pattern.hits == 2 && owner.level() instanceof ServerLevel server) {
            server.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 0.75F, 1.85F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(1.0F, 0.02F, 0.06F), 1.35F),
                    owner.getX(), owner.getY() + 1.25D, owner.getZ(), 24,
                    0.55D, 0.75D, 0.55D, 0.035D);
        }
        if (pattern.hits >= 3 && defense.mirrorCounterCooldown <= 0) {
            pattern.hits = 0;
            defense.mirrorCounterCooldown = 75;
            performMirrorCounter(attacker);
            pattern.currentMultiplier = GameplayConfig.MIKAGE_MIRROR_COUNTER_DAMAGE_MULTIPLIER
                    .get().floatValue();
            return pattern.currentMultiplier;
        }
        pattern.currentMultiplier = pattern.hits >= 2
                ? GameplayConfig.MIKAGE_REPEATED_SA_DAMAGE_MULTIPLIER.get().floatValue()
                : 1.0F;
        return pattern.currentMultiplier;
    }

    void registerJudgementCutCast(LivingEntity attacker) {
        if (owner.encounter().portalSkill()) return;
        long now = owner.level().getGameTime();
        JudgementPattern pattern = defense.judgementPatterns.computeIfAbsent(
                attacker.getUUID(), id -> new JudgementPattern());
        int chainWindow = GameplayConfig.MIKAGE_JUDGEMENT_CHAIN_WINDOW_TICKS.get();
        int resetWindow = GameplayConfig.MIKAGE_JUDGEMENT_RESET_TICKS.get();
        if (now - pattern.lastCast > Math.min(chainWindow, resetWindow)) {
            pattern.casts = 0;
            pattern.blockedUntil = Long.MIN_VALUE;
        }
        pattern.lastCast = now;

        // Solving a mechanic deliberately creates an unrestricted burst window.
        if (owner.duel().staggered() || techniques.interactionOpeningTicks > 0 || defense.swordWheelBreakTicks > 0
                || ((arena.cagePerfectCountered || arena.toriiScissorCountered || techniques.pursuitRainCountered)
                && combat.signatureRecoveryTicks > 0)) {
            pattern.casts = 0;
            pattern.blockedUntil = Long.MIN_VALUE;
            return;
        }

        pattern.casts = Math.min(3, pattern.casts + 1);
        if (pattern.casts == 2 && owner.level() instanceof ServerLevel server) {
            server.playSound(null, owner.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 0.8F, 1.9F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.95F, 0.02F, 0.08F), 1.25F),
                    owner.getX(), owner.getY() + 1.25D, owner.getZ(), 20,
                    0.5D, 0.7D, 0.5D, 0.03D);
        } else if (pattern.casts >= 3 && now >= pattern.blockedUntil) {
            pattern.blockedUntil = now
                    + GameplayConfig.MIKAGE_JUDGEMENT_LOCKOUT_TICKS.get();
            if (owner.level() instanceof ServerLevel server) {
                defense.mirrorCounterCooldown = Math.max(defense.mirrorCounterCooldown, 75);
                clearJudgementCuts(server, attacker.getUUID());
                performMirrorCounter(attacker);
                server.playSound(null, owner.blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.HOSTILE, 1.1F, 0.65F);
                server.sendParticles(new DustParticleOptions(
                                new Vector3f(0.85F, 0.01F, 0.05F), 1.55F),
                        owner.getX(), owner.getY() + 1.2D, owner.getZ(), 48,
                        1.0D, 1.0D, 1.0D, 0.08D);
            }
        }
    }

    float judgementCutDamageMultiplier(LivingEntity attacker) {
        if (owner.duel().staggered() || owner.encounter().portalSkill()) return 1.0F;
        JudgementPattern pattern = defense.judgementPatterns.get(attacker.getUUID());
        if (pattern == null) {
            return 1.0F;
        }
        long now = owner.level().getGameTime();
        if (now < pattern.blockedUntil) {
            return 0.0F;
        }
        return pattern.casts >= 2
                ? GameplayConfig.MIKAGE_SECOND_JUDGEMENT_DAMAGE_MULTIPLIER.get().floatValue()
                : 1.0F;
    }

    boolean isJudgementCutBlocked(LivingEntity attacker) {
        if (owner.encounter().portalSkill()) return false;
        JudgementPattern pattern = defense.judgementPatterns.get(attacker.getUUID());
        return pattern != null && owner.level().getGameTime() < pattern.blockedUntil;
    }

    void tickJudgementCutAdaptation(ServerLevel server) {
        long now = owner.level().getGameTime();
        int resetTicks = GameplayConfig.MIKAGE_JUDGEMENT_RESET_TICKS.get();
        defense.judgementPatterns.entrySet().removeIf(entry -> {
            JudgementPattern pattern = entry.getValue();
            if (now - pattern.lastCast > resetTicks) {
                return true;
            }
            if (now < pattern.blockedUntil) {
                clearJudgementCuts(server, entry.getKey());
            }
            return false;
        });
    }

    private void clearJudgementCuts(ServerLevel server, UUID ownerId) {
        server.getEntitiesOfClass(EntityJudgementCut.class,
                        owner.getBoundingBox().inflate(72.0D), cut -> cut.getOwner() != null
                                && ownerId.equals(cut.getOwner().getUUID()))
                .forEach(Entity::discard);
    }

    private void performMirrorCounter(LivingEntity attacker) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        defense.lockBreakUntil.put(attacker.getUUID(), owner.level().getGameTime() + 16L);
        clearPlayerLock(attacker);
        if (!owner.isSwordWheelDeployed() && owner.getSwordWheelCount() > 0) {
            owner.swordWheel().deploySwordWheel(attacker);
        } else {
            owner.legacyEffects().flashStepAway(attacker, server, 8.5D);
        }
        owner.setAction(MikageAction.IAIDO_DRAW, 15);
        Vec3 targetAtCast = attacker.position();
        attackTimeline.circle(12, targetAtCast, 3.2D, 3.2D,
                (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.78F, 0.72D);
        server.playSound(null, owner.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.HOSTILE, 1.0F, 1.35F);
        Vec3 direction = targetAtCast.subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() > 0.001D) {
            Vec3 end = targetAtCast.add(direction.normalize().scale(7.0D));
            attackTimeline.line(14, targetAtCast.subtract(direction.normalize().scale(4.0D)),
                    end, 1.25D, 3.0D,
                    (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.72F, 0.68D);
        }
    }

    void clearPlayerLock(LivingEntity attacker) {
        ItemStack blade = attacker.getMainHandItem();
        blade.getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> state.setTargetEntityId(-1));
    }

    void tickBrokenLocks(ServerLevel server) {
        long now = server.getGameTime();
        defense.lockBreakUntil.entrySet().removeIf(entry -> {
            if (entry.getValue() < now) return true;
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) clearPlayerLock(player);
            return false;
        });
        if (owner.tickCount % 100 == 0) {
            defense.saPatterns.entrySet().removeIf(entry -> now - entry.getValue().lastHit > 200L);
        }
    }

    void sampleParticipantMovement(ServerLevel server) {
        long now = server.getGameTime();
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || !ChallengeManager.isParticipant(owner, player)) continue;
            Deque<MovementSample> samples = defense.movementSamples.computeIfAbsent(player.getUUID(),
                    id -> new ArrayDeque<>());
            samples.addLast(new MovementSample(now, player.position()));
            while (!samples.isEmpty() && (samples.size() > 10
                    || now - samples.peekFirst().tick() > 10L)) {
                samples.removeFirst();
            }
        }
        defense.movementSamples.keySet().removeIf(id -> server.getPlayerByUUID(id) == null);
    }

    void registerShadowCrossIaido(ServerPlayer player, long now) {
        if (owner.duel().staggered() || !isIaidoAttack(player) || techniques.interactionOpeningTicks > 0 || owner.encounter().isWindingUp()
                || techniques.isSignatureActive(arena, defense)) return;
        Deque<MovementSample> samples = defense.movementSamples.get(player.getUUID());
        if (samples == null || samples.isEmpty()) return;
        MovementSample startSample = null;
        for (MovementSample sample : samples) {
            long age = now - sample.tick();
            if (age >= 2L && age <= 8L) {
                startSample = sample;
                break;
            }
        }
        if (startSample == null) return;
        Vec3 start = startSample.position();
        Vec3 end = player.position();
        Vec3 travel = end.subtract(start).multiply(1.0D, 0.0D, 1.0D);
        if (travel.lengthSqr() < 9.0D
                || MikageLegacySkillEffects.distanceToSegment(owner.position(), start, end) > 1.65D) return;
        Vec3 before = start.subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 after = end.subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        if (before.lengthSqr() < 0.64D || after.lengthSqr() < 0.64D
                || before.dot(after) >= -0.35D) return;

        Vec3 direction = travel.normalize();
        ShadowCrossPattern pattern = defense.shadowCrossPatterns.computeIfAbsent(player.getUUID(),
                id -> new ShadowCrossPattern());
        int window = GameplayConfig.MIKAGE_SHADOW_CROSS_WINDOW_TICKS.get();
        boolean sameRoute = now - pattern.lastCross <= window
                && pattern.lastDirection.lengthSqr() > 0.01D
                // Back-and-forth passes along the same axis are still one learned
                // route; a genuinely different exit angle breaks the pattern.
                && Math.abs(pattern.lastDirection.dot(direction)) >= 0.72D;
        pattern.crossings = sameRoute ? pattern.crossings + 1 : 1;
        pattern.lastCross = now;
        pattern.lastDirection = direction;
        if (pattern.crossings < GameplayConfig.MIKAGE_SHADOW_CROSS_THRESHOLD.get()
                || defense.zanshinCounterTicks > 0) return;
        pattern.crossings = 1;
        beginZanshinCounter(player, direction, pattern);
    }

    private boolean isIaidoAttack(ServerPlayer player) {
        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ItemSlashBlade)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) return false;
        return blade.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> ModComboStates.isIaidoAttack(state.getComboSeq()))
                .orElse(false);
    }

    private void beginZanshinCounter(ServerPlayer player, Vec3 route,
            ShadowCrossPattern pattern) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        if (owner.isSwordWheelDeployed()) owner.swordWheel().recallSwordWheel(server, 60);
        defense.zanshinCounterTicks = GameplayConfig.MIKAGE_ZANSHIN_WARNING_TICKS.get();
        defense.zanshinCounterTarget = player.getUUID();
        defense.zanshinCounterCenter = ChallengeManager.clampMikagePosition(owner,
                player.position().add(route.scale(1.4D)));
        owner.getNavigation().stop();
        owner.setAction(MikageAction.IAIDO_READY, defense.zanshinCounterTicks);
        if (!pattern.warned) {
            pattern.warned = true;
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.mikage.shadow_cross_read"), true);
        }
        server.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 0.85F, 1.9F);
    }

    void tickZanshinCounter(ServerLevel server) {
        owner.getNavigation().stop();
        Entity found = defense.zanshinCounterTarget == null ? null : server.getEntity(defense.zanshinCounterTarget);
        ServerPlayer target = found instanceof ServerPlayer player ? player : null;
        if (target != null && target.isAlive()) owner.lookAt(target, 180.0F, 180.0F);
        double radius = 2.8D;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2.0D / 24.0D;
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.92F, 0.025F, 0.10F), 0.85F),
                    defense.zanshinCounterCenter.x + Math.cos(angle) * radius,
                    defense.zanshinCounterCenter.y + 0.12D,
                    defense.zanshinCounterCenter.z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (--defense.zanshinCounterTicks > 0) return;
        if (target != null && target.isAlive()
                && target.position().distanceToSqr(defense.zanshinCounterCenter) <= radius * radius) {
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * GameplayConfig.MIKAGE_ZANSHIN_DAMAGE_MULTIPLIER.get().floatValue();
            owner.dealTrialDamage(target, damage, owner.legacyEffects().isBladeGuarding(target));
        }
        AttackManager.doSlash(owner, 180.0F, true, false, 0.0D);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                defense.zanshinCounterCenter.x, defense.zanshinCounterCenter.y + 0.9D,
                defense.zanshinCounterCenter.z, 16, 1.8D, 0.5D, 1.8D, 0.0D);
        server.playSound(null, BlockPos.containing(defense.zanshinCounterCenter),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.25F, 1.7F);
        defense.zanshinCounterTarget = null;
        combat.techniqueCooldown = Math.max(combat.techniqueCooldown, 18);
        owner.setAction(MikageAction.IAIDO_DRAW, 10);
    }

    boolean hasShadowCrossPressure(LivingEntity target) {
        ShadowCrossPattern pattern = defense.shadowCrossPatterns.get(target.getUUID());
        return pattern != null && pattern.crossings >= 2
                && owner.level().getGameTime() - pattern.lastCross
                <= GameplayConfig.MIKAGE_SHADOW_CROSS_WINDOW_TICKS.get();
    }

}
