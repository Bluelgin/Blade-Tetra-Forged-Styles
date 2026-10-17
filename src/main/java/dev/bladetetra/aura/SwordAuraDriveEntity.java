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
 * Reuses native Drive physics and renderer, not Drive's iframe-clearing hit code.
 * Secondary visual petals never deal damage.
 */
public final class SwordAuraDriveEntity extends EntityDrive {
    private static final String DECORATION = "blade_tetra_lifeng_decoration";
    private static final double MAX_RANGE_SQUARED = 8.0D * 8.0D;
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
        if (!level().isClientSide() && launchPosition.distanceToSqr(position()) >= MAX_RANGE_SQUARED) {
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
                || !hitTargets.add(target.getId())) return;
        // No bypass, no invulnerability reset, no direct health mutation. The
        // target, Forge hooks and other mods keep ownership of damage mitigation.
        float amount = (float) getDamage();
        if (amount > 0.0F && target.hurt(damageSources().indirectMagic(this, attacker), amount)) {
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
