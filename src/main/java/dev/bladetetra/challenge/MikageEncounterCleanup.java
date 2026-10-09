package dev.bladetetra.challenge;

import net.minecraft.world.phys.Vec3;

/** SkillRunner closes the foreground scope first; this resets encounter-wide state. */
final class MikageEncounterCleanup {
    static void foreground(MikageEntity owner) {
        owner.setNoGravity(false); owner.getNavigation().stop(); owner.setDeltaMovement(Vec3.ZERO);
    }
    static void encounter(MikageEntity owner) {
        owner.movement().clearCombatFootwork(); owner.duel().clear(); foreground(owner);
        owner.defenseController().hurtCooldownUntil.clear();
        owner.defenseController().playerDefenseProfiles.clear();
    }
    private MikageEncounterCleanup() { }
}
