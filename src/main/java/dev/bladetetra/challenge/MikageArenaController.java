package dev.bladetetra.challenge;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mutable arena/boundary/torii state for one Mikage encounter. */
final class MikageArenaController {
    int boundarySlashDelay;
    Vec3 boundarySlashCenter = Vec3.ZERO;
    Vec3 boundarySlashReturn = Vec3.ZERO;
    Vec3 boundarySlashHover = Vec3.ZERO;
    Vec3 boundarySlashDirection = new Vec3(0.0D, 0.0D, 1.0D);
    UUID boundarySlashTarget;
    boolean boundarySlashLocked;
    boolean boundarySlashExecuted;
    boolean boundaryFlashPending;
    int boundaryFlashCharge;
    int boundaryFlashCycle;
    int boundaryFlashReadyTicks;
    final Map<UUID, Integer> boundaryGuardHoldTicks = new HashMap<>();
    final List<MikageEntity.BoundaryWallState> boundaryWalls = new ArrayList<>();
    int boundaryWallSequence;
    int boundaryGapCycleTicks;

    int toriiSweepTicks;
    int toriiSweepCooldown = 180;
    Vec3 toriiSweepCenter = Vec3.ZERO;
    UUID toriiSweepFocusTarget;
    final Map<UUID, MikageEntity.ToriiScissorState> toriiScissorStates = new HashMap<>();
    boolean toriiScissorCountered;
    int toriiCageTicks;
    int toriiCageCooldown = 140;
    final Map<UUID, MikageEntity.CageState> toriiCages = new HashMap<>();
    boolean cageGuardPraised;
    boolean boundarySlashVoiced;
    boolean toriiSweepVoiced;
    boolean toriiCageVoiced;
    boolean cagePerfectCountered;
}
