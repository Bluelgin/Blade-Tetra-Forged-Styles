package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageLegacyTiming.*;

import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.network.BladeCombatVfxPacket;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Incoming hit gates and outgoing adaptive trial damage. Calls vanilla hurt exactly once after filtering. */
final class MikageDamageService {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;
    private final MikageTechniqueRuntime techniques;
    private final MikageArenaController arena;

    MikageDamageService(MikageEntity owner) {
        this.owner = owner;
        combat = owner.combatDirector();
        defense = owner.defenseController();
        techniques = owner.techniqueRuntime();
        arena = owner.arenaController();
    }

    boolean dealTrialDamage(ServerPlayer player, float rawDamage, boolean guarded) {
        return dealTrialDamage(player, rawDamage, guarded ? 0.25F : 1.0F,
                guarded, false);
    }

    private boolean dealTrialDamage(ServerPlayer player, float rawDamage,
            float mechanicMultiplier, boolean protectedResponse, boolean forceBoundarySource) {
        if (rawDamage <= 0.0F || !player.isAlive() || player.isCreative()
                || player.isSpectator() || !ChallengeManager.isParticipant(owner, player)) {
            return false;
        }
        double baseAttack = Math.max(1.0D, owner.getAttributeValue(Attributes.ATTACK_DAMAGE));
        TrialImpact impact = TrialImpact.from(rawDamage / baseAttack);
        PlayerDefenseProfile profile = defense.playerDefenseProfiles.computeIfAbsent(player.getUUID(),
                id -> new PlayerDefenseProfile());
        boolean adaptive = GameplayConfig.MIKAGE_ADAPTIVE_PLAYER_DAMAGE.get()
                && !protectedResponse;
        double responseFactor = Mth.clamp(mechanicMultiplier, 0.0F, 1.0F);
        double scale = adaptive ? profile.rawMultiplier : 1.0D;
        double maxFraction = Math.min(impact.maximumFraction,
                GameplayConfig.MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT.get());
        float requested = (float) Math.min(rawDamage * scale * responseFactor,
                player.getMaxHealth() * maxFraction * responseFactor);
        if (requested <= 0.0F) return false;

        boolean boundaryAssist = adaptive && profile.boundaryAssistHits > 0;
        DamageSource source = boundaryAssist || forceBoundarySource
                ? owner.level().damageSources().indirectMagic(owner, owner)
                : owner.level().damageSources().mobAttack(owner);
        float before = player.getHealth() + player.getAbsorptionAmount();
        int immunityBefore = player.invulnerableTime;
        boolean hurt = player.hurt(source, requested);
        // A fatal hit can synchronously eject the player to another dimension.
        if (player.level() != owner.level() || !ChallengeManager.isParticipant(owner, player)) {
            return false;
        }
        float after = player.getHealth() + player.getAbsorptionAmount();
        float actual = Math.max(0.0F, before - after);

        if (adaptive && immunityBefore <= 0) {
            double desired = player.getMaxHealth() * impact.targetFraction;
            if (boundaryAssist) profile.boundaryAssistHits--;
            if (actual < desired * 0.50D) {
                profile.lowDamageHits++;
                profile.rawMultiplier = Math.min(
                        GameplayConfig.MIKAGE_ADAPTIVE_DAMAGE_MAX_MULTIPLIER.get(),
                        profile.rawMultiplier * 1.22D);
                if (profile.lowDamageHits
                        >= GameplayConfig.MIKAGE_BOUNDARY_ASSIST_LOW_HITS.get()) {
                    profile.lowDamageHits = 0;
                    profile.boundaryAssistHits = Math.max(profile.boundaryAssistHits, 3);
                }
            } else {
                profile.lowDamageHits = 0;
                if (actual > desired * 1.60D) {
                    profile.rawMultiplier = Math.max(0.65D,
                            profile.rawMultiplier * 0.86D);
                } else if (profile.rawMultiplier > 1.0D) {
                    profile.rawMultiplier = Math.max(1.0D,
                            profile.rawMultiplier * 0.985D);
                }
            }
        }
        return hurt;
    }

    boolean dealAdjustedTrialDamage(ServerPlayer player, float adjustedDamage,
            boolean protectedResponse) {
        return dealTrialDamage(player, adjustedDamage, 1.0F, protectedResponse, true);
    }

