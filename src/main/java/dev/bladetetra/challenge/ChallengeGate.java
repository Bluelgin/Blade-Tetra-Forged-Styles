package dev.bladetetra.challenge;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Process-local portal gate state owned by ChallengeManager. */
final class ChallengeGate {
    final UUID owner;
    final ResourceKey<Level> dimension;
    final Vec3 position;
    final Vec3 right;
    final long expiresAt;
    final ChallengeManager.GateMode mode;
    long challengeId;

    ChallengeGate(UUID owner, ResourceKey<Level> dimension, Vec3 position, Vec3 right,
            long expiresAt, ChallengeManager.GateMode mode) {
        this.owner = owner;
        this.dimension = dimension;
        this.position = position;
        this.right = right;
        this.expiresAt = expiresAt;
        this.mode = mode;
    }
}
