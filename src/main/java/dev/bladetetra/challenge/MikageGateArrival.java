package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Samples actual arena space before announcing a fixed spatial sound source. */
final class MikageGateArrival {
    static Vec3 choose(MikageEntity owner, ServerPlayer target, Vec3 previous) {
        ServerLevel level = (ServerLevel) owner.level();
        double start = owner.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 12; i++) {
            double angle = start + i * Math.PI / 6;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(3.6);
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                Vec3 at = target.position().add(radial).add(0, dy, 0);
                if (previous != null && at.distanceToSqr(previous) < 4) continue;
                if (safe(owner, target, level, at)) return at;
            }
        }
        return null;
    }

    static boolean safe(MikageEntity owner, ServerPlayer target, ServerLevel level, Vec3 at) {
        if (ChallengeManager.clampMikagePosition(owner, at).distanceToSqr(at) > .01
                || !level.hasChunkAt(BlockPos.containing(at))) return false;
        var box = owner.getBoundingBox().move(at.subtract(owner.position()));
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(owner, box)
                || level.containsAnyLiquid(box)) return false;
        if (target.onGround()) {
            BlockPos floor = BlockPos.containing(at.add(0, -.1, 0));
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        for (ServerPlayer player : level.players())
            if (player.isAlive() && box.inflate(.25).intersects(player.getBoundingBox())) return false;
        Vec3 eye = at.add(0, owner.getEyeHeight(), 0);
        return level.clip(new ClipContext(eye, target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private MikageGateArrival() {}
}
