package dev.bladetetra.challenge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Encounter-wide incoming hit ledger and bounded adaptive native-damage calibration. */
final class MikageDefenseController {
    final Map<UUID, Long> hurtCooldownUntil = new HashMap<>();
    final Map<UUID, PlayerDefenseProfile> playerDefenseProfiles = new HashMap<>();
    static final class PlayerDefenseProfile { double rawMultiplier = 1; }
}
