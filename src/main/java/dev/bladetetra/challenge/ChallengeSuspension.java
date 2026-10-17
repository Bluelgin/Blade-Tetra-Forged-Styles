package dev.bladetetra.challenge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import java.util.Set;
import java.util.UUID;
import static dev.bladetetra.challenge.ChallengeManager.*;

/** Temporary combat closure and safe retirement, separate from the normal challenge lifecycle. */
final class ChallengeSuspension {
    static boolean rejectEntry(ServerPlayer player, GateMode mode) {
        if (gateModeAvailable(mode)) return false;
        notifyChallengeSealed(player);
        return true;
    }
    static boolean suspendIfNeeded(ChallengeSession session, MinecraftServer server, ServerLevel mirror) {
        if (!MikageChallengeAccess.shouldSuspend(session.mode == GateMode.VISITOR, session.won)) return false;
        close(session, server, mirror);
        return true;
    }
    private static void close(ChallengeSession session, MinecraftServer server, ServerLevel mirror) {
        // Offline players keep their NBT return point for login recovery. Inventory, permanent
        // completion data and realm blocks are never removed by retiring a process-local session.
        for (UUID playerId : Set.copyOf(session.participants)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                session.returnPlayer(player);
                notifyChallengeSealed(player);
            }
        }
        mirror.getEntitiesOfClass(MikageEntity.class,
                new AABB(session.offsetX - 32, 50, session.offsetZ - 172,
                        session.offsetX + 290, 340, session.offsetZ + 152),
                entity -> entity.getPersistentData().getLong("blade_tetra_challenge") == session.id)
                .forEach(MikageEntity::discard);
        cleanupChallengeAttacks(mirror, session);
        session.participants.clear();
        session.origins.clear();
        session.disconnectedTicks.clear();
        session.closed = true;
    }
    private ChallengeSuspension() { }
}
