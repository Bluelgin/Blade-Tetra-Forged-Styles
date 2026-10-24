package dev.bladetetra.aura;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import mods.flammpfeil.slashblade.entity.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Reuses native Drive physics and renderer, with a separate bounded aura hit cadence.
 * Secondary visual petals never deal damage.
 */
public final class SwordAuraDriveEntity extends EntityDrive {
    private static final String DECORATION = "blade_tetra_lifeng_decoration";
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
    public void tick() {
        if (launchPosition == null) launchPosition = position();
        super.tick();
        if (!level().isClientSide() && launchPosition.distanceToSqr(position()) >= SwordAuraFlight.RANGE_SQUARED) {
            discard();
        }
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
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setDecoration(tag.getBoolean(DECORATION));
        SoulLegacyDamageGuard.markSecondary(this);
    }
}
