package dev.bladetetra.challenge;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** A targetable, short-lived body double used by Moonshadow. */
public final class MikageEchoEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            MikageEchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private int lifeTicks = 100;

    public MikageEchoEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    public void configure(MikageEntity owner) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        setYRot(owner.getYRot());
        setYHeadRot(owner.getYHeadRot());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide()) {
            MikageEntity owner = owner();
            if (owner == null || !owner.isAlive() || --lifeTicks <= 0) {
                discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return true;
        MikageEntity owner = owner();
        if (owner != null) {
            owner.strikeMoonEcho(this, MikageEntity.resolveCombatAttacker(source));
        } else {
            discard();
        }
        return true;
    }

    private MikageEntity owner() {
        if (!(level() instanceof ServerLevel server)) return null;
        return entityData.get(OWNER).map(server::getEntity)
                .filter(MikageEntity.class::isInstance)
                .map(MikageEntity.class::cast).orElse(null);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
