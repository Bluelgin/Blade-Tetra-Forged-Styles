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
    final List<BoundaryWallState> boundaryWalls = new ArrayList<>();
    int boundaryWallSequence;
    int boundaryGapCycleTicks;

    int toriiSweepTicks;
    int toriiSweepCooldown = 180;
    Vec3 toriiSweepCenter = Vec3.ZERO;
    UUID toriiSweepFocusTarget;
    final Map<UUID, ToriiScissorState> toriiScissorStates = new HashMap<>();
    boolean toriiScissorCountered;
    int toriiCageTicks;
    int toriiCageCooldown = 140;
    final Map<UUID, CageState> toriiCages = new HashMap<>();
    boolean cageGuardPraised;
    boolean boundarySlashVoiced;
    boolean toriiSweepVoiced;
    boolean toriiCageVoiced;
    boolean cagePerfectCountered;
    Vec3 separatedBoundaryDirection(Vec3 candidate) {
        if (boundaryWalls.isEmpty()) {
            return candidate;
        }
        double candidateAngle = Math.atan2(candidate.z, candidate.x);
        double minimum = Math.PI;
        List<Double> angles = new ArrayList<>();
        for (BoundaryWallState wall : boundaryWalls) {
            double angle = Math.atan2(wall.direction.z, wall.direction.x);
            if (angle < 0.0D) {
                angle += Math.PI * 2.0D;
            }
            angles.add(angle);
            double delta = Math.abs(net.minecraft.util.Mth.wrapDegrees(
                    Math.toDegrees(candidateAngle - angle)));
            minimum = Math.min(minimum, Math.toRadians(delta));
        }
        if (minimum >= Math.toRadians(24.0D)) {
            return candidate;
        }
        angles.sort(Double::compareTo);
        double largestGap = -1.0D;
        double chosen = candidateAngle;
        for (int i = 0; i < angles.size(); i++) {
            double from = angles.get(i);
            double to = i + 1 < angles.size()
                    ? angles.get(i + 1) : angles.get(0) + Math.PI * 2.0D;
            if (to - from > largestGap) {
                largestGap = to - from;
                chosen = from + largestGap * 0.5D;
            }
        }
        return new Vec3(Math.cos(chosen), 0.0D, Math.sin(chosen));
    }

    Vec3 horizontalDirection(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.0001D) {
            direction = fallback.multiply(1.0D, 0.0D, 1.0D);
        }
        return direction.lengthSqr() < 0.0001D
                ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    static double smoothStep(double value) {
        double t = net.minecraft.util.Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    static final class CageState {
        final Vec3 center;
        int guardedTicks;
        int lastGuardTick = Integer.MIN_VALUE;
        CageState(Vec3 center) { this.center = center; }
    }

    static final class ToriiScissorState {
        final float baseYaw;
        int consecutiveGuardTicks;
        int stableGuards;
        boolean feintPlayed;
        ToriiScissorState(float baseYaw) { this.baseYaw = baseYaw; }
    }

    static final class BoundaryWallState {
        final int id;
        final Vec3 center;
        final Vec3 direction;
        double gapAlong = -1.0D;
        int gapTicks;
        double pendingGapAlong = -1.0D;
        int gapWarningTicks;

        BoundaryWallState(int id, Vec3 center, Vec3 direction) {
            this.id = id;
            this.center = center;
            this.direction = direction.normalize();
        }

        Vec3 left() {
            return new Vec3(-direction.z, 0.0D, direction.x);
        }
    }
}
