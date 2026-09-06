package dev.bladetetra.challenge;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Keeps Mikage in a deliberate casting ring instead of using zombie-like melee pursuit. */
final class MikageSpacingGoal extends Goal {
    private final MikageEntity mikage;
    private float strafeDirection = 0.65F;
    private int directionTicks;

    MikageSpacingGoal(MikageEntity mikage) {
        this.mikage = mikage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mikage.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mikage.getTarget();
        if (target == null) {
            return;
        }
        mikage.getLookControl().setLookAt(target, 180.0F, 180.0F);
        if (mikage.isUsingTechnique()) {
            mikage.getNavigation().stop();
            return;
        }
        if (--directionTicks <= 0) {
            directionTicks = 28 + mikage.getRandom().nextInt(28);
            if (mikage.getRandom().nextFloat() < 0.55F) {
                strafeDirection = -strafeDirection;
            }
        }

        double distanceSqr = mikage.distanceToSqr(target);
        if (distanceSqr < 42.25D) { // closer than 6.5 blocks: retreat diagonally
            mikage.getNavigation().stop();
            mikage.getMoveControl().strafe(-0.9F, strafeDirection);
        } else if (distanceSqr > 156.25D) { // farther than 12.5 blocks: close only to casting range
            mikage.getNavigation().moveTo(target, 0.92D);
        } else { // 6.5-12.5 blocks: circle the target
            mikage.getNavigation().stop();
            mikage.getMoveControl().strafe(0.0F, strafeDirection);
        }
    }

    @Override
    public void stop() {
        mikage.getNavigation().stop();
    }
}
