package dev.bladetetra.challenge;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;

/** The attackable objective owns synced gate geometry; its native cut is an unticked render proxy only. */
public final class MikageGateCoreEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> CASTER = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> CAST = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> HITS = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> BREAK_AT = SynchedEntityData.defineId(MikageGateCoreEntity.class, EntityDataSerializers.LONG);
    private Vec3 anchor;
    private EntityJudgementCut visual;
    public MikageGateCoreEntity(EntityType<? extends MikageGateCoreEntity> type, Level level) {
        super(type, level); setNoAi(true); setNoGravity(true); noPhysics = true; xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 3).add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 0);
    }
    @Override protected void registerGoals() { }
    @Override protected void defineSynchedData() {
        super.defineSynchedData(); entityData.define(CASTER, Optional.empty()); entityData.define(CAST, 0L);
        entityData.define(START, 0L); entityData.define(HITS, 3); entityData.define(STATE, 0); entityData.define(BREAK_AT, 0L);
    }
    void configure(MikageEntity owner, long cast, Vec3 at, float yaw) {
        entityData.set(CASTER, Optional.of(owner.getUUID())); entityData.set(CAST, cast);
        entityData.set(START, level().getGameTime()); setPos(at); anchor = at; setYRot(yaw);
        setPersistenceRequired(); setHealth(3);
    }
    public int remainingHits() { return entityData.get(HITS); }
    public int gateState() { return entityData.get(STATE); }
    public long gateStart() { return entityData.get(START); }
    public long breakAt() { return entityData.get(BREAK_AT); }
    void sync(int hits, int state) {
        entityData.set(HITS, hits);
        if (state == 2 && gateState() != 2) entityData.set(BREAK_AT, level().getGameTime());
        entityData.set(STATE, state); setHealth(Math.max(1, hits));
    }
    MikageGateBarrageExecution release() {
        if (!(level() instanceof ServerLevel server)) return null;
        var id = entityData.get(CASTER);
        if (id.isEmpty() || !(server.getEntity(id.get()) instanceof MikageEntity owner)) return null;
        var release = owner.encounter().barrage();
        return release != null && release.owns(this, entityData.get(CAST)) ? release : null;
    }
    @Override public void tick() {
        if (!level().isClientSide() && release() == null) { discard(); return; }
        setDeltaMovement(Vec3.ZERO); super.tick(); setDeltaMovement(Vec3.ZERO);
        if (anchor != null) setPos(anchor);
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide() || !Float.isFinite(amount) || amount <= 0) return false;
        var release = release(); return release != null && release.hitCore(source);
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isAttackable() { return gateState() == 1; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    /** Never added to a level or ticked. Reuses the original JudgementCutRenderer without any cut damage. */
    public EntityJudgementCut nativeVisual() {
        if (!level().isClientSide()) throw new IllegalStateException("Gate visual requested on server");
        if (visual == null) {
            visual = new EntityJudgementCut(SlashBlade.RegistryEvents.JudgementCut, level());
            visual.setLifetime(1000); visual.setDamage(0);
        }
        visual.tickCount = (int) Math.floorMod(level().getGameTime() - gateStart(), 420); visual.setYRot(getYRot());
        visual.setColor(remainingHits() == 3 ? 0xA329D9 : remainingHits() == 2 ? 0xE53D71 : 0xFFD090);
        return visual;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); entityData.get(CASTER).ifPresent(id -> tag.putUUID("GateCaster", id));
        tag.putLong("GateCast", entityData.get(CAST));
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CASTER, tag.hasUUID("GateCaster") ? Optional.of(tag.getUUID("GateCaster")) : Optional.empty());
        entityData.set(CAST, tag.getLong("GateCast")); anchor = position();
    }
}
