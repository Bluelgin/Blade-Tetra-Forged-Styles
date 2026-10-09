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
    private boolean returning;
    public MikageGateSwordEntity(EntityType<? extends MikageGateSwordEntity> type, Level level) { super(type, level); }
    void configure(MikageEntity boss, dev.bladetetra.challenge.mikage.CastScope scope) {
        caster = boss.getUUID(); this.cast = scope.id();
        setOwner(boss); setNoGravity(true); setColor(0xD51F3F);
        setDamage(boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * .22);
        var release = boss.encounter().barrage();
        MikageNativeCombat.track(this, boss, scope, release.nativeRule());
    }
    void configureAttack(MikageEntity boss, dev.bladetetra.challenge.mikage.CastScope scope, boolean returning) {
        caster = boss.getUUID(); cast = scope.id(); this.returning = returning;
        setOwner(boss); setNoGravity(true); setColor(returning ? 0xFFDF90 : 0xD51F3F);
    }
    private MikageEntity boss() {
        return level() instanceof ServerLevel server && caster != null && server.getEntity(caster) instanceof MikageEntity b ? b : null;
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
        return actor instanceof ServerPlayer player && boss() != null && boss().encounter().eligible(player)
                && (release != null || MikageNativeCombat.owns(this));
    }
    /** Called only AFTER ArrowReflector.doReflect has performed its original velocity update. */
    public void nativeReflected(Entity actor) {
        if (!level().isClientSide() && mayReflect(actor) && !entityData.get(REFLECTED)) {
            entityData.set(REFLECTED, true); setColor(0xFFE0A0);
        }
    }
    @Override public void tick() {
        if (!level().isClientSide() && (!MikageNativeCombat.owns(this) || tickCount >= GateBarrageSequence.SWORD_LIFETIME)) {
            discard(); return;
        }
        super.tick();
    }
    @Override protected EntityHitResult getRayTrace(Vec3 from, Vec3 to) {
        MikageEntity boss = boss();
        if (boss == null || !MikageNativeCombat.owns(this)) return null;
        return ProjectileUtil.getEntityHitResult(level(), this, from, to,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), entity ->
                entityData.get(REFLECTED) ? returning && entity == boss
                : MikageNativeCombat.mayCollide(this, entity));
    }
    @Override protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide()) return;
        MikageEntity boss = boss();
        if (boss == null) { discard(); return; }
        if (entityData.get(REFLECTED)) {
            if (returning && hit.getEntity() == boss) boss.duel().rewardOpening(35);
        } else if (hit.getEntity() instanceof ServerPlayer player && MikageNativeCombat.swordContact(this, player)) {
            MikageBladeAttackTrace.enter(this);
            try { super.onHitEntity(hit); }
            finally { MikageBladeAttackTrace.exit(); mods.flammpfeil.slashblade.ability.StunManager.removeStun(player); }
        }
        discard();
    }
    @Override public java.util.List<net.minecraft.world.effect.MobEffectInstance> getPotionEffects() { return java.util.List.of(); }
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
