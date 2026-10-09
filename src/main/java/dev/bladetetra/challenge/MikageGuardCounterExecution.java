package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CastScope;
import dev.bladetetra.challenge.mikage.SkillExecution;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Adapted from BlackFoxCounterCut: direction commits on guard, tell at two, answering cut at ten. */
final class MikageGuardCounterExecution implements SkillExecution {
    private final MikageEntity owner;
    private final UUID target;
    private final Vec3 origin, direction;
    private CastScope scope;
    private int age;

    MikageGuardCounterExecution(MikageEntity owner, ServerPlayer player) {
        this.owner = owner; target = player.getUUID(); origin = owner.position();
        Vec3 flat = player.position().subtract(origin).multiply(1, 0, 1);
        direction = (flat.lengthSqr() < .001 ? owner.getLookAngle().multiply(1, 0, 1) : flat).normalize();
    }

    @Override public void start(CastScope scope) {
        this.scope = scope;
        scope.own(() -> owner.attackTimeline().cancel(scope));
        owner.getNavigation().stop();
        owner.setAction(MikageEntity.MikageAction.GUARD, 9);
        face();
    }
    @Override public boolean windingUp() { return age < 9; }
    @Override public Status tick() {
        if (!(owner.level() instanceof ServerLevel level)
                || !(level.getEntity(target) instanceof ServerPlayer player)
                || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO); face();
        if (++age == 2) {
            level.sendParticles(ParticleTypes.CRIT, owner.getX(), owner.getY() + 1.2, owner.getZ(),
                    12, .3, .3, .3, .02);
            level.playSound(null, owner.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, .7F, 1.5F);
        }
        if (age == 9) {
            owner.setAction(MikageEntity.MikageAction.IAIDO_DRAW, 17);
            owner.attackTimeline().inScope(scope, () -> {
                Entity visual = AttackManager.doSlash(owner, -65, true, false, 0);
                if (visual != null) {
                    visual.getPersistentData().putBoolean(MikageDuelEvents.VISUAL_ONLY, true);
                    if (visual instanceof Projectile projectile) projectile.setOwner(null);
                }
                owner.attackTimeline().meleeCone(1, origin.add(0, .8, 0), direction, 6,
                        Math.toDegrees(Math.acos(.8)), 3,
                        (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * .4F, .35);
            });
            level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, .8F, .8F);
        }
        return age >= 26 ? Status.COMPLETE : Status.RUNNING;
    }
    private void face() {
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
    }
    @Override public void stop(StopReason reason) {
        if (age >= 9 && (reason == StopReason.COMPLETE || reason == StopReason.COUNTERED))
            owner.movement().meleeExchangeCompleted(target);
        owner.duel().cancelGuard();
        owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        if (reason != StopReason.COMPLETE) owner.combatDirector().techniqueCooldown = Math.max(20, owner.combatDirector().techniqueCooldown);
    }
}
