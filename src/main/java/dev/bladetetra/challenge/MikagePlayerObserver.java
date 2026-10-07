package dev.bladetetra.challenge;

import dev.bladetetra.challenge.mikage.CombatObservation;
import dev.bladetetra.challenge.mikage.PlayerBehaviorMemory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Samples actual participant movement and visible actions on the server. */
final class MikagePlayerObserver {
    private final MikageEntity owner;
    private final PlayerBehaviorMemory memory = new PlayerBehaviorMemory();
    private final Map<UUID, Vec3> previousPositions = new HashMap<>();

    MikagePlayerObserver(MikageEntity owner) { this.owner = owner; }

    boolean eligible(ServerPlayer player) {
        return player.level() == owner.level() && player.isAlive()
                && !player.isCreative() && !player.isSpectator()
                && ChallengeManager.isParticipant(owner, player);
    }

    void tick(ServerLevel level) {
        var present = new HashSet<UUID>();
        long tick = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (!eligible(player)) continue;
            UUID id = player.getUUID();
            present.add(id);
            Vec3 position = player.position();
            Vec3 previous = previousPositions.put(id, position);
            Vec3 direction = position.subtract(owner.position()).multiply(1, 0, 1);
            Vec3 movement = previous == null ? Vec3.ZERO : position.subtract(previous).multiply(1, 0, 1);
            // Teleports, including arena correction, are not dodge/retreat evidence.
            double radial = movement.lengthSqr() > 9 || direction.lengthSqr() < 0.001
                    ? 0 : movement.dot(direction.normalize());
            memory.sample(id, new PlayerBehaviorMemory.Sample(tick,
                    owner.distanceTo(player), player.getY() - owner.getY(), radial,
                    owner.isBladeGuarding(player), !player.onGround()));
        }
        previousPositions.keySet().retainAll(present);
        memory.retain(present, tick);
    }

    CombatObservation observation(ServerPlayer player) {
        return eligible(player) ? memory.observation(player.getUUID(), owner.level().getGameTime()) : null;
    }

    void attack(ServerPlayer player) {
        if (eligible(player)) memory.attack(player.getUUID(), owner.level().getGameTime());
    }

    void slashArt(ServerPlayer player, String id) {
        if (eligible(player)) memory.slashArt(player.getUUID(), owner.level().getGameTime(), id);
    }

    void clear() { memory.clear(); previousPositions.clear(); }
}
