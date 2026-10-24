package dev.bladetetra.aura;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.combat.BladeStyle;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Reuses native Drive physics with a separate bounded aura hit cadence.
 * Appearance is synced once on the main projectile; legacy decorative entities remain harmless.
 */
public final class SwordAuraDriveEntity extends EntityDrive {
    private static final String DECORATION = "blade_tetra_lifeng_decoration";
    private static final String VISUAL_STYLE = "blade_tetra_lifeng_visual_style";
    private static final EntityDataAccessor<Byte> VISUAL = SynchedEntityData.defineId(
            SwordAuraDriveEntity.class, EntityDataSerializers.BYTE);
    private static final SwordAuraHitCadence<Entity> HIT_CADENCE = new SwordAuraHitCadence<>();
    private Vec3 launchPosition;
    private final Set<Integer> hitTargets = new HashSet<>();

    public SwordAuraDriveEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        SoulLegacyDamageGuard.markSecondary(this);
    }

    public void setDecoration(boolean value) {
        getPersistentData().putBoolean(DECORATION, value);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VISUAL, (byte) 0);
    }

    /** Launch-time appearance only; never infer it from the owner's current weapon. */
    public void setVisualStyle(BladeStyle style) {
        entityData.set(VISUAL, (byte) switch (style) {
            case RENGEKI -> 1;
            case DANGAKU -> 2;
            default -> 0;
        });
    }

    public BladeStyle getVisualStyle() {
        return switch (entityData.get(VISUAL)) {
            case 1 -> BladeStyle.RENGEKI;
            case 2 -> BladeStyle.DANGAKU;
            default -> BladeStyle.STANDARD;
        };
    }

    @Override
    public void tick() {
        if (launchPosition == null) launchPosition = position();
        super.tick();
        if (!level().isClientSide() && launchPosition.distanceToSqr(position()) >= SwordAuraFlight.RANGE_SQUARED) {
            discard();
        }
    }

    @Override
    public @Nullable EntityHitResult getRayTrace(Vec3 start, Vec3 end) {
        // Client entity contacts were already ignored by onHitEntity. Avoid the native
        // per-tick entity search there; keep block prediction and server tracing intact.
        return level().isClientSide() ? null : super.getRayTrace(start, end);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide() || getPersistentData().getBoolean(DECORATION)) return;
        Entity directTarget = hit.getEntity();
        Entity target = directTarget instanceof PartEntity<?> part ? part.getParent() : directTarget;
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity attacker) || target == owner || !target.isAlive()
                || hitTargets.contains(target.getId())) return;
        // Do not zero invulnerableTime or bypass real invulnerability. Only aura hurt-cooldown
        // handling is replaced, at most once per victim every four ticks across all projectiles.
        float amount = (float) getDamage();
        if (amount > 0.0F && HIT_CADENCE.tryHit(target, level().getGameTime(), () -> {
            float health = target instanceof LivingEntity victim ? victim.getHealth() : 0;
            float absorption = target instanceof LivingEntity victim ? victim.getAbsorptionAmount() : 0;
            boolean accepted = SwordAuraImpact.hurt(target, new SwordAuraDamageSource(this, attacker), amount);
            return accepted && (!(target instanceof LivingEntity victim) || victim.getHealth() < health
                    || victim.getAbsorptionAmount() < absorption || !victim.isAlive());
        })) {
            hitTargets.add(target.getId());
            attacker.setLastHurtMob(target);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(DECORATION, getPersistentData().getBoolean(DECORATION));
        tag.putByte(VISUAL_STYLE, entityData.get(VISUAL));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDecoration(tag.getBoolean(DECORATION));
        byte visual = tag.getByte(VISUAL_STYLE);
        entityData.set(VISUAL, visual >= 0 && visual <= 2 ? visual : (byte) 0);
        SoulLegacyDamageGuard.markSecondary(this);
    }
}
