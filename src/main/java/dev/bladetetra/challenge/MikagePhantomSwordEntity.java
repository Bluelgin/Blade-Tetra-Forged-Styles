package dev.bladetetra.challenge;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/** A short-lived lock-on decoy. Only one sword in each formation has a solid core. */
public final class MikagePhantomSwordEntity extends Monster {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            MikagePhantomSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SOLID = SynchedEntityData.defineId(
            MikagePhantomSwordEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(
            MikagePhantomSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(
            MikagePhantomSwordEntity.class, EntityDataSerializers.INT);
    private int lifeTicks = 110;

    public MikagePhantomSwordEntity(EntityType<? extends Monster> type, Level level) {
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

    public void configure(MikageEntity owner, int slot, boolean solid) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(SLOT, slot);
        entityData.set(SOLID, solid);
        entityData.set(MODE, 0);
    }

    public void configureSeal(MikageEntity owner, int slot) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(SLOT, slot);
        entityData.set(SOLID, true);
        entityData.set(MODE, 1);
        lifeTicks = 180;
    }

    public boolean isSolidSword() {
        return entityData.get(SOLID);
    }

    public int getSlot() {
        return entityData.get(SLOT);
    }

    public boolean isBoundarySeal() {
        return entityData.get(MODE) == 1;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(SOLID, false);
        entityData.define(SLOT, 0);
        entityData.define(MODE, 0);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            if (isSolidSword() && tickCount % 2 == 0) {
                level().addParticle(new DustParticleOptions(
                                new Vector3f(1.0F, 0.015F, 0.06F), 1.15F),
                        getX(), getY() + 0.78D, getZ(), 0.0D, 0.012D, 0.0D);
            }
            return;
        }
        MikageEntity owner = owner();
        if (owner == null || !owner.isAlive() || --lifeTicks <= 0) {
            discard();
            return;
        }
        if (isBoundarySeal()) {
            setDeltaMovement(Vec3.ZERO);
            setYRot(tickCount * 2.4F);
            return;
        }
        int count = Math.max(1, owner.getSwordWheelCount());
        double angle = owner.tickCount * 0.065D + getSlot() * (Math.PI * 2.0D / count);
        double radius = 3.8D + 0.35D * Math.sin(owner.tickCount * 0.11D + getSlot());
        setPos(owner.getX() + Math.cos(angle) * radius,
                owner.getY() + 1.25D + Math.sin(angle * 2.0D) * 0.55D,
                owner.getZ() + Math.sin(angle) * radius);
        setYRot((float) Math.toDegrees(-angle) + 90.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) {
            return true;
        }
        MikageEntity owner = owner();
        if (owner == null) {
            discard();
            return false;
        }
        ServerLevel server = (ServerLevel) level();
        if (isBoundarySeal()) {
            owner.breakBoundarySeal(this, MikageEntity.resolveCombatAttacker(source));
            server.playSound(null, blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.HOSTILE, 1.25F, 0.82F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(1.0F, 0.04F, 0.08F), 1.35F),
                    getX(), getY() + 0.7D, getZ(), 42,
                    0.55D, 0.75D, 0.55D, 0.08D);
        } else if (isSolidSword()) {
            owner.breakSwordWheelLayer(this, source.getEntity());
            server.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK,
                    SoundSource.HOSTILE, 1.2F, 0.72F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.95F, 0.03F, 0.12F), 1.25F),
                    getX(), getY() + 0.7D, getZ(), 34,
                    0.38D, 0.65D, 0.38D, 0.06D);
        } else {
            server.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.HOSTILE, 0.55F, 1.75F);
            server.sendParticles(new DustParticleOptions(
                            new Vector3f(0.55F, 0.05F, 0.13F), 0.75F),
                    getX(), getY() + 0.7D, getZ(), 10,
                    0.22D, 0.42D, 0.22D, 0.03D);
        }
        return true;
    }

    private MikageEntity owner() {
        if (!(level() instanceof ServerLevel server)) {
            return null;
        }
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
