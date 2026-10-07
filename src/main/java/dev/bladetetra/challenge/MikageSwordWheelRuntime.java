package dev.bladetetra.challenge;

import static dev.bladetetra.challenge.MikageEntity.*;

import dev.bladetetra.registry.ModEntities;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

/** Owns sword-wheel deployment, layer breaks and recovery; entity owns only its synced fields. */
final class MikageSwordWheelRuntime {
    private final MikageEntity owner;
    private final MikageCombatDirector combat;
    private final MikageDefenseController defense;
    private final MikageTechniqueRuntime techniques;
    private final MikageArenaController arena;

    MikageSwordWheelRuntime(MikageEntity owner) {
        this.owner = owner;
        combat = owner.combatDirector();
        defense = owner.defenseController();
        techniques = owner.techniqueRuntime();
        arena = owner.arenaController();
    }

    void registerClosePressure(LivingEntity attacker) {
        if (owner.tickCount - combat.lastClosePressureTick > 32) {
            combat.closePressureHits = 0;
        }
        combat.lastClosePressureTick = owner.tickCount;
        combat.closePressureHits++;
        int threshold = owner.getPhase() >= 3 ? 2 : 3;
        if (combat.closePressureHits >= threshold && defense.swordWheelCooldown <= 0
                && owner.getSwordWheelCount() > 0 && !owner.isSwordWheelDeployed()
                && !techniques.isSignatureActive(arena, defense)) {
            combat.closePressureHits = 0;
            deploySwordWheel(attacker);
        }
    }

    void tickSwordWheel(ServerLevel server) {
        if (defense.swordWheelCooldown > 0) defense.swordWheelCooldown--;
        if (defense.swordWheelCounterCooldown > 0) defense.swordWheelCounterCooldown--;
        if (defense.swordWheelBreakTicks > 0) {
            defense.swordWheelBreakTicks--;
            owner.getNavigation().stop();
            owner.setDeltaMovement(Vec3.ZERO);
            if (defense.swordWheelBreakTicks == 0) {
                int restored = Math.min(3, 1 + owner.getPhase());
                owner.setSwordWheelCount(restored);
                defense.swordWheelCooldown = 90;
                owner.setAction(MikageAction.IDLE, 1);
                owner.legacyEffects().flashStepAwayFromNearestPlayer(server);
            }
        }
        if (!owner.isSwordWheelDeployed()) {
            return;
        }
        if (--defense.swordWheelDeployTicks <= 0 || owner.getSwordWheelCount() <= 0
                || techniques.isSignatureActive(arena, defense)) {
            recallSwordWheel(server, 45);
        }
    }

    void deploySwordWheel(LivingEntity pressureTarget) {
        if (!(owner.level() instanceof ServerLevel server)) {
            return;
        }
        clearSwordWheelEntities(server);
        int count = owner.getSwordWheelCount();
        int solidSlot = owner.getRandom().nextInt(count);
        owner.setSwordWheelDeployed(true);
        defense.swordWheelDeployTicks = 105;
        defense.swordWheelCounterCooldown = 5;
        owner.setAction(MikageAction.CAST_READY, 18);
        server.playSound(null, owner.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.HOSTILE, 1.0F, 1.45F);
        Vec3 wheelCenter = owner.position().add(0.0D, 1.3D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.SWORD_WHEEL,
                wheelCenter.x, wheelCenter.y, wheelCenter.z,
                wheelCenter.x, wheelCenter.y, wheelCenter.z,
                owner.getYRot(), 1.0F, owner.getId(), pressureTarget == null ? -1 : pressureTarget.getId(),
                105, count), wheelCenter);
        for (int slot = 0; slot < count; slot++) {
            MikagePhantomSwordEntity sword = ModEntities.MIKAGE_PHANTOM_SWORD.get().create(server);
            if (sword == null) continue;
            double angle = slot * Math.PI * 2.0D / count;
            sword.setPos(owner.getX() + Math.cos(angle) * 3.8D,
                    owner.getY() + 1.3D, owner.getZ() + Math.sin(angle) * 3.8D);
            sword.configure(owner, slot, slot == solidSlot);
            server.addFreshEntity(sword);
            defense.swordWheelEntities.add(sword.getUUID());
        }
        if (pressureTarget != null) {
            owner.legacyEffects().flashStepAway(pressureTarget, server, 7.5D);
        }
    }

    void breakSwordWheelLayer(MikagePhantomSwordEntity broken, Entity attacker) {
        if (!(owner.level() instanceof ServerLevel server) || !owner.isSwordWheelDeployed()) {
            return;
        }
        int remaining = Math.max(0, owner.getSwordWheelCount() - 1);
        Vec3 breakPoint = broken.position().add(0.0D, 0.6D, 0.0D);
        owner.presentation().sendTechniqueVfx(server, new BladeTechniqueVfxPacket(
                BladeTechniqueVfxPacket.SWORD_WHEEL_BREAK,
                breakPoint.x, breakPoint.y, breakPoint.z,
                owner.getX(), owner.getY() + 1.0D, owner.getZ(),
                owner.getYRot(), remaining == 0 ? 1.3F : 0.82F,
                broken.getId(), attacker == null ? -1 : attacker.getId(),
                remaining == 0 ? 24 : 14, remaining), breakPoint);
        owner.setSwordWheelCount(remaining);
        clearSwordWheelEntities(server);
        owner.setSwordWheelDeployed(false);
        defense.swordWheelCooldown = 34;
        combat.closePressureHits = 0;
        if (remaining == 0) {
            defense.swordWheelBreakTicks = 120;
            combat.signatureRecoveryTicks = Math.max(combat.signatureRecoveryTicks, defense.swordWheelBreakTicks);
            owner.setAction(MikageAction.STAGGERED, defense.swordWheelBreakTicks);
            server.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE,
                    SoundSource.HOSTILE, 0.75F, 0.62F);
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    owner.getX(), owner.getY() + 1.0D, owner.getZ(), 28,
                    1.1D, 0.8D, 1.1D, 0.0D);
        } else {
            combat.signatureRecoveryTicks = Math.max(combat.signatureRecoveryTicks, 16);
            owner.setAction(MikageAction.STAGGERED, 16);
        }
    }

    void counterWithSwordWheel(LivingEntity target) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        Vec3 away = target.position().subtract(owner.position());
        if (away.lengthSqr() > 0.001D) {
            target.push(away.normalize().x * 0.55D, 0.18D, away.normalize().z * 0.55D);
        }
        float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.32F;
        if (target instanceof ServerPlayer player) {
            owner.dealTrialDamage(player, damage, false);
        } else {
            target.hurt(server.damageSources().indirectMagic(owner, owner), damage);
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
        owner.setSwordWheelDeployed(false);
        defense.swordWheelCooldown = Math.max(defense.swordWheelCooldown, cooldown);
    }

    void clearSwordWheelEntities(ServerLevel server) {
        for (UUID id : defense.swordWheelEntities) {
            Entity entity = server.getEntity(id);
            if (entity != null) entity.discard();
        }
        defense.swordWheelEntities.clear();
        server.getEntitiesOfClass(MikagePhantomSwordEntity.class,
                        owner.getBoundingBox().inflate(16.0D), sword -> true)
                .stream().filter(sword -> sword.getPersistentData()
                        .getLong("blade_tetra_owner_id") == owner.getId())
                .forEach(Entity::discard);
    }

}
