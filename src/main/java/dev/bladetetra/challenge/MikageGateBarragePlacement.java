package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;

/** A grounded, reachable gate. The central approach must remain walkable after terrain edits. */
record MikageGateBarragePlacement(Vec3 base, Vec3 forward, Vec3 approach) {
    static MikageGateBarragePlacement choose(MikageEntity boss, ServerPlayer player) {
        if (!player.onGround()) return null;
        double start = Math.toRadians(player.getYRot());
        for (int i = 0; i < 12; i++) {
            double angle = start + i * Math.PI / 6;
            Vec3 radial = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            var placement = new MikageGateBarragePlacement(player.position().add(radial.scale(18)),
                    radial.scale(-1), player.position());
            if (placement.safe(boss)) return placement;
        }
        return null;
    }
    Vec3 right() { return new Vec3(forward.z, 0, -forward.x); }
    AABB bounds() {
        double x = Math.abs(right().x) * 7.3 + Math.abs(forward.x) * .8;
        double z = Math.abs(right().z) * 7.3 + Math.abs(forward.z) * .8;
        return new AABB(base.x - x, base.y, base.z - z, base.x + x, base.y + 11.3, base.z + z);
    }
    boolean safe(MikageEntity boss) {
        ServerLevel level = (ServerLevel) boss.level();
        for (double x : new double[]{-7.3, 0, 7.3}) {
            Vec3 at = base.add(right().scale(x));
            if (ChallengeManager.clampMikagePosition(boss, at).distanceToSqr(at) > .01) return false;
        }
        AABB box = bounds();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                || !level.getWorldBorder().isWithinBounds(box)
                || level.getBlockCollisions(boss, box).iterator().hasNext() || level.containsAnyLiquid(box)) return false;
        int steps = (int) Math.ceil(approach.distanceTo(base) / .5);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = approach.lerp(base, (double) i / Math.max(1, steps));
            AABB body = new AABB(at.x - .7, at.y, at.z - .7, at.x + .7, at.y + 1.9, at.z + .7);
            if (ChallengeManager.clampMikagePosition(boss, at).distanceToSqr(at) > .01
                    || !level.hasChunksAt(BlockPos.containing(body.minX, body.minY, body.minZ),
                        BlockPos.containing(body.maxX, body.maxY, body.maxZ))
                    || !level.getWorldBorder().isWithinBounds(body) || level.getBlockCollisions(boss, body).iterator().hasNext() || level.containsAnyLiquid(body)
                    || !level.getBlockState(BlockPos.containing(at.add(0, -.1, 0)))
                    .isFaceSturdy(level, BlockPos.containing(at.add(0, -.1, 0)), Direction.UP)) return false;
        }
        return true;
    }
}
