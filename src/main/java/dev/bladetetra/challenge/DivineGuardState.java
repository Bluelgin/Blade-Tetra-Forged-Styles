package dev.bladetetra.challenge;

import java.util.UUID;

/** Exactly one rescue per Ritual, shared by all participants; no retained player/entity references. */
final class DivineGuardState {
    private boolean used;
    private UUID protectedPlayer;
    private long until;

    boolean tryUse(UUID player, long now) {
        if (used) return false;
        used = true; protectedPlayer = player; until = now + 60;
        return true;
    }
    boolean protects(UUID player, long now) { return player.equals(protectedPlayer) && now < until; }
    boolean used() { return used; }
    void revokeProtection() { protectedPlayer = null; until = 0; }
}
