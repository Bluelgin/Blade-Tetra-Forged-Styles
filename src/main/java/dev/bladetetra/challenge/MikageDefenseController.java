package dev.bladetetra.challenge;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adaptation, counter, lock and sword-wheel state for one Mikage encounter. */
final class MikageDefenseController {
    int swordWheelDeployTicks;
    int swordWheelCooldown = 35;
    int swordWheelCounterCooldown;
    int swordWheelBreakTicks;
    final List<UUID> swordWheelEntities = new ArrayList<>();
    final Map<UUID, SaPattern> saPatterns = new HashMap<>();
    final Map<UUID, JudgementPattern> judgementPatterns = new HashMap<>();
    final Map<UUID, Long> hurtCooldownUntil = new HashMap<>();
    final Map<UUID, Long> lockBreakUntil = new HashMap<>();
    final Map<UUID, PursuitPressure> pursuitPressure = new HashMap<>();
    boolean stepIaidoCommitted;
    final Map<UUID, Deque<MovementSample>> movementSamples = new HashMap<>();
    final Map<UUID, ShadowCrossPattern> shadowCrossPatterns = new HashMap<>();
    int zanshinCounterTicks;
    UUID zanshinCounterTarget;
    Vec3 zanshinCounterCenter = Vec3.ZERO;
    final Map<UUID, PlayerDefenseProfile> playerDefenseProfiles = new HashMap<>();
    int mirrorCounterCooldown;
    long lastParryVfxTick = Long.MIN_VALUE;
}
