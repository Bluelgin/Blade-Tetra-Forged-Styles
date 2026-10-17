package dev.bladetetra.challenge;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

/** Real foot support is distinct from a rejected movement sweep or arena boundary. */
final class MikageLanding {
    static boolean supported(MikageEntity owner, Vec3 at) {
        if (!MikageCorridorRoute.safe(owner, at, at)) return false;
        var box = owner.getBoundingBox().move(at.subtract(owner.position()));
        return owner.level().getBlockCollisions(owner, box.move(0, -.08, 0)).iterator().hasNext();
    }
    static Vec3 find(MikageEntity owner, Vec3 reference) {
        for (int offset : new int[]{0, 2, -2, 4, -4})
            for (int side : new int[]{0, 2, -2, 4, -4})
                for (int y = 2; y >= -32; y--) {
                    Vec3 at = new Vec3(reference.x + offset, Math.floor(reference.y) + y, reference.z + side);
                    if (!owner.level().hasChunkAt(BlockPos.containing(at))) continue;
                    if (supported(owner, at)) return at;
                    // Slabs/stairs have fractional tops: use actual collision geometry, not block Y.
                    var hit = owner.level().clip(new ClipContext(at.add(0, 1, 0), at,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                    if (hit.getType() == HitResult.Type.BLOCK && supported(owner, hit.getLocation()))
                        return hit.getLocation();
                }
        return null;
    }
    private MikageLanding() { }
}
