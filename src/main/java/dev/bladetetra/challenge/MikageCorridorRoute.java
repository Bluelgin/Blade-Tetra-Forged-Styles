package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Check the entire swept body before cueing a gate, and recheck each movement after block edits. */
record MikageCorridorRoute(Vec3 entry, Vec3 attack, Vec3 exit, Vec3 heading) {
    static MikageCorridorRoute choose(MikageEntity owner, ServerPlayer player, int pass) {
        double start = Math.toRadians(player.getYRot()) + pass * Math.PI * 2 / 3 + Math.PI / 3;
        for (int i = 0; i < 12; i++) {
            double a = start + i * Math.PI / 6;
            Vec3 heading = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 center = player.position().add(0, .15, 0);
            Vec3 entry = center.subtract(heading.scale(13)), attack = center.subtract(heading.scale(4.5));
            Vec3 exit = center.add(heading.scale(15));
            if (safe(owner, entry, exit)) return new MikageCorridorRoute(entry, attack, exit, heading);
        }
        return null;
    }
    static boolean safe(MikageEntity owner, Vec3 from, Vec3 to) {
        ServerLevel level = (ServerLevel) owner.level();
        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .4));
        for (int i = 0; i <= samples; i++) {
            Vec3 at = from.lerp(to, (double) i / samples);
            if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)
                    || ChallengeManager.clampMikagePosition(owner, at).distanceToSqr(at) > .01) return false;
            var box = owner.getBoundingBox().move(at.subtract(owner.position()));
            if (!level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                    || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    || !level.getWorldBorder().isWithinBounds(box) || level.getBlockCollisions(owner, box).iterator().hasNext()
                    || level.containsAnyLiquid(box)) return false;
        }
        return true;
    }
    boolean entryFree(MikageEntity owner) {
        var box = owner.getBoundingBox().move(entry.subtract(owner.position())).inflate(.3);
        return ((ServerLevel) owner.level()).players().stream().noneMatch(p -> p.isAlive() && box.intersects(p.getBoundingBox()));
    }
}
