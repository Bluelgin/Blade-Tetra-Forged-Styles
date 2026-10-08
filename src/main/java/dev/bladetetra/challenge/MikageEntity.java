package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageLegacyTiming.BOUNDARY_FLASH_TOTAL_TICKS;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.UUID;

public final class MikageEntity extends Monster {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_START =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_LENGTH =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SWORD_WHEEL_COUNT =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SWORD_WHEEL_DEPLOYED =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> VISITOR_GUIDE =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WITHIN_THOUSAND_GATES =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RIDING_PHANTOM_SWORD =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> NATIVE_COMBO_STAGE =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> NATIVE_COMBO_START =
            SynchedEntityData.defineId(MikageEntity.class, EntityDataSerializers.LONG);
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
    private final MikageLegacyCounterRuntime counters = new MikageLegacyCounterRuntime(this);
    private final MikageSwordWheelRuntime swordWheel = new MikageSwordWheelRuntime(this);
    private final MikageDamageService damageService = new MikageDamageService(this);
    private final MikageArenaMovement movement = new MikageArenaMovement(this);
    private final MikageEncounterPresentation presentation = new MikageEncounterPresentation(this);
    private final MikageDuelDefense duel = new MikageDuelDefense(this);
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

    MikageDuelDefense duel() { return duel; }
    MikageEncounter encounter() { return encounter; }
    MikageLegacySkillEffects legacyEffects() { return legacyEffects; }
    MikageLegacyCounterRuntime counters() { return counters; }
    MikageSwordWheelRuntime swordWheel() { return swordWheel; }
    MikageArenaMovement movement() { return movement; }
    MikageEncounterPresentation presentation() { return presentation; }
    MikagePursuitRainController pursuitRain() { return pursuitRain; }
    void setSwordWheelCount(int count) { entityData.set(SWORD_WHEEL_COUNT, count); }
    void setSwordWheelDeployed(boolean deployed) { entityData.set(SWORD_WHEEL_DEPLOYED, deployed); }
    void markVisitorGuide() { entityData.set(VISITOR_GUIDE, true); }
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
                && !duel.staggered()
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
        entityData.define(WITHIN_THOUSAND_GATES, false);
        entityData.define(RIDING_PHANTOM_SWORD, false);
        entityData.define(NATIVE_COMBO_STAGE, 0);
        entityData.define(NATIVE_COMBO_START, 0L);
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
        MikageEncounterSetup.configureForParty(this, party, reminiscence, challengeId);
    }

    public void configureVisitor(long challengeId) {
        MikageEncounterSetup.configureVisitor(this, challengeId);
    }

    public boolean isVisitorGuide() {
        return entityData.get(VISITOR_GUIDE);
    }

    public boolean isRidingPhantomSword() { return entityData.get(RIDING_PHANTOM_SWORD); }
    void setRidingPhantomSword(boolean riding) { entityData.set(RIDING_PHANTOM_SWORD, riding); }
    public int getNativeComboStage() { return entityData.get(NATIVE_COMBO_STAGE); }
    public long getNativeComboStart() { return entityData.get(NATIVE_COMBO_START); }
    void setNativeCombo(int stage, long start) {
        entityData.set(NATIVE_COMBO_STAGE, stage); entityData.set(NATIVE_COMBO_START, start);
    }
    public boolean isWithinThousandGates() { return entityData.get(WITHIN_THOUSAND_GATES); }
    void setWithinThousandGates(boolean hidden) { entityData.set(WITHIN_THOUSAND_GATES, hidden); }

    @Override public boolean displayFireAnimation() {
        return !isWithinThousandGates() && super.displayFireAnimation();
    }

    boolean dealTrialDamage(ServerPlayer player, float rawDamage, boolean guarded) {
        return damageService.dealTrialDamage(player, rawDamage, guarded);
    }

    boolean dealAdjustedTrialDamage(ServerPlayer player, float adjustedDamage,
            boolean protectedResponse) {
        return damageService.dealAdjustedTrialDamage(player, adjustedDamage, protectedResponse);
    }

    void prepareOpening(int ticks) {
        combat.techniqueCooldown = Math.max(combat.techniqueCooldown, ticks);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return damageService.hurt(source, amount, super::hurt);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            noActionTime = 0;
            encounter.tick(server);
        }
    }

    public boolean isMoonEchoActive() {
        return legacyEffects.isMoonEchoActive();
    }

    boolean isUsingTechnique() {
        return encounter.active() || legacySkillBusy() || duel.staggered();
    }

    private boolean isUsingSignatureTechnique() {
        return techniques.isSignatureActive(arena, defense);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        presentation.startSeenByPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        presentation.stopSeenByPlayer(player);
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
        STAGGERED,
        GUARD
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
}
