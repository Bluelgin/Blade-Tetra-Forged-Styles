package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.config.GameplayConfig;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Incoming hit gates and outgoing adaptive trial damage. Calls vanilla hurt exactly once after filtering. */
final class MikageDamageService {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;

    MikageDamageService(MikageEntity owner) {
        this.owner = owner;
        combat = owner.combatDirector();
        defense = owner.defenseController();
    }

    boolean hurt(DamageSource source, float amount, HurtOperation vanillaHurt) {
        if (owner.isVisitorGuide() || owner.isWithinThousandGates()) {
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
        long now = owner.level().getGameTime();
        if (attacker != null
                && defense.hurtCooldownUntil.getOrDefault(attacker.getUUID(), Long.MIN_VALUE) > now) {
            return false;
        }
        if (owner.duel().intercept(source)) return false;
        float threshold = GameplayConfig.MIKAGE_SOFT_CAP_THRESHOLD.get().floatValue();
        if (amount > threshold) {
            amount = Math.min(GameplayConfig.MIKAGE_SINGLE_HIT_CAP.get().floatValue(),
                    threshold + (amount - threshold)
                            * GameplayConfig.MIKAGE_SOFT_CAP_OVERFLOW_RATIO.get().floatValue());
        }
        if (owner.duel().staggered()) amount *= 1.25F;
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
        if (hurt) owner.duel().hitAccepted();
        if (hurt && attacker instanceof ServerPlayer player && owner.encounter().echo() != null)
            owner.encounter().echo().struck(player, source);
        if (hurt && reachesPhaseGate && owner.isAlive()) {
            owner.setHealth(phaseFloor);
        }
        if (hurt && attacker != null) {
            // Replace vanilla's one-global-timer immunity with Mikage's per-attacker
            // ledger so one participant never consumes another participant's hit.
            owner.invulnerableTime = 0;
            boolean rewardedOpening = owner.duel().staggered();
            int cooldown = rewardedOpening
                    ? GameplayConfig.MIKAGE_OPENING_HURT_COOLDOWN_TICKS.get()
                    : GameplayConfig.MIKAGE_HURT_COOLDOWN_TICKS.get();
            if (cooldown > 0) {
                defense.hurtCooldownUntil.put(attacker.getUUID(), now + cooldown);
            }
        }
        return hurt;
    }

    float nativeDamage(ServerPlayer player, float raw) {
        if (!Float.isFinite(raw) || raw <= 0 || !owner.encounter().eligible(player)) return 0;
        var impact = TrialImpact.from(raw / Math.max(1, owner.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        var profile = defense.playerDefenseProfiles.computeIfAbsent(player.getUUID(), id -> new PlayerDefenseProfile());
        double scale = GameplayConfig.MIKAGE_ADAPTIVE_PLAYER_DAMAGE.get() ? profile.rawMultiplier : 1;
        return (float) Math.min(raw * scale, player.getMaxHealth() * Math.min(impact.maximumFraction,
                GameplayConfig.MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT.get()));
    }
    void nativeDamageAccepted(ServerPlayer player, float actual) {
        if (!GameplayConfig.MIKAGE_ADAPTIVE_PLAYER_DAMAGE.get()) return;
        var profile = defense.playerDefenseProfiles.computeIfAbsent(player.getUUID(), id -> new PlayerDefenseProfile());
        double desired = player.getMaxHealth() * .11;
        if (actual < desired * .5) profile.rawMultiplier = Math.min(
                GameplayConfig.MIKAGE_ADAPTIVE_DAMAGE_MAX_MULTIPLIER.get(), profile.rawMultiplier * 1.22);
        else if (actual > desired * 1.6) profile.rawMultiplier = Math.max(.65, profile.rawMultiplier * .86);
        else if (profile.rawMultiplier > 1) profile.rawMultiplier = Math.max(1, profile.rawMultiplier * .985);
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
