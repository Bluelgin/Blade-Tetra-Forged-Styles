package dev.bladetetra.kyouka;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.LivingEntity;

/** Locks only enrolled native capabilities. No tick rollback and no shadow balance. */
public final class AwakenedRankConstraintService {
    private static final Map<IConcentrationRank, UUID> LOCKS = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    public static void lock(IConcentrationRank rank, UUID owner) { LOCKS.put(rank, owner); }
    public static void unlock(UUID owner) { LOCKS.values().removeIf(owner::equals); }
    public static boolean locked(IConcentrationRank rank) {
        UUID owner = LOCKS.get(rank);
        if (owner == null) return false;
        var session = KyoukaMirrorSessions.SESSIONS.get(owner);
        // A same-tick weapon swap/death/ring release ends the lock before another blade earns Rank.
        // This reads native Rank only; no writes or rollback are needed to validate the session.
        if (session == null || !KyoukaMirrorSessions.valid(session)) {
            if (session != null) KyoukaMirrorSessions.stop(session);
            else unlock(owner);
            return false;
        }
        return true;
    }
    public static boolean reject(IConcentrationRank rank, LivingEntity user, long points) {
        return points > 0 && !user.level().isClientSide() && locked(rank);
    }
    public static void clear() { LOCKS.clear(); }
    private AwakenedRankConstraintService() {}
}
