package dev.bladetetra.challenge;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Server-authoritative hit boxes for Mikage. SlashBlade entities remain the
 * visuals, while this timeline owns player targeting and damage independently
 * of SlashBlade's global PvP configuration.
 */
final class MikageAttackTimeline {
    private final MikageEntity owner;
    private final List<Hit> pending = new ArrayList<>();

    MikageAttackTimeline(MikageEntity owner) {
        this.owner = owner;
    }

    void circle(int delay, Vec3 center, double radius, double height,
            float damage, double knockback) {
        pending.add(new Hit(owner.tickCount + delay, Shape.CIRCLE, center,
                center, Vec3.ZERO, radius, height, -1.0D, damage, knockback, null));
    }

    void line(int delay, Vec3 start, Vec3 end, double width, double height,
            float damage, double knockback) {
        pending.add(new Hit(owner.tickCount + delay, Shape.LINE, start, end,
                Vec3.ZERO, width, height, -1.0D, damage, knockback, null));
    }

    void cone(int delay, Vec3 origin, Vec3 direction, double range,
            double halfAngleDegrees, double height, float damage, double knockback) {
        cone(delay, origin, direction, range, halfAngleDegrees, height, damage, knockback, null);
    }

    void cone(int delay, Vec3 origin, Vec3 direction, double range,
            double halfAngleDegrees, double height, float damage, double knockback,
            Consumer<Boolean> result) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 0.001D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        pending.add(new Hit(owner.tickCount + delay, Shape.CONE, origin,
                origin, flat.normalize(), range, height,
                Math.cos(Math.toRadians(halfAngleDegrees)), damage, knockback, result));
    }

    void tick(ServerLevel level) {
        Iterator<Hit> iterator = pending.iterator();
        while (iterator.hasNext()) {
            Hit hit = iterator.next();
            if (hit.executeTick > owner.tickCount) {
                continue;
            }
            boolean landed = apply(level, hit);
            if (hit.result != null) {
                hit.result.accept(landed);
            }
            iterator.remove();
        }
    }

    void clear() {
        pending.clear();
    }

    private boolean apply(ServerLevel level, Hit hit) {
        boolean landed = false;
        AABB bounds = switch (hit.shape) {
            case CIRCLE, CONE -> new AABB(hit.start.x - hit.radius,
                    hit.start.y - hit.height, hit.start.z - hit.radius,
                    hit.start.x + hit.radius, hit.start.y + hit.height,
                    hit.start.z + hit.radius);
            case LINE -> new AABB(
                    Math.min(hit.start.x, hit.end.x) - hit.radius,
                    Math.min(hit.start.y, hit.end.y) - hit.height,
                    Math.min(hit.start.z, hit.end.z) - hit.radius,
                    Math.max(hit.start.x, hit.end.x) + hit.radius,
                    Math.max(hit.start.y, hit.end.y) + hit.height,
                    Math.max(hit.start.z, hit.end.z) + hit.radius);
        };
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bounds,
                candidate -> candidate.isAlive() && !candidate.isCreative()
                        && !candidate.isSpectator()
                        && ChallengeManager.isParticipant(owner, candidate))) {
            if (!contains(hit, player.position().add(0.0D, 0.9D, 0.0D))) {
                continue;
            }
            landed = true;
            if (owner.dealTrialDamage(player, hit.damage, false)) {
                Vec3 away = player.position().subtract(hit.start)
                        .multiply(1.0D, 0.0D, 1.0D);
                if (away.lengthSqr() < 0.01D) {
                    away = hit.direction.reverse();
                }
                player.knockback(hit.knockback, -away.x, -away.z);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        player.getX(), player.getY() + 1.0D, player.getZ(),
                        5, 0.25D, 0.45D, 0.25D, 0.08D);
            }
        }
        return landed;
    }

    private static boolean contains(Hit hit, Vec3 point) {
        if (Math.abs(point.y - hit.start.y) > hit.height) {
            return false;
        }
        Vec3 flatPoint = new Vec3(point.x, hit.start.y, point.z);
        if (hit.shape == Shape.CIRCLE) {
            return flatPoint.distanceToSqr(hit.start) <= hit.radius * hit.radius;
        }
        if (hit.shape == Shape.CONE) {
            Vec3 relative = flatPoint.subtract(hit.start);
            double distance = relative.length();
            return distance <= hit.radius && distance > 0.001D
                    && relative.scale(1.0D / distance).dot(hit.direction) >= hit.cosine;
        }
        Vec3 segment = hit.end.subtract(hit.start).multiply(1.0D, 0.0D, 1.0D);
        Vec3 relative = flatPoint.subtract(hit.start).multiply(1.0D, 0.0D, 1.0D);
        double lengthSqr = segment.lengthSqr();
        double t = lengthSqr < 0.001D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, relative.dot(segment) / lengthSqr));
        Vec3 nearest = hit.start.add(segment.scale(t));
        return flatPoint.distanceToSqr(nearest) <= hit.radius * hit.radius;
    }

    private enum Shape {
        CIRCLE,
        LINE,
        CONE
    }

    private record Hit(int executeTick, Shape shape, Vec3 start, Vec3 end,
            Vec3 direction, double radius, double height, double cosine,
            float damage, double knockback, Consumer<Boolean> result) {
    }
}
