package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.challenge.mikage.TrialDamageMath;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Incoming hit gates and outgoing adaptive trial damage. Calls vanilla hurt exactly once after filtering. */
final class MikageDamageService {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;
    private record PendingImpact(Entity source, long tick, TrialDamageMath.Impact impact) { }
    private final Map<UUID, PendingImpact> pending = new HashMap<>();

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
        if (GameplayConfig.MIKAGE_ENABLE_PHASE_HEALTH_GATES.get()) {
            phaseFloor = owner.getPhase() == 1 ? owner.getMaxHealth() * 2.0F / 3.0F
                    : owner.getPhase() == 2 ? owner.getMaxHealth() / 3.0F : 0.0F;
        }
        boolean hurt = vanillaHurt.apply(source, amount);
        if (hurt) owner.duel().hitAccepted();
        if (hurt && attacker instanceof ServerPlayer player && owner.encounter().echo() != null)
            owner.encounter().echo().struck(player, source);
        if (hurt && phaseFloor > 0 && owner.isAlive() && owner.getHealth() < phaseFloor) {
            owner.setHealth(TrialDamageMath.preservePhaseFloor(owner.getHealth(), phaseFloor));
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

    float phaseLimitedDamage(float actual) {
        if (!GameplayConfig.MIKAGE_ENABLE_PHASE_HEALTH_GATES.get()) return actual;
        float floor = owner.getPhase() == 1 ? owner.getMaxHealth() * 2 / 3
                : owner.getPhase() == 2 ? owner.getMaxHealth() / 3 : 0;
        return TrialDamageMath.limitPhaseDamage(actual, owner.getHealth(), floor);
    }

    float nativeDamage(ServerPlayer player, Entity source, float raw) {
        if (!Float.isFinite(raw) || raw <= 0 || !owner.encounter().eligible(player)) return 0;
        var impact = TrialDamageMath.impact(raw / Math.max(1, owner.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        pending.entrySet().removeIf(entry -> entry.getValue().tick != owner.level().getGameTime());
        pending.put(player.getUUID(), new PendingImpact(source, owner.level().getGameTime(), impact));
        var profile = defense.playerDefenseProfiles.computeIfAbsent(player.getUUID(), id -> new PlayerDefenseProfile());
        double scale = GameplayConfig.MIKAGE_ADAPTIVE_PLAYER_DAMAGE.get() ? profile.rawMultiplier : 1;
        // A pre-armor health cap prevented adaptive correction from ever overcoming high armor.
        double corrected = raw * scale * GameplayConfig.MIKAGE_NATIVE_DAMAGE_MULTIPLIER.get();
        return (float) Math.min(corrected, Float.MAX_VALUE);
    }
    float finishNativeDamage(ServerPlayer player, Entity source, float actual, boolean cancelled) {
        var impact = pending.get(player.getUUID());
        if (impact == null || impact.source != source || impact.tick != owner.level().getGameTime() || cancelled) return actual;
        return TrialDamageMath.capFinal(actual, player.getMaxHealth(), impact.impact,
                GameplayConfig.MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT.get());
    }
    void nativeHitCompleted(ServerPlayer player, Entity source, float previousHealth, float previousAbsorption) {
        var impact = pending.remove(player.getUUID());
        if (impact == null || impact.source != source || impact.tick != owner.level().getGameTime()) return;
        float actual = Math.max(0, previousHealth - player.getHealth());
        boolean absorbed = player.getAbsorptionAmount() < previousAbsorption;
        // Observe AFTER native hurt returns: later event cancellation cannot fabricate a hit.
        if (actual > 0 || absorbed) MikageNativeCombat.accepted(source, player);
        if (!GameplayConfig.MIKAGE_ADAPTIVE_PLAYER_DAMAGE.get() || absorbed) return;
        var profile = defense.playerDefenseProfiles.computeIfAbsent(player.getUUID(), id -> new PlayerDefenseProfile());
        double desired = player.getMaxHealth() * Math.min(impact.impact.targetFraction,
                GameplayConfig.MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT.get());
        // Zero damage may be a cancellation or an immunity, not evidence of strong armor.
        if (actual <= 0) return;
        profile.rawMultiplier = TrialDamageMath.adapt(profile.rawMultiplier, actual, desired,
                GameplayConfig.MIKAGE_ADAPTIVE_DAMAGE_MAX_MULTIPLIER.get());
    }
    void clearPending() { pending.clear(); }

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

    @FunctionalInterface
    interface HurtOperation {
        boolean apply(DamageSource source, float amount);
    }
}
