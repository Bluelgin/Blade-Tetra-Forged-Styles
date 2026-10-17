package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CombatSpacingState;
import net.minecraft.server.level.*;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Owns only idle navigation/strafe commands. Skills retain their own movement and committed facing. */
final class MikageCombatFootwork {
    private final MikageEntity owner;
    private final CombatSpacingState state = new CombatSpacingState();
    private Path ownedPath;
    private Vec3 waypoint;
    private UUID routedTarget;
    private CombatSpacingState.Motion routedMotion;
    private int side = 1;
    private long routeAt;
    private boolean strafing;
    MikageCombatFootwork(MikageEntity owner) { this.owner = owner; }
    void tick() {
        long now = owner.level().getGameTime();
        if (!owner.encounter().footworkAllowed() || !owner.onGround() || owner.isInWaterOrBubble()) { pause(); return; }
        ServerLevel level = (ServerLevel) owner.level();
        ServerPlayer target = owner.getTarget() instanceof ServerPlayer player && owner.encounter().eligible(player)
                ? player : owner.movement().nearestChallengeParticipant(level);
        if (target == null) { clear(); return; }
        owner.setTarget(target);
        Vec3 toward = target.position().subtract(owner.position()).multiply(1, 0, 1);
        double distance = toward.length();
        var motion = state.update(target.getUUID(), distance, now);
        owner.getLookControl().setLookAt(target, 180, 180);
        if (motion == CombatSpacingState.Motion.HOLD || motion == CombatSpacingState.Motion.ENGAGE) { halt(); return; }
        boolean newStep = motion != routedMotion || !target.getUUID().equals(routedTarget);
        if (newStep || waypoint == null || motion != CombatSpacingState.Motion.RETREAT && now >= routeAt) {
            halt(); waypoint = MikageFootworkRoutes.choose(owner, target, motion, side);
            routedTarget = target.getUUID(); routedMotion = motion; routeAt = now + 10;
            if (waypoint == null) { state.blocked(now); side = -side; return; }
        }
        if (!MikageFootworkRoutes.clear(owner, waypoint)) { halt(); state.blocked(now); side = -side; return; }
        Vec3 step = waypoint.subtract(owner.position()).multiply(1, 0, 1);
        if (step.lengthSqr() < .06) { releaseCommands(); return; }
        if (motion == CombatSpacingState.Motion.CLOSE) {
            if (ownedPath == null || owner.getNavigation().isDone() || newStep || now + 10 == routeAt) {
                if (!owner.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.05)) { state.blocked(now); halt(); return; }
                ownedPath = owner.getNavigation().getPath();
            }
        } else {
            Vec3 face = distance < .001 ? owner.getLookAngle().multiply(1, 0, 1).normalize() : toward.normalize();
            Vec3 right = new Vec3(face.z, 0, -face.x); Vec3 direction = step.normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
            owner.setYRot(yaw); owner.setYBodyRot(yaw);
            owner.getMoveControl().strafe((float) direction.dot(face), (float) direction.dot(right)); strafing = true;
        }
    }
    boolean needsExchange(ServerPlayer target) {
        return state.exchangeRequired(target.getUUID()) && owner.distanceToSqr(target) <= 36
                && Math.abs(target.getY() - owner.getY()) <= 3;
    }
    void exchangeCompleted(UUID target) { state.exchangeCompleted(target, owner.level().getGameTime()); }
    void pause() { halt(); state.pause(owner.level().getGameTime()); }
    void clear() { halt(); state.clear(); routedTarget = null; }
    private void halt() { releaseCommands(); waypoint = null; routedMotion = null; }
    private void releaseCommands() {
        boolean ours = strafing || ownedPath != null && owner.getNavigation().getPath() == ownedPath;
        if (ownedPath != null && owner.getNavigation().getPath() == ownedPath) owner.getNavigation().stop();
        if (ours) {
            owner.getMoveControl().strafe(0, 0); owner.setXxa(0); owner.setZza(0);
            owner.setDeltaMovement(0, owner.getDeltaMovement().y, 0);
        }
        ownedPath = null; strafing = false;
    }
}
