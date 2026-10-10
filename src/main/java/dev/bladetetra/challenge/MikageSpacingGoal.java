package dev.bladetetra.challenge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** Reserve movement/look flags even during casts, so random strolling cannot interfere with a skill. */
final class MikageSpacingGoal extends Goal {
    private final MikageEntity mikage;
    MikageSpacingGoal(MikageEntity mikage) {
        this.mikage = mikage; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        return mikage.level() instanceof ServerLevel && mikage.isAlive() && !mikage.isVisitorGuide();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { mikage.movement().tickCombatFootwork(); }
    @Override public void stop() { mikage.movement().pauseCombatFootwork(); }
}
