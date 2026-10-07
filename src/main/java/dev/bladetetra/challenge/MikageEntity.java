package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageArenaController.*;
import static dev.bladetetra.challenge.MikageDefenseController.*;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.network.BladeCombatVfxPacket;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import com.mojang.logging.LogUtils;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityHeavyRainSwords;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class MikageEntity extends Monster {
    static final Logger LOGGER = LogUtils.getLogger();
    static final int TORII_SWEEP_TOTAL_TICKS = 154;
    static final int TORII_SWEEP_FIRST_IMPACT_AGE = 34;
    static final int TORII_SWEEP_SECOND_IMPACT_AGE = 72;
    static final int TORII_SWEEP_FAKE_IMPACT_AGE = 108;
    static final int TORII_SWEEP_FINAL_IMPACT_AGE = 136;
    static final int TORII_SWEEP_STABLE_GUARD_TICKS = 6;
    static final int TORII_SWEEP_RECOVERY_TICKS = 18;
    static final int TORII_CAGE_TOTAL_TICKS = 110;
    static final int TORII_CAGE_WARNING_TICKS = 25;
    static final int TORII_CAGE_RECOVERY_TICKS = 10;
    static final double TORII_CAGE_RADIUS = 4.6D;
    static final int PURSUIT_RAIN_WARNING_TICKS = 20;
    static final int PURSUIT_RAIN_FINAL_TICKS = 22;
    static final int PURSUIT_RAIN_FINAL_LAUNCH_TICK = 8;
    static final int MIRROR_DUEL_TOTAL_TICKS = 30;
    static final int MIRROR_DUEL_DASH_START = 16;
    static final int BOUNDARY_SEAL_TOTAL_TICKS = 140;
    static final int MOON_ECHO_TOTAL_TICKS = 72;
    static final int BOUNDARY_FLASH_TOTAL_TICKS = 230;
    static final int BOUNDARY_FLASH_ASCEND_TICKS = 32;
    static final int BOUNDARY_FLASH_LOCK_AGE = 180;
    static final int BOUNDARY_FLASH_IMPACT_AGE = 206;
    static final int BOUNDARY_FLASH_DESCEND_AGE = 216;
    static final int BOUNDARY_WALL_VISUAL_TICKS = 1_000_000;
    static final int BOUNDARY_WALL_MAX_COUNT = 6;
    static final int BOUNDARY_GAP_OPEN_TICKS = 60;
    static final int BOUNDARY_GAP_WARNING_TICKS = 20;
    static final double BOUNDARY_GAP_HALF_WIDTH = 1.65D;
    static final int BOUNDARY_FLAME_DAMAGE_INTERVAL = 4;
    static final float BOUNDARY_FLAME_HEALTH_FRACTION = 0.085F;
    static final double BOUNDARY_FLAME_HALF_WIDTH = 1.05D;
    static final double BOUNDARY_FLAME_HEIGHT = 4.25D;
    static final double BOUNDARY_FLASH_LENGTH = 53.0D;
    static final ResourceKey<DamageType> BOUNDARY_FLAME_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "boundary_flame"));
    static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> ACTION_START =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> ACTION_LENGTH =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> SWORD_WHEEL_COUNT =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> SWORD_WHEEL_DEPLOYED =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Boolean> VISITOR_GUIDE =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.blade_tetra.mikage"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS);
    private final MikageCombatDirector combat = new MikageCombatDirector();
    private final MikageDefenseController defense = new MikageDefenseController();
    private final MikageTechniqueRuntime techniques = new MikageTechniqueRuntime();
    private final MikageArenaController arena = new MikageArenaController();
    private final MikageAttackTimeline attackTimeline = new MikageAttackTimeline(this);
    private final MikagePursuitRainController pursuitRain = new MikagePursuitRainController(this);
    private final MikageLegacySkillEffects legacyEffects = new MikageLegacySkillEffects(this);
    private final MikageEncounter encounter = new MikageEncounter(this);

    public MikageEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 80;
    }

    MikageCombatDirector combatDirector() { return combat; }
    MikageDefenseController defenseController() { return defense; }
    MikageTechniqueRuntime techniqueRuntime() { return techniques; }
    MikageArenaController arenaController() { return arena; }
    MikageAttackTimeline attackTimeline() { return attackTimeline; }

    MikageEncounter encounter() { return encounter; }
    MikageLegacySkillEffects legacyEffects() { return legacyEffects; }
    ServerBossEvent bossBar() { return bossBar; }
    void setPhase(int phase) { entityData.set(PHASE, phase); }
    int actionRemainingTicks() {
        return getAction() == MikageAction.IDLE ? 0
                : Math.max(0, entityData.get(ACTION_LENGTH) - (tickCount - entityData.get(ACTION_START)));
    }
    boolean legacySkillBusy() {
        return techniques.isSignatureActive(arena, defense)
                || combat.signatureRecoveryTicks > 0 || defense.swordWheelBreakTicks > 0;
    }
    public void restoreCombatHealthFraction(float fraction) {
        setHealth(getMaxHealth() * Mth.clamp(fraction, 0.01F, 1.0F));
        encounter.restorePhase(getHealth() / getMaxHealth());
    }

    boolean shouldResetExpiredAction() {
        return getAction() != MikageAction.IDLE
                && tickCount - entityData.get(ACTION_START) > entityData.get(ACTION_LENGTH)
                && !encounter.isWindingUp()
                && !isUsingSignatureTechnique();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 560.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE, 1);
        entityData.define(ACTION, MikageAction.IDLE.ordinal());
        entityData.define(ACTION_START, 0);
        entityData.define(ACTION_LENGTH, 1);
        entityData.define(SWORD_WHEEL_COUNT, 6);
        entityData.define(SWORD_WHEEL_DEPLOYED, false);
        entityData.define(VISITOR_GUIDE, false);
    }

    public MikageAction getAction() {
        int value = entityData.get(ACTION);
        return value >= 0 && value < MikageAction.values().length
                ? MikageAction.values()[value] : MikageAction.IDLE;
    }

    public float getActionProgress(float partialTick) {
        int elapsed = tickCount - entityData.get(ACTION_START);
        return Mth.clamp((elapsed + partialTick)
                / Math.max(1.0F, entityData.get(ACTION_LENGTH)), 0.0F, 1.0F);
    }

    /** Read-only client pose discriminator; damage and technique timing remain server-owned. */
    public boolean isBoundaryFlashPose() {
        return getAction() == MikageAction.RITUAL
                && entityData.get(ACTION_LENGTH) == BOUNDARY_FLASH_TOTAL_TICKS;
    }

    void setAction(MikageAction action, int duration) {
        entityData.set(ACTION, action.ordinal());
        entityData.set(ACTION_START, tickCount);
        entityData.set(ACTION_LENGTH, Math.max(1, duration));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MikageSpacingGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
                target -> target instanceof ServerPlayer player
                        && ChallengeManager.isParticipant(this, player)));
    }

    public int getPhase() {
        return entityData.get(PHASE);
    }

    public int getSwordWheelCount() {
        return entityData.get(SWORD_WHEEL_COUNT);
    }

    public boolean isSwordWheelDeployed() {
        return entityData.get(SWORD_WHEEL_DEPLOYED);
    }

    UUID bossEventId() {
        return bossBar.getId();
    }

    public void configureForParty(Collection<ServerPlayer> party, boolean reminiscence,
            long challengeId) {
        // Challenge bosses are managed explicitly by ChallengeManager. Mark the combat
        // form as persistent as well as the visitor form so vanilla mob despawning can
        // never remove Mikage while a challenge is in progress.
        setPersistenceRequired();
        int players = Math.max(1, party.size());
        PowerCalibration calibration = GameplayConfig.MIKAGE_AUTO_DIFFICULTY_SCALING.get()
                ? calibrateParty(party) : PowerCalibration.BASE;
        double scale = 1.0D + Math.max(0, players - 1)
                * GameplayConfig.MIKAGE_PARTY_HEALTH_PER_PLAYER.get();
        scale *= calibration.healthScale;
        if (reminiscence) {
            scale *= GameplayConfig.MIKAGE_REMINISCENCE_HEALTH_MULTIPLIER.get();
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                GameplayConfig.MIKAGE_BASE_HEALTH.get() * scale);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(
                GameplayConfig.MIKAGE_BASE_DAMAGE.get()
                        * (reminiscence
                                ? GameplayConfig.MIKAGE_REMINISCENCE_DAMAGE_MULTIPLIER.get()
                                : 1.0D)
                        * (1.0D + Math.max(0, players - 1)
                                * GameplayConfig.MIKAGE_PARTY_DAMAGE_PER_PLAYER.get())
                        * calibration.damageScale);
        combat.skillSpeedMultiplier = calibration.skillSpeedScale;
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND,
                ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            state.setColorCode(0xD51F3F);
            state.setEffectColorInverse(false);
            state.setTargetEntityId(-1);
        });
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        getPersistentData().putLong("blade_tetra_challenge", challengeId);
        getPersistentData().putBoolean("blade_tetra_reminiscence", reminiscence);
        getPersistentData().putDouble("blade_tetra_power_health_scale",
                calibration.healthScale);
        getPersistentData().putDouble("blade_tetra_power_damage_scale",
                calibration.damageScale);
        getPersistentData().putDouble("blade_tetra_power_skill_scale",
                calibration.skillSpeedScale);
        bossBar.setName(Component.translatable(reminiscence
                ? "entity.blade_tetra.mikage.echo" : "entity.blade_tetra.mikage"));
    }

    public void configureVisitor(long challengeId) {
        entityData.set(VISITOR_GUIDE, true);
        getPersistentData().putBoolean("blade_tetra_visitor_guide", true);
        getPersistentData().putLong("blade_tetra_challenge", challengeId);
        setNoAi(true);
        setInvulnerable(true);
        setPersistenceRequired();
        setItemSlot(EquipmentSlot.MAINHAND,
                ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        bossBar.setVisible(false);
    }

    public boolean isVisitorGuide() {
        return entityData.get(VISITOR_GUIDE);
    }

    private PowerCalibration calibrateParty(Collection<ServerPlayer> party) {
        if (party.isEmpty()) {
            return PowerCalibration.BASE;
        }
        double attackSum = 0.0D;
        double strongestAttack = 0.0D;
        double defenseSum = 0.0D;
        for (ServerPlayer player : party) {
            double attack = safeScore(player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                    1.0D, GameplayConfig.MIKAGE_MAX_SCORED_WEAPON_DAMAGE.get());
            var strength = player.getEffect(MobEffects.DAMAGE_BOOST);
            if (strength != null && strength.getDuration() >= 0
                    && strength.getDuration() < 600) {
                attack = Math.max(1.0D, attack - 3.0D * (strength.getAmplifier() + 1));
            }
            attackSum += attack;
            strongestAttack = Math.max(strongestAttack, attack);

            double health = safeScore(player.getMaxHealth(), 20.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_PLAYER_HEALTH.get());
            double armor = safeScore(player.getArmorValue(), 0.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_ARMOR.get());
            double toughness = safeScore(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                    0.0D, GameplayConfig.MIKAGE_MAX_SCORED_ARMOR_TOUGHNESS.get());
            double absorption = safeScore(player.getAbsorptionAmount(), 0.0D,
                    GameplayConfig.MIKAGE_MAX_SCORED_PLAYER_HEALTH.get());
            double defense = 0.15D + 0.40D * health / 20.0D
                    + 0.30D * armor / 20.0D + 0.15D * toughness / 8.0D
                    + 0.10D * absorption / 20.0D;
            var resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null && (resistance.getDuration() < 0
                    || resistance.getDuration() >= 600)) {
                defense += Math.min(1.0D, 0.25D * (resistance.getAmplifier() + 1));
            }
            defenseSum += Math.max(1.0D, defense);
        }

        double averageAttack = attackSum / party.size();
        double teamAttack = Math.max(averageAttack, strongestAttack * 0.85D);
        double maxAttack = GameplayConfig.MIKAGE_MAX_SCORED_WEAPON_DAMAGE.get();
        double offenseProgress = Mth.clamp((teamAttack - 20.0D)
                / Math.max(1.0D, maxAttack - 20.0D), 0.0D, 1.0D);
        double defenseProgress = Mth.clamp((defenseSum / party.size() - 1.0D) / 3.0D,
                0.0D, 1.0D);
        return new PowerCalibration(
                1.0D + offenseProgress
                        * (GameplayConfig.MIKAGE_MAX_EQUIPMENT_HEALTH_SCALE.get() - 1.0D),
                1.0D + defenseProgress
                        * (GameplayConfig.MIKAGE_MAX_DEFENSE_DAMAGE_SCALE.get() - 1.0D),
                1.0D + offenseProgress
                        * (GameplayConfig.MIKAGE_MAX_SKILL_SPEED_SCALE.get() - 1.0D));
    }

    private static double safeScore(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return maximum;
        }
        return Mth.clamp(value, minimum, maximum);
    }

    boolean dealTrialDamage(ServerPlayer player, float rawDamage, boolean guarded) {
        return dealTrialDamage(player, rawDamage, guarded ? 0.25F : 1.0F,
                guarded, false);
    }

    boolean dealAdjustedTrialDamage(ServerPlayer player, float adjustedDamage,
            boolean protectedResponse) {
        return dealTrialDamage(player, adjustedDamage, 1.0F, protectedResponse, true);
    }

    private boolean dealTrialDamage(ServerPlayer player, float rawDamage,
            float mechanicMultiplier, boolean protectedResponse, boolean forceBoundarySource) {
        if (rawDamage <= 0.0F || !player.isAlive() || player.isCreative()
                || player.isSpectator() || !ChallengeManager.isParticipant(this, player)) {
            return false;
        }
        double baseAttack = Math.max(1.0D, getAttributeValue(Attributes.ATTACK_DAMAGE));
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
                ? level().damageSources().indirectMagic(this, this)
                : level().damageSources().mobAttack(this);
        float before = player.getHealth() + player.getAbsorptionAmount();
        int immunityBefore = player.invulnerableTime;
        boolean hurt = player.hurt(source, requested);
        // A fatal hit can synchronously eject the player to another dimension.
        if (player.level() != level() || !ChallengeManager.isParticipant(this, player)) {
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

    void prepareOpening(int ticks) {
        combat.techniqueCooldown = Math.max(combat.techniqueCooldown, ticks);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isVisitorGuide()) {
            return false;
        }
        if (Float.isNaN(amount) || amount <= 0.0F) {
            return false;
        }
        if (Float.isInfinite(amount)) {
            amount = GameplayConfig.MIKAGE_SINGLE_HIT_CAP.get().floatValue();
        }
        Entity attacker = resolveCombatAttacker(source);
        if (attacker instanceof ServerPlayer observed) encounter.observeAttack(observed);
        if (attacker instanceof ServerPlayer player
                && !ChallengeManager.isParticipant(this, player)) {
            return false;
        }
        if (combat.phaseProtectionTicks > 0) {
            return false;
        }
        if (attacker instanceof ServerPlayer player) {
            if (techniques.moonEchoTicks > 0 && ChallengeManager.isParticipant(this, player)) {
                solveMoonEcho(player);
                return false;
            }
            if (techniques.mirrorDuelTicks <= MIRROR_DUEL_DASH_START && techniques.mirrorDuelTicks >= 5
                    && player.getUUID().equals(techniques.mirrorDuelTarget)) {
                counterMirrorDuel(player);
                return false;
            }
        }
        long now = level().getGameTime();
        if (attacker != null
                && defense.hurtCooldownUntil.getOrDefault(attacker.getUUID(), Long.MIN_VALUE) > now) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof EntityJudgementCut cut && cut.getOwner() instanceof LivingEntity owner) {
            attacker = owner;
            amount *= judgementCutDamageMultiplier(owner);
            if (amount <= 0.0F) {
                return false;
            }
        } else if (attacker instanceof LivingEntity living) {
            amount *= handleSlashArtPressure(living);
        }
        if (attacker instanceof LivingEntity living && distanceToSqr(living) <= 25.0D
                && defense.swordWheelBreakTicks <= 0 && combat.signatureRecoveryTicks <= 0
                && techniques.interactionOpeningTicks <= 0) {
            registerClosePressure(living);
            if (isSwordWheelDeployed() && defense.swordWheelCounterCooldown <= 0) {
                defense.swordWheelCounterCooldown = 14;
                counterWithSwordWheel(living);
                amount *= 0.70F;
            }
        }
        float threshold = GameplayConfig.MIKAGE_SOFT_CAP_THRESHOLD.get().floatValue();
        if (amount > threshold) {
            amount = Math.min(GameplayConfig.MIKAGE_SINGLE_HIT_CAP.get().floatValue(),
                    threshold + (amount - threshold)
                            * GameplayConfig.MIKAGE_SOFT_CAP_OVERFLOW_RATIO.get().floatValue());
        }
        if (combat.signatureRecoveryTicks <= 0 && getPhase() == 1
                && tickCount % 70 < 16 && source.getEntity() != null) {
            amount *= 0.25F;
            if (level() instanceof ServerLevel server) {
                long effectNow = server.getGameTime();
                if (effectNow - defense.lastParryVfxTick >= 3L) {
                    defense.lastParryVfxTick = effectNow;
                    Vec3 direction = attacker == null ? getLookAngle()
                            : attacker.position().subtract(position());
                    direction = direction.multiply(1.0D, 0.0D, 1.0D);
                    if (direction.lengthSqr() < 0.001D) {
                        direction = getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                    }
                    Vec3 impact = position().add(direction.normalize().scale(0.72D))
                            .add(0.0D, 1.15D, 0.0D);
                    sendCombatVfx(server, BladeCombatVfxPacket.PARRY, impact,
                            getYRot(), 1.0F,
                            attacker instanceof ServerPlayer player ? player.getId() : -1);
                    playBladeParrySound(server, impact, SoundSource.HOSTILE, false);
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
            phaseFloor = getPhase() == 1 ? getMaxHealth() * 2.0F / 3.0F
                    : getPhase() == 2 ? getMaxHealth() / 3.0F : 0.0F;
            if (phaseFloor > 0.0F) {
                float remaining = Math.max(0.0F, getHealth() - phaseFloor);
                reachesPhaseGate = amount >= remaining;
                amount = Math.min(amount, remaining);
                if (amount <= 0.0F) {
                    return false;
                }
            }
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && reachesPhaseGate && isAlive()) {
            setHealth(phaseFloor);
        }
        if (hurt && attacker != null) {
            // Replace vanilla's one-global-timer immunity with Mikage's per-attacker
            // ledger so one participant never consumes another participant's hit.
            invulnerableTime = 0;
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
                registerPursuitPressure(player, now);
            }
            if (attacker instanceof ServerPlayer player) {
                registerShadowCrossIaido(player, now);
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

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            noActionTime = 0;
            encounter.tick(server);
        }
    }

    void teleportWithinArena(double x, double y, double z) {
        Vec3 clamped = ChallengeManager.clampMikagePosition(this, new Vec3(x, y, z));
        teleportTo(clamped.x, clamped.y, clamped.z);
    }

    void enforceArenaBoundary() {
        Vec3 clamped = ChallengeManager.clampMikagePosition(this, position());
        if (clamped.distanceToSqr(position()) < 0.0001D) return;
        teleportTo(clamped.x, clamped.y, clamped.z);
        navigation.stop();
        Vec3 center = ChallengeManager.arenaCenter(this);
        Vec3 outward = position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
        Vec3 motion = getDeltaMovement();
        if (outward.lengthSqr() > 0.001D) {
            Vec3 normal = outward.normalize();
            double outwardSpeed = motion.multiply(1.0D, 0.0D, 1.0D).dot(normal);
            if (outwardSpeed > 0.0D) {
                setDeltaMovement(motion.subtract(normal.scale(outwardSpeed)));
            }
        }
    }

    ServerPlayer nearestChallengeParticipant(ServerLevel server) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(this, player)) continue;
            double distance = distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    void phaseTransition(int phase) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        bossBar.setName(Component.translatable(phase == 1
                ? "entity.blade_tetra.mikage" : "entity.blade_tetra.mikage.phase" + phase));
        combat.phaseProtectionTicks = GameplayConfig.MIKAGE_PHASE_PROTECTION_TICKS.get();
        clearQueuedPlayerBladeAttacks(server);
        encounter.story().phase(phase);
        Vec3 center = position().add(0.0D, 1.0D, 0.0D);
        sendCombatVfx(server, BladeCombatVfxPacket.PHASE_SHIFT, center,
                getYRot(), phase == 3 ? 1.18F : 1.0F, phase);
        server.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 0.9F, phase == 3 ? 0.62F : 0.74F);
        server.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER,
                SoundSource.HOSTILE, 0.55F, phase == 3 ? 1.18F : 1.32F);
    }

    void sendCombatVfx(ServerLevel server, int type, Vec3 position,
            float yaw, float intensity, int focusEntityId) {
        BladeCombatVfxPacket packet = new BladeCombatVfxPacket(type,
                position.x, position.y, position.z, yaw, intensity, focusEntityId);
        for (ServerPlayer viewer : server.players()) {
            if (viewer.level() == server
                    && ChallengeManager.isParticipant(this, viewer)
                    && viewer.distanceToSqr(position) <= 128.0D * 128.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    void sendTechniqueVfx(ServerLevel server, BladeTechniqueVfxPacket packet,
            Vec3 center) {
        for (ServerPlayer viewer : server.players()) {
            if (viewer.level() == server
                    && ChallengeManager.isParticipant(this, viewer)
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

    void clearQueuedPlayerBladeAttacks(ServerLevel server) {
        AABB area = getBoundingBox().inflate(72.0D);
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

    private void registerClosePressure(LivingEntity attacker) {
        if (tickCount - combat.lastClosePressureTick > 32) {
            combat.closePressureHits = 0;
        }
        combat.lastClosePressureTick = tickCount;
        combat.closePressureHits++;
        int threshold = getPhase() >= 3 ? 2 : 3;
        if (combat.closePressureHits >= threshold && defense.swordWheelCooldown <= 0
                && getSwordWheelCount() > 0 && !isSwordWheelDeployed()
                && !isUsingSignatureTechnique()) {
            combat.closePressureHits = 0;
            deploySwordWheel(attacker);
        }
    }

    private void registerPursuitPressure(ServerPlayer attacker, long now) {
        pursuitRain.registerPressure(attacker, now);
    }

    ServerPlayer selectPursuitTarget(ServerLevel server) {
        return pursuitRain.selectTarget(server);
    }

    void beginPursuitRain(ServerPlayer target, ServerLevel server) {
        pursuitRain.begin(target, server);
    }

    void tickPursuitRain(ServerLevel server) {
        pursuitRain.tick(server);
    }

    void tickPursuitRainFinal(ServerLevel server) {
        pursuitRain.tickFinal(server);
    }

    private float handleSlashArtPressure(LivingEntity attacker) {
        long now = level().getGameTime();
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
        if (techniques.interactionOpeningTicks > 0 || defense.swordWheelBreakTicks > 0
                || ((arena.cagePerfectCountered || arena.toriiScissorCountered || techniques.pursuitRainCountered)
                && combat.signatureRecoveryTicks > 0)) {
            pattern.hits = 0;
            pattern.currentMultiplier = 1.0F;
            return 1.0F;
        }

        if (pattern.hits == 2 && level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 0.75F, 1.85F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(1.0F, 0.02F, 0.06F), 1.35F),
                    getX(), getY() + 1.25D, getZ(), 24,
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
        long now = level().getGameTime();
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
        if (techniques.interactionOpeningTicks > 0 || defense.swordWheelBreakTicks > 0
                || ((arena.cagePerfectCountered || arena.toriiScissorCountered || techniques.pursuitRainCountered)
                && combat.signatureRecoveryTicks > 0)) {
            pattern.casts = 0;
            pattern.blockedUntil = Long.MIN_VALUE;
            return;
        }

        pattern.casts = Math.min(3, pattern.casts + 1);
        if (pattern.casts == 2 && level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                    SoundSource.HOSTILE, 0.8F, 1.9F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.95F, 0.02F, 0.08F), 1.25F),
                    getX(), getY() + 1.25D, getZ(), 20,
                    0.5D, 0.7D, 0.5D, 0.03D);
        } else if (pattern.casts >= 3 && now >= pattern.blockedUntil) {
            pattern.blockedUntil = now
                    + GameplayConfig.MIKAGE_JUDGEMENT_LOCKOUT_TICKS.get();
            if (level() instanceof ServerLevel server) {
                defense.mirrorCounterCooldown = Math.max(defense.mirrorCounterCooldown, 75);
                clearJudgementCuts(server, attacker.getUUID());
                performMirrorCounter(attacker);
                server.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK,
                        SoundSource.HOSTILE, 1.1F, 0.65F);
                server.sendParticles(new DustParticleOptions(
                                new Vector3f(0.85F, 0.01F, 0.05F), 1.55F),
                        getX(), getY() + 1.2D, getZ(), 48,
                        1.0D, 1.0D, 1.0D, 0.08D);
            }
        }
    }

    private float judgementCutDamageMultiplier(LivingEntity attacker) {
        JudgementPattern pattern = defense.judgementPatterns.get(attacker.getUUID());
        if (pattern == null) {
            return 1.0F;
        }
        long now = level().getGameTime();
        if (now < pattern.blockedUntil) {
            return 0.0F;
        }
        return pattern.casts >= 2
                ? GameplayConfig.MIKAGE_SECOND_JUDGEMENT_DAMAGE_MULTIPLIER.get().floatValue()
                : 1.0F;
    }

    boolean isJudgementCutBlocked(LivingEntity attacker) {
        JudgementPattern pattern = defense.judgementPatterns.get(attacker.getUUID());
        return pattern != null && level().getGameTime() < pattern.blockedUntil;
    }

    void tickJudgementCutAdaptation(ServerLevel server) {
        long now = level().getGameTime();
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
                        getBoundingBox().inflate(72.0D), cut -> cut.getOwner() != null
                                && ownerId.equals(cut.getOwner().getUUID()))
                .forEach(Entity::discard);
    }

    private void performMirrorCounter(LivingEntity attacker) {
        if (!(level() instanceof ServerLevel server)) return;
        defense.lockBreakUntil.put(attacker.getUUID(), level().getGameTime() + 16L);
        clearPlayerLock(attacker);
        if (!isSwordWheelDeployed() && getSwordWheelCount() > 0) {
            deploySwordWheel(attacker);
        } else {
            flashStepAway(attacker, server, 8.5D);
        }
        setAction(MikageAction.IAIDO_DRAW, 15);
        Vec3 targetAtCast = attacker.position();
        attackTimeline.circle(12, targetAtCast, 3.2D, 3.2D,
                (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.78F, 0.72D);
        server.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.HOSTILE, 1.0F, 1.35F);
        Vec3 direction = targetAtCast.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() > 0.001D) {
            Vec3 end = targetAtCast.add(direction.normalize().scale(7.0D));
            attackTimeline.line(14, targetAtCast.subtract(direction.normalize().scale(4.0D)),
                    end, 1.25D, 3.0D,
                    (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.72F, 0.68D);
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
        if (tickCount % 100 == 0) {
            defense.saPatterns.entrySet().removeIf(entry -> now - entry.getValue().lastHit > 200L);
        }
    }

    void tickSwordWheel(ServerLevel server) {
        if (defense.swordWheelCooldown > 0) defense.swordWheelCooldown--;
        if (defense.swordWheelCounterCooldown > 0) defense.swordWheelCounterCooldown--;
        if (defense.swordWheelBreakTicks > 0) {
            defense.swordWheelBreakTicks--;
            navigation.stop();
            setDeltaMovement(Vec3.ZERO);
            if (defense.swordWheelBreakTicks == 0) {
                int restored = Math.min(3, 1 + getPhase());
                entityData.set(SWORD_WHEEL_COUNT, restored);
                defense.swordWheelCooldown = 90;
                setAction(MikageAction.IDLE, 1);
                flashStepAwayFromNearestPlayer(server);
            }
        }
        if (!isSwordWheelDeployed()) {
            return;
        }
        if (--defense.swordWheelDeployTicks <= 0 || getSwordWheelCount() <= 0
                || isUsingSignatureTechnique()) {
            recallSwordWheel(server, 45);
        }
    }

    private void deploySwordWheel(LivingEntity pressureTarget) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        clearSwordWheelEntities(server);
        int count = getSwordWheelCount();
        int solidSlot = random.nextInt(count);
        entityData.set(SWORD_WHEEL_DEPLOYED, true);
        defense.swordWheelDeployTicks = 105;
        defense.swordWheelCounterCooldown = 5;
        setAction(MikageAction.CAST_READY, 18);
        server.playSound(null, blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.HOSTILE, 1.0F, 1.45F);
        Vec3 wheelCenter = position().add(0.0D, 1.3D, 0.0D);
        sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.SWORD_WHEEL,
                wheelCenter.x, wheelCenter.y, wheelCenter.z,
                wheelCenter.x, wheelCenter.y, wheelCenter.z,
                getYRot(), 1.0F, getId(), pressureTarget == null ? -1 : pressureTarget.getId(),
                105, count), wheelCenter);
        for (int slot = 0; slot < count; slot++) {
            MikagePhantomSwordEntity sword = ModEntities.MIKAGE_PHANTOM_SWORD.get().create(server);
            if (sword == null) continue;
            double angle = slot * Math.PI * 2.0D / count;
            sword.setPos(getX() + Math.cos(angle) * 3.8D,
                    getY() + 1.3D, getZ() + Math.sin(angle) * 3.8D);
            sword.configure(this, slot, slot == solidSlot);
            server.addFreshEntity(sword);
            defense.swordWheelEntities.add(sword.getUUID());
        }
        if (pressureTarget != null) {
            flashStepAway(pressureTarget, server, 7.5D);
        }
    }

    void breakSwordWheelLayer(MikagePhantomSwordEntity broken, Entity attacker) {
        if (!(level() instanceof ServerLevel server) || !isSwordWheelDeployed()) {
            return;
        }
        int remaining = Math.max(0, getSwordWheelCount() - 1);
        Vec3 breakPoint = broken.position().add(0.0D, 0.6D, 0.0D);
        sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.SWORD_WHEEL_BREAK,
                breakPoint.x, breakPoint.y, breakPoint.z,
                getX(), getY() + 1.0D, getZ(),
                getYRot(), remaining == 0 ? 1.3F : 0.82F,
                broken.getId(), attacker == null ? -1 : attacker.getId(),
                remaining == 0 ? 24 : 14, remaining), breakPoint);
        entityData.set(SWORD_WHEEL_COUNT, remaining);
        clearSwordWheelEntities(server);
        entityData.set(SWORD_WHEEL_DEPLOYED, false);
        defense.swordWheelCooldown = 34;
        combat.closePressureHits = 0;
        if (remaining == 0) {
            defense.swordWheelBreakTicks = 120;
            combat.signatureRecoveryTicks = Math.max(combat.signatureRecoveryTicks, defense.swordWheelBreakTicks);
            setAction(MikageAction.STAGGERED, defense.swordWheelBreakTicks);
            server.playSound(null, blockPosition(), SoundEvents.TOTEM_USE,
                    SoundSource.HOSTILE, 0.75F, 0.62F);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    getX(), getY() + 1.0D, getZ(), 28,
                    1.1D, 0.8D, 1.1D, 0.0D);
        } else {
            combat.signatureRecoveryTicks = Math.max(combat.signatureRecoveryTicks, 16);
            setAction(MikageAction.STAGGERED, 16);
        }
    }

    private void counterWithSwordWheel(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 away = target.position().subtract(position());
        if (away.lengthSqr() > 0.001D) {
            target.push(away.normalize().x * 0.55D, 0.18D, away.normalize().z * 0.55D);
        }
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.32F;
        if (target instanceof ServerPlayer player) {
            dealTrialDamage(player, damage, false);
        } else {
            target.hurt(server.damageSources().indirectMagic(this, this), damage);
        }
        server.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.0F, 1.35F);
        server.sendParticles(new DustParticleOptions(
                        new Vector3f(0.85F, 0.02F, 0.10F), 1.0F),
                target.getX(), target.getY() + 1.0D, target.getZ(),
                16, 0.42D, 0.65D, 0.42D, 0.05D);
    }

    void recallSwordWheel(ServerLevel server, int cooldown) {
        clearSwordWheelEntities(server);
        entityData.set(SWORD_WHEEL_DEPLOYED, false);
        defense.swordWheelCooldown = Math.max(defense.swordWheelCooldown, cooldown);
    }

    void clearSwordWheelEntities(ServerLevel server) {
        for (UUID id : defense.swordWheelEntities) {
            Entity entity = server.getEntity(id);
            if (entity != null) entity.discard();
        }
        defense.swordWheelEntities.clear();
        server.getEntitiesOfClass(MikagePhantomSwordEntity.class,
                        getBoundingBox().inflate(16.0D), sword -> true)
                .stream().filter(sword -> sword.getPersistentData()
                        .getLong("blade_tetra_owner_id") == getId())
                .forEach(Entity::discard);
    }

    void advanceBoundaryFlashCharge(Technique technique, ServerLevel server) {
        if (getPhase() != 3 || technique == Technique.NONE
                || technique == Technique.BOUNDARY_FLASH || arena.boundaryFlashPending) return;
        arena.boundaryFlashCharge = Math.min(3, arena.boundaryFlashCharge + 1);
        Vec3 arenaCenter = ChallengeManager.arenaCenter(this);
        Vec3 focus = position().add(0.0D, 1.0D, 0.0D);
        sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.BOUNDARY_CHARGE,
                arenaCenter.x, arenaCenter.y + 0.08D, arenaCenter.z,
                focus.x, focus.y, focus.z,
                getYRot(), arena.boundaryFlashCharge, -1, getId(), 60,
                random.nextInt()), arenaCenter);
        server.playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 0.58F + arena.boundaryFlashCharge * 0.14F,
                0.72F + arena.boundaryFlashCharge * 0.16F);
        if (arena.boundaryFlashCharge >= 3) {
            arena.boundaryFlashPending = true;
            arena.boundaryFlashReadyTicks = 56;
        }
    }

    void sampleParticipantMovement(ServerLevel server) {
        long now = server.getGameTime();
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || !ChallengeManager.isParticipant(this, player)) continue;
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

    private void registerShadowCrossIaido(ServerPlayer player, long now) {
        if (!isIaidoAttack(player) || techniques.interactionOpeningTicks > 0 || encounter.isWindingUp()
                || isUsingSignatureTechnique()) return;
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
                || distanceToSegment(position(), start, end) > 1.65D) return;
        Vec3 before = start.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 after = end.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
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
        if (!(level() instanceof ServerLevel server)) return;
        if (isSwordWheelDeployed()) recallSwordWheel(server, 60);
        defense.zanshinCounterTicks = GameplayConfig.MIKAGE_ZANSHIN_WARNING_TICKS.get();
        defense.zanshinCounterTarget = player.getUUID();
        defense.zanshinCounterCenter = ChallengeManager.clampMikagePosition(this,
                player.position().add(route.scale(1.4D)));
        navigation.stop();
        setAction(MikageAction.IAIDO_READY, defense.zanshinCounterTicks);
        if (!pattern.warned) {
            pattern.warned = true;
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.mikage.shadow_cross_read"), true);
        }
        server.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.HOSTILE, 0.85F, 1.9F);
    }

    void tickZanshinCounter(ServerLevel server) {
        navigation.stop();
        Entity found = defense.zanshinCounterTarget == null ? null : server.getEntity(defense.zanshinCounterTarget);
        ServerPlayer target = found instanceof ServerPlayer player ? player : null;
        if (target != null && target.isAlive()) lookAt(target, 180.0F, 180.0F);
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
            float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE)
                    * GameplayConfig.MIKAGE_ZANSHIN_DAMAGE_MULTIPLIER.get().floatValue();
            dealTrialDamage(target, damage, isBladeGuarding(target));
        }
        AttackManager.doSlash(this, 180.0F, true, false, 0.0D);
        server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                defense.zanshinCounterCenter.x, defense.zanshinCounterCenter.y + 0.9D,
                defense.zanshinCounterCenter.z, 16, 1.8D, 0.5D, 1.8D, 0.0D);
        server.playSound(null, BlockPos.containing(defense.zanshinCounterCenter),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.25F, 1.7F);
        defense.zanshinCounterTarget = null;
        combat.techniqueCooldown = Math.max(combat.techniqueCooldown, 18);
        setAction(MikageAction.IAIDO_DRAW, 10);
    }

    boolean hasShadowCrossPressure(LivingEntity target) {
        ShadowCrossPattern pattern = defense.shadowCrossPatterns.get(target.getUUID());
        return pattern != null && pattern.crossings >= 2
                && level().getGameTime() - pattern.lastCross
                <= GameplayConfig.MIKAGE_SHADOW_CROSS_WINDOW_TICKS.get();
    }

    void tickMirrorDuel(ServerLevel server) {
        legacyEffects.tickMirrorDuel(server);
    }

    private void counterMirrorDuel(ServerPlayer player) {
        legacyEffects.counterMirrorDuel(player);
    }

    void tickBoundarySeal(ServerLevel server) {
        legacyEffects.tickBoundarySeal(server);
    }

    void breakBoundarySeal(MikagePhantomSwordEntity seal, Entity attacker) {
        legacyEffects.breakBoundarySeal(seal, attacker);
    }

    void tickMoonEcho(ServerLevel server) {
        legacyEffects.tickMoonEcho(server);
    }

    void strikeMoonEcho(MikageEchoEntity echo, Entity attacker) {
        legacyEffects.strikeMoonEcho(echo, attacker);
    }

    private void solveMoonEcho(ServerPlayer player) {
        legacyEffects.solveMoonEcho(player);
    }

    private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        return MikageLegacySkillEffects.distanceToSegment(point, start, end);
    }

    public boolean isMoonEchoActive() {
        return legacyEffects.isMoonEchoActive();
    }

    private void flashStepAway(LivingEntity target, ServerLevel server, double distance) {
        legacyEffects.flashStepAway(target, server, distance);
    }

    void tickAerialTechnique() {
        legacyEffects.tickAerialTechnique();
    }

    void beginBoundaryFlash(LivingEntity target, ServerLevel server) {
        legacyEffects.beginBoundaryFlash(target, server);
    }

    void tickBoundaryFlash(ServerLevel server) {
        legacyEffects.tickBoundaryFlash(server);
    }

    boolean isUsingTechnique() {
        return encounter.active() || legacySkillBusy();
    }

    private boolean isUsingSignatureTechnique() {
        return techniques.isSignatureActive(arena, defense);
    }

    void tickBoundaryWalls(ServerLevel server) {
        legacyEffects.tickBoundaryWalls(server);
    }

    void tickToriiSweep(ServerLevel server) {
        legacyEffects.tickToriiSweep(server);
    }

    void tickToriiCages(ServerLevel server) {
        legacyEffects.tickToriiCages(server);
    }

    boolean isBladeGuarding(ServerPlayer player) {
        return legacyEffects.isBladeGuarding(player);
    }

    void flashStepAwayFromNearestPlayer(ServerLevel server) {
        legacyEffects.flashStepAwayFromNearestPlayer(server);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isVisitorGuide()) {
            bossBar.addPlayer(player);
            if (level() instanceof ServerLevel
                    && ChallengeManager.isParticipant(this, player)) {
                for (BoundaryWallState wall : arena.boundaryWalls) {
                    Vec3 edge = wall.center.add(
                            wall.direction.scale(BOUNDARY_FLASH_LENGTH));
                    ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new BladeTechniqueVfxPacket(
                                    BladeTechniqueVfxPacket.BOUNDARY_WALL,
                                    wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                    edge.x, wall.center.y + 0.05D, edge.z,
                                    getYRot(), 1.0F, -1, -1,
                                    BOUNDARY_WALL_VISUAL_TICKS, wall.id));
                    if (wall.gapTicks > 0) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new BladeTechniqueVfxPacket(
                                        BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP,
                                        wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                        edge.x, wall.center.y + 0.05D, edge.z,
                                        getYRot(), (float) wall.gapAlong, -1, -1,
                                        wall.gapTicks, wall.id));
                    } else if (wall.gapWarningTicks > 0) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new BladeTechniqueVfxPacket(
                                        BladeTechniqueVfxPacket.BOUNDARY_WALL_GAP_WARNING,
                                        wall.center.x, wall.center.y + 0.05D, wall.center.z,
                                        edge.x, wall.center.y + 0.05D, edge.z,
                                        getYRot(), (float) wall.pendingGapAlong, -1, -1,
                                        wall.gapWarningTicks, wall.id));
                    }
                }
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVisitorGuide()) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ChallengeManager.openVisitorDialogue(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public void die(DamageSource source) {
        encounter.beforeDeath();
        super.die(source);
        encounter.defeated();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier,
            DamageSource source) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && isAlive() && !isVisitorGuide()) {
            LOGGER.warn("Mikage removed while alive: reason={}, challenge={}, phase={}, "
                            + "health={}/{}, position={}",
                    reason,
                    getPersistentData().getLong("blade_tetra_challenge"),
                    getPhase(), getHealth(), getMaxHealth(), position());
        }
        encounter.cancel();
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public enum MikageAction {
        IDLE,
        IAIDO_READY,
        IAIDO_DRAW,
        COMBO_READY,
        COMBO_SLASH,
        HEAVY_READY,
        HEAVY_CLEAVE,
        CAST_READY,
        CAST_SLASH,
        AERIAL_CAST,
        RITUAL,
        STAGGERED
    }

    enum SwordDiscipline {
        STANDARD,
        IAIDO,
        RENGEKI,
        DANGAKU
    }

    enum Technique {
        NONE(SwordDiscipline.STANDARD),
        CIRCLE_SLASH(SwordDiscipline.DANGAKU),
        BLADE_COMBO(SwordDiscipline.RENGEKI),
        STEP_IAIDO(SwordDiscipline.IAIDO),
        DANGAKU_CLEAVE(SwordDiscipline.DANGAKU),
        FLASH_COUNTER(SwordDiscipline.IAIDO),
        SAKURA_END(SwordDiscipline.IAIDO),
        DRIVE_FAN(SwordDiscipline.STANDARD),
        WAVE_EDGE(SwordDiscipline.STANDARD),
        JUDGEMENT_CUT(SwordDiscipline.STANDARD),
        SUPER_JUDGEMENT(SwordDiscipline.STANDARD),
        SUMMONED_VOLLEY(SwordDiscipline.RENGEKI),
        AERIAL_RAIN(SwordDiscipline.RENGEKI),
        BOUNDARY_FLASH(SwordDiscipline.IAIDO),
        MIRROR_DUEL(SwordDiscipline.IAIDO),
        BOUNDARY_SEAL(SwordDiscipline.DANGAKU),
        MOON_ECHO(SwordDiscipline.IAIDO),
        TORII_SWEEP(SwordDiscipline.DANGAKU),
        TORII_CAGE(SwordDiscipline.DANGAKU);

        final SwordDiscipline style;

        Technique(SwordDiscipline style) {
            this.style = style;
        }
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

    private record PowerCalibration(double healthScale, double damageScale,
            double skillSpeedScale) {
        static final PowerCalibration BASE = new PowerCalibration(1.0D, 1.0D, 1.0D);
    }
}