    boolean hurt(DamageSource source, float amount, HurtOperation vanillaHurt) {
        if (owner.isVisitorGuide()) {
            return false;
        }
        if (Float.isNaN(amount) || amount <= 0.0F) {
            return false;
        }
        if (Float.isInfinite(amount)) {
            amount = GameplayConfig.MIKAGE_SINGLE_HIT_CAP.get().floatValue();
        }
        Entity attacker = resolveCombatAttacker(source);
        if (attacker instanceof ServerPlayer observed) owner.encounter().observeAttack(observed);
        if (attacker instanceof ServerPlayer player
                && !ChallengeManager.isParticipant(owner, player)) {
            return false;
        }
        if (combat.phaseProtectionTicks > 0) {
            return false;
        }
        if (attacker instanceof ServerPlayer player) {
            if (techniques.moonEchoTicks > 0 && ChallengeManager.isParticipant(owner, player)) {
                owner.legacyEffects().solveMoonEcho(player);
                return false;
            }
            if (techniques.mirrorDuelTicks <= MIRROR_DUEL_DASH_START && techniques.mirrorDuelTicks >= 5
                    && player.getUUID().equals(techniques.mirrorDuelTarget)) {
                owner.legacyEffects().counterMirrorDuel(player);
                return false;
            }
        }
        long now = owner.level().getGameTime();
        if (attacker != null
                && defense.hurtCooldownUntil.getOrDefault(attacker.getUUID(), Long.MIN_VALUE) > now) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof EntityJudgementCut cut && cut.getOwner() instanceof LivingEntity cutOwner) {
            attacker = cutOwner;
            amount *= owner.counters().judgementCutDamageMultiplier(cutOwner);
            if (amount <= 0.0F) {
                return false;
            }
        } else if (attacker instanceof LivingEntity living) {
            amount *= owner.counters().handleSlashArtPressure(living);
        }
        if (attacker instanceof LivingEntity living && owner.distanceToSqr(living) <= 25.0D
                && defense.swordWheelBreakTicks <= 0 && combat.signatureRecoveryTicks <= 0
                && techniques.interactionOpeningTicks <= 0) {
            owner.swordWheel().registerClosePressure(living);
            if (owner.isSwordWheelDeployed() && defense.swordWheelCounterCooldown <= 0) {
                defense.swordWheelCounterCooldown = 14;
                owner.swordWheel().counterWithSwordWheel(living);
                amount *= 0.70F;
            }
        }
        float threshold = GameplayConfig.MIKAGE_SOFT_CAP_THRESHOLD.get().floatValue();
        if (amount > threshold) {
            amount = Math.min(GameplayConfig.MIKAGE_SINGLE_HIT_CAP.get().floatValue(),
                    threshold + (amount - threshold)
                            * GameplayConfig.MIKAGE_SOFT_CAP_OVERFLOW_RATIO.get().floatValue());
        }
        if (combat.signatureRecoveryTicks <= 0 && owner.getPhase() == 1
                && owner.tickCount % 70 < 16 && source.getEntity() != null) {
            amount *= 0.25F;
            if (owner.level() instanceof ServerLevel server) {
                long effectNow = server.getGameTime();
                if (effectNow - defense.lastParryVfxTick >= 3L) {
                    defense.lastParryVfxTick = effectNow;
                    Vec3 direction = attacker == null ? owner.getLookAngle()
                            : attacker.position().subtract(owner.position());
                    direction = direction.multiply(1.0D, 0.0D, 1.0D);
                    if (direction.lengthSqr() < 0.001D) {
                        direction = owner.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                    }
                    Vec3 impact = owner.position().add(direction.normalize().scale(0.72D))
                            .add(0.0D, 1.15D, 0.0D);
                    owner.presentation().sendCombatVfx(server, BladeCombatVfxPacket.PARRY, impact,
                            owner.getYRot(), 1.0F,
                            attacker instanceof ServerPlayer player ? player.getId() : -1);
                    owner.presentation().playBladeParrySound(server, impact, SoundSource.HOSTILE, false);
                }
            }
        }
        if (techniques.boundarySealTicks > 0 || techniques.moonEchoTicks > 0
                || arena.toriiSweepTicks > TORII_SWEEP_RECOVERY_TICKS
                || arena.toriiCageTicks > TORII_CAGE_RECOVERY_TICKS) {
            amount *= 0.25F;
        } else if (techniques.interactionOpeningTicks > 0) {
            amount *= techniques.interactionOpeningMultiplier;
        } else if (combat.signatureRecoveryTicks > 0) {
            amount *= techniques.pursuitRainCountered
                    ? GameplayConfig.MIKAGE_PURSUIT_RAIN_STAGGER_DAMAGE_MULTIPLIER.get().floatValue()
                    : (arena.cagePerfectCountered || arena.toriiScissorCountered || defense.swordWheelBreakTicks > 0)
                    ? GameplayConfig.MIKAGE_CAGE_STAGGER_DAMAGE_MULTIPLIER.get().floatValue()
                    : 1.25F;
        }
        float phaseFloor = 0.0F;
        boolean reachesPhaseGate = false;
        if (GameplayConfig.MIKAGE_ENABLE_PHASE_HEALTH_GATES.get()) {
            phaseFloor = owner.getPhase() == 1 ? owner.getMaxHealth() * 2.0F / 3.0F
                    : owner.getPhase() == 2 ? owner.getMaxHealth() / 3.0F : 0.0F;
            if (phaseFloor > 0.0F) {
                float remaining = Math.max(0.0F, owner.getHealth() - phaseFloor);
                reachesPhaseGate = amount >= remaining;
                amount = Math.min(amount, remaining);
                if (amount <= 0.0F) {
                    return false;
                }
            }
        }
        boolean hurt = vanillaHurt.apply(source, amount);
        if (hurt && reachesPhaseGate && owner.isAlive()) {
            owner.setHealth(phaseFloor);
        }
        if (hurt && attacker != null) {
            // Replace vanilla's one-global-timer immunity with Mikage's per-attacker
            // ledger so one participant never consumes another participant's hit.
            owner.invulnerableTime = 0;
            boolean rewardedOpening = defense.swordWheelBreakTicks > 0
                    || techniques.interactionOpeningTicks > 0
                    || ((arena.cagePerfectCountered || arena.toriiScissorCountered || techniques.pursuitRainCountered)
                    && combat.signatureRecoveryTicks > 0);
            int cooldown = rewardedOpening
                    ? GameplayConfig.MIKAGE_OPENING_HURT_COOLDOWN_TICKS.get()
                    : GameplayConfig.MIKAGE_HURT_COOLDOWN_TICKS.get();
            if (cooldown > 0) {
                defense.hurtCooldownUntil.put(attacker.getUUID(), now + cooldown);
            }
            if (attacker instanceof ServerPlayer player && !rewardedOpening
                    && combat.signatureRecoveryTicks <= 0) {
                owner.pursuitRain().registerPressure(player, now);
            }
            if (attacker instanceof ServerPlayer player) {
                owner.counters().registerShadowCrossIaido(player, now);
            }
        }
        return hurt;
    }

    static Entity resolveCombatAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (direct instanceof EntityJudgementCut cut && cut.getOwner() != null) {
            return cut.getOwner();
        }
        if (direct instanceof EntitySlashEffect slash && slash.getShooter() != null) {
            return slash.getShooter();
        }
        if (direct instanceof EntityAbstractSummonedSword sword && sword.getShooter() != null) {
            return sword.getShooter();
        }
        return attacker;
    }

    private enum TrialImpact {
        LIGHT(0.06D, 0.14D),
        NORMAL(0.11D, 0.24D),
        HEAVY(0.20D, 0.36D),
        FAILURE(0.34D, 0.55D);

        final double targetFraction;
        final double maximumFraction;

        TrialImpact(double targetFraction, double maximumFraction) {
            this.targetFraction = targetFraction;
            this.maximumFraction = maximumFraction;
        }

        static TrialImpact from(double attackRatio) {
            if (attackRatio <= 0.45D) return LIGHT;
            if (attackRatio <= 0.95D) return NORMAL;
            if (attackRatio <= 1.30D) return HEAVY;
            return FAILURE;
        }
    }

    @FunctionalInterface
    interface HurtOperation {
        boolean apply(DamageSource source, float amount);
    }
}
