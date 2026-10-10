package dev.bladetetra.challenge;

import net.minecraft.server.level.ServerLevel;

/** Shared maintenance only. Each execution owns its attacks, movement and recovery. */
final class MikageRuntimeCoordinator {
    static void tick(MikageEntity owner, ServerLevel server, int phase) {
        if (owner.combatDirector().phaseProtectionTicks > 0) owner.combatDirector().phaseProtectionTicks--;
        if (owner.tickCount % 100 == 0) {
            long now = server.getGameTime();
            owner.defenseController().hurtCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if (owner.shouldResetExpiredAction()) owner.setAction(MikageEntity.MikageAction.IDLE, 1);
        owner.movement().enforceArenaBoundary();
    }
    private MikageRuntimeCoordinator() { }
}
