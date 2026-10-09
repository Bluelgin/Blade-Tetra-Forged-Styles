package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CombatSpacingState.Motion;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;

import static dev.bladetetra.challenge.MikageLegacyTiming.*;

/** Short, grounded steps never teleport, cross flame walls, or step into unloaded space. */
final class MikageFootworkRoutes {
    static Vec3 choose(MikageEntity owner, ServerPlayer target, Motion motion, int side) {
        Vec3 toward = target.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
        if (toward.lengthSqr() < .001) toward = owner.getLookAngle().multiply(1, 0, 1).normalize();
        if (toward.lengthSqr() < .001) toward = new Vec3(0, 0, 1);
        Vec3 right = new Vec3(toward.z, 0, -toward.x);
        double distance = target.position().subtract(owner.position()).multiply(1, 0, 1).length();
        for (int sign : new int[]{side, -side}) {
            Vec3 delta = switch (motion) {
                case RETREAT -> toward.scale(-2).add(right.scale(sign * 1.3));
                case ORBIT -> {
                    Vec3 radial = owner.position().subtract(target.position()).multiply(1, 0, 1);
                    Vec3 arc = radial.add(right.scale(sign * 1.7)).normalize().scale(Math.max(6, Math.min(8, distance)));
                    yield arc.subtract(radial);
                }
                case CLOSE -> toward.scale(Math.min(3, Math.max(0, distance - 5.2)))
                        .add(right.scale(sign * .4));
                default -> Vec3.ZERO;
            };
            Vec3 at = owner.position().add(delta);
            if (delta.lengthSqr() > .04 && clear(owner, at)) return at;
        }
        return null;
    }
    static boolean clear(MikageEntity owner, Vec3 destination) {
        ServerLevel level = (ServerLevel) owner.level();
        int steps = Math.max(1, (int) Math.ceil(owner.position().distanceTo(destination) / .3));
        if (steps > 16) return false;
        for (int i = 0; i <= steps; i++) {
            Vec3 at = owner.position().lerp(destination, (double) i / steps);
            AABB body = owner.getBoundingBox().move(at.subtract(owner.position()));
            BlockPos bottom = BlockPos.containing(at.add(0, -.1, 0));
            if (ChallengeManager.clampMikagePosition(owner, at).distanceToSqr(at) > .001
                    || body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                    || !level.hasChunksAt(BlockPos.containing(body.minX, body.minY, body.minZ),
                        BlockPos.containing(body.maxX, body.maxY, body.maxZ))
                    || !level.getWorldBorder().isWithinBounds(body)
                    || level.getBlockCollisions(owner, body).iterator().hasNext() || level.containsAnyLiquid(body)
                    || !level.getBlockState(bottom).isFaceSturdy(level, bottom, Direction.UP)
                    || touchesFlame(owner, body)
                    || !level.getEntitiesOfClass(ServerPlayer.class, body, p -> p.isAlive() && !p.isSpectator()).isEmpty())
                return false;
        }
        return true;
    }
    private static boolean touchesFlame(MikageEntity owner, AABB body) {
        for (var wall : owner.arenaController().boundaryWalls) {
            if (body.maxY < wall.center.y || body.minY > wall.center.y + BOUNDARY_FLAME_HEIGHT) continue;
            Vec3 relative = body.getCenter().subtract(wall.center);
            double along = relative.dot(wall.direction), across = relative.dot(wall.left());
            double alongRadius = (Math.abs(wall.direction.x) * body.getXsize()
                    + Math.abs(wall.direction.z) * body.getZsize()) * .5;
            double acrossRadius = (Math.abs(wall.direction.z) * body.getXsize()
                    + Math.abs(wall.direction.x) * body.getZsize()) * .5;
            if (along + alongRadius < -.45 || along - alongRadius > BOUNDARY_FLASH_LENGTH + .45) continue;
            if (wall.gapTicks > 0 && Math.abs(along - wall.gapAlong) + alongRadius <= BOUNDARY_GAP_HALF_WIDTH) continue;
            if (Math.abs(across) <= BOUNDARY_FLAME_HALF_WIDTH + acrossRadius) return true;
        }
        return false;
    }
    private MikageFootworkRoutes() { }
}
