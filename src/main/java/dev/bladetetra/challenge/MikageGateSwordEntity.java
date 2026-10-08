package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.GateBarrageSequence;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Original native flight/model/reflection; encounter authorization replaces forceHit/stun on contact. */
public final class MikageGateSwordEntity extends EntityAbstractSummonedSword {
    private static final EntityDataAccessor<Boolean> REFLECTED = SynchedEntityData.defineId(
            MikageGateSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID caster;
    private long cast;
    public MikageGateSwordEntity(EntityType<? extends MikageGateSwordEntity> type, Level level) { super(type, level); }
    void configure(MikageEntity boss, long cast) {
        caster = boss.getUUID(); this.cast = cast;
        // Native PVP selection must not govern a boss attack. Authorization lives in getRayTrace below.
        // Ownerless native projectiles still use ArrowReflector unchanged; caster/cast are separate.
        setOwner(null); setNoGravity(true); setColor(0xD51F3F); setDamage(.22);
    }
    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(REFLECTED, false); }
    private MikageGateBarrageExecution release() {
        if (!(level() instanceof ServerLevel server) || caster == null
                || !(server.getEntity(caster) instanceof MikageEntity boss)) return null;
        var release = boss.encounter().barrage();
        return release != null && release.owns(this, cast) ? release : null;
    }
    public boolean mayReflect(Entity actor) {
        if (level().isClientSide()) return actor instanceof net.minecraft.world.entity.player.Player;
        var release = release();
        return release != null && actor instanceof ServerPlayer player && release.eligible(player);
    }
    /** Called only AFTER ArrowReflector.doReflect has performed its original velocity update. */
    public void nativeReflected(Entity actor) {
        if (!level().isClientSide() && mayReflect(actor) && !entityData.get(REFLECTED)) {
            entityData.set(REFLECTED, true); setColor(0xFFE0A0);
        }
    }
    @Override public void tick() {
        if (!level().isClientSide() && (release() == null || tickCount >= GateBarrageSequence.SWORD_LIFETIME)) {
            discard(); return;
        }
        super.tick();
    }
    @Override protected EntityHitResult getRayTrace(Vec3 from, Vec3 to) {
        var release = release();
        if (release == null || entityData.get(REFLECTED)) return null;
        return ProjectileUtil.getEntityHitResult(level(), this, from, to,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1),
                entity -> entity instanceof ServerPlayer player && release.eligible(player));
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        var release = release();
        if (release != null && !entityData.get(REFLECTED) && hit.getEntity() instanceof ServerPlayer player)
            release.hitPlayer(player);
        if (!level().isClientSide()) discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) { if (!level().isClientSide()) discard(); }
    /** Native burst adds area potion effects; trial swords always disappear without those effects. */
    @Override public void burst() { discard(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (caster != null) tag.putUUID("GateCaster", caster); tag.putLong("GateCast", cast);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); caster = tag.hasUUID("GateCaster") ? tag.getUUID("GateCaster") : null;
        cast = tag.getLong("GateCast");
        // No active release is restored from a save; ownership check discards this on its first tick.
    }
}
