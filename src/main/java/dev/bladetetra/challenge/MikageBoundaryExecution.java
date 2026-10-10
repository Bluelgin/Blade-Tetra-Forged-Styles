package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.*;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Grounded, locked lane. Native travelling cleave and temporary contact hazards share one scope. */
final class MikageBoundaryExecution implements SkillExecution {
    static final int WINDUP = 40, TRAIL = 80, TOTAL = WINDUP + TRAIL + 32;
    private final MikageEntity owner;
    private final UUID target;
    private final Runnable committed;
    private CastScope scope;
    private Vec3 origin, heading;
    private int age;
    private boolean countered;
    MikageBoundaryExecution(MikageEntity owner, ServerPlayer target, Runnable committed) {
        this.owner = owner; this.target = target.getUUID(); this.committed = committed;
    }
    @Override public void start(CastScope scope) {
        this.scope = scope; origin = owner.position();
        ChallengeManager.queueDialogue(owner, MikageDialogue.BOUNDARY_SLASH);
        owner.setAction(MikageEntity.MikageAction.IAIDO_READY, WINDUP);
    }
    @Override public boolean windingUp() { return age < WINDUP; }
    int remaining() { return Math.max(0, TOTAL - age); }
    @Override public Status tick() {
        if (!(((ServerLevel) owner.level()).getEntity(target) instanceof ServerPlayer player)
                || !owner.encounter().eligible(player)) return Status.TARGET_LOST;
        owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO); ++age;
        if (age <= 20) {
            heading = player.position().subtract(origin).multiply(1, 0, 1).normalize();
            if (heading.lengthSqr() < .001) heading = new Vec3(0, 0, 1);
            float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
            owner.setYRot(yaw); owner.setYHeadRot(yaw); owner.setYBodyRot(yaw);
        }
        ServerLevel level = (ServerLevel) owner.level();
        if (age < WINDUP && age % 2 == 0) lane(level, ParticleTypes.END_ROD);
        if (age == 20) level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4F, .7F);
        if (age == WINDUP) {
            committed.run(); owner.setAction(MikageEntity.MikageAction.HEAVY_CLEAVE, 20);
            var cleave = new MikageNativeCombat.Rule(null, true, false, (p, source) -> {
                if (owner.distanceToSqr(p) > 36 || !owner.duel().parry(p, origin.add(0, 1, 0))) return true;
                countered = true; owner.duel().rewardOpening(40); return false;
            }, (p, source) -> { });
            MikageNativeCombat.run(owner, scope, cleave, () -> {
                var slash = Drive.doSlash(owner, 90, 16, Vec3.ZERO, true, 1.08, 1.6F);
                slash.setBaseSize(3.0F);
            });
            level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.4F, .8F);
        }
        // Leave a trail behind the wave, not a maze and not damage to every participant.
        if (age == WINDUP + 24 && !countered) {
            MikageNativeCombat.run(owner, scope, new MikageNativeCombat.Rule(null, false, false,
                    (p, source) -> true, (p, source) -> { }), () -> {
                for (int i = 2; i <= 28; i += 2) {
                    Vec3 at = origin.add(heading.scale(i));
                    if (!MikageBoundaryRoute.clear(owner, origin, at)) break;
                    EntityDrive flame = Drive.doSlash(owner, 0, TRAIL - 24, Vec3.ZERO, false, .14, 0);
                    flame.setPos(at.add(0, .15, 0)); flame.setBaseSize(.35F); flame.setDeltaMovement(Vec3.ZERO);
                }
            });
        }
        if (age > WINDUP + 24 && age < WINDUP + TRAIL && age % 3 == 0) lane(level, ParticleTypes.SOUL_FIRE_FLAME);
        if (age == WINDUP + TRAIL) MikageNativeCombat.clearAttacks(scope);
        return countered ? Status.COUNTERED : age >= TOTAL ? Status.COMPLETE : Status.RUNNING;
    }
    private void lane(ServerLevel level, net.minecraft.core.particles.SimpleParticleType particle) {
        for (int i = 2; i <= 28; i++) {
            Vec3 at = origin.add(heading.scale(i));
            if (!MikageBoundaryRoute.clear(owner, origin, at)) break;
            for (int side : new int[]{-1, 0, 1})
                level.sendParticles(particle, at.x - heading.z * side, at.y + .12,
                        at.z + heading.x * side, 1, .1, .05, .1, .005);
        }
    }
    @Override public void stop(StopReason reason) {
        if (!owner.duel().staggered()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.combatDirector().techniqueCooldown = Math.max(32, owner.combatDirector().techniqueCooldown);
    }
}
