package dev.bladetetra.challenge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Arena-constrained movement and participant lookup. */
final class MikageArenaMovement {
    private final MikageEntity owner;
    private final MikageCombatFootwork footwork;

    MikageArenaMovement(MikageEntity owner) {
        this.owner = owner;
        footwork = new MikageCombatFootwork(owner);
    }

    void tickCombatFootwork() { footwork.tick(); }
    void pauseCombatFootwork() { footwork.pause(); }
    void clearCombatFootwork() { footwork.clear(); }
    boolean needsMeleeExchange(ServerPlayer target) { return footwork.needsExchange(target); }
    void meleeExchangeCompleted(java.util.UUID target) { footwork.exchangeCompleted(target); }

    void teleportWithinArena(double x, double y, double z) {
        Vec3 clamped = ChallengeManager.clampMikagePosition(owner, new Vec3(x, y, z));
        owner.teleportTo(clamped.x, clamped.y, clamped.z);
    }

    void enforceArenaBoundary() {
        Vec3 clamped = ChallengeManager.clampMikagePosition(owner, owner.position());
        if (clamped.distanceToSqr(owner.position()) < 0.0001D) return;
        owner.teleportTo(clamped.x, clamped.y, clamped.z);
        owner.getNavigation().stop();
        Vec3 center = ChallengeManager.arenaCenter(owner);
        Vec3 outward = owner.position().subtract(center).multiply(1.0D, 0.0D, 1.0D);
        Vec3 motion = owner.getDeltaMovement();
        if (outward.lengthSqr() > 0.001D) {
            Vec3 normal = outward.normalize();
            double outwardSpeed = motion.multiply(1.0D, 0.0D, 1.0D).dot(normal);
            if (outwardSpeed > 0.0D) {
                owner.setDeltaMovement(motion.subtract(normal.scale(outwardSpeed)));
            }
        }
    }

    ServerPlayer nearestChallengeParticipant(ServerLevel server) {
        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : server.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !ChallengeManager.isParticipant(owner, player)) continue;
            double distance = owner.distanceToSqr(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

}
