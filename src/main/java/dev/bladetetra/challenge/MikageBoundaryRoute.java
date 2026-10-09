package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** The warned lane never crosses unloaded chunks, arena edges or solid walls. */
final class MikageBoundaryRoute {
    static boolean clear(MikageEntity owner, Vec3 from, Vec3 to) {
        var level = owner.level();
        return ChallengeManager.clampMikagePosition(owner, to).distanceToSqr(to) < .001
                && level.hasChunkAt(BlockPos.containing(to))
                && level.clip(new ClipContext(from.add(0, .6, 0), to.add(0, .6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
    }
    private MikageBoundaryRoute() { }
}
