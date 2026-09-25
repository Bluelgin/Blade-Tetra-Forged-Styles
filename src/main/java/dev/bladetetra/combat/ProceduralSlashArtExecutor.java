package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Conservative bounded response grammar used only by programmatic legacy fusion.
 *
 * <p>Player-authored forged Slash Arts no longer use this executor: they splice
 * SlashBlade's native ComboState graph directly and therefore inherit the source
 * art's own combat behavior.</p>
 */
final class ProceduralSlashArtExecutor {
    static final float DRIVE_SPEED = 2.0F;
    static final int DRIVE_LIFETIME = 12;
    static final double FAN_ANGLE_DEGREES = 12.0D;

    static void execute(ServerPlayer player,
            ProgrammaticFusionProfile.Response response,
            int count, double damagePerHit, int baseDelay, double angleScale) {
        execute(player, player.getLookAngle(), response, count, damagePerHit,
                baseDelay, angleScale);
    }

    static void execute(ServerPlayer player, Vec3 forward,
            ProgrammaticFusionProfile.Response response,
            int count, double damagePerHit, int baseDelay, double angleScale) {
        int boundedCount = Math.max(1, Math.min(12, count));
        Vec3 aim = safeDirection(forward, player.getLookAngle());
        for (int index = 0; index < boundedCount; index++) {
            Vec3 direction = rotateHorizontal(
                    aim, responseYaw(response, index, boundedCount, angleScale));
            spawnDrive(player, direction, damagePerHit,
                    baseDelay + responseDelay(response, index),
                    responseRoll(response, index, boundedCount),
                    responseSpeed(response, index));
        }
    }

    static double responseYaw(ProgrammaticFusionProfile.Response response,
            int index, int count, double angleScale) {
        double scale = Math.max(0.25D, Math.min(2.5D, angleScale));
        return switch (response) {
            case SAKURA_CROSS -> spreadOffset(index, count, 20.0D * scale);
            case JUDGEMENT_ECHO -> spreadOffset(index, count, 6.0D * scale);
            case VOID_TRIDENT -> spreadOffset(index, count,
                    FAN_ANGLE_DEGREES * scale);
            case CIRCLE_RING -> index * (360.0D / Math.max(1, count));
            default -> 0.0D;
        };
    }

    static int responseDelay(ProgrammaticFusionProfile.Response response, int index) {
        return switch (response) {
            case JUDGEMENT_ECHO -> index * 3;
            case VOID_TRIDENT -> index;
            case WAVE_EDGE -> index * 2;
            default -> 0;
        };
    }

    static float responseSpeed(ProgrammaticFusionProfile.Response response, int index) {
        return switch (response) {
            case JUDGEMENT_ECHO -> 1.55F;
            case VOID_TRIDENT -> 2.15F;
            case CIRCLE_RING -> 1.60F;
            case WAVE_EDGE -> 1.20F + index * 0.28F;
            default -> DRIVE_SPEED;
        };
    }

    static float responseRoll(ProgrammaticFusionProfile.Response response,
            int index, int count) {
        return switch (response) {
            case HORIZONTAL_DRIVE -> 0.0F;
            case VERTICAL_DRIVE, PIERCING_FOCUS, WAVE_EDGE, FOCUSED_DRIVE -> -90.0F;
            case SAKURA_CROSS -> count <= 1
                    ? 90.0F
                    : 22.5F + index * (135.0F / (count - 1));
            case JUDGEMENT_ECHO -> 45.0F + index * (360.0F / Math.max(1, count));
            case VOID_TRIDENT -> (float) spreadOffset(index, count, 20.0D);
            case CIRCLE_RING -> index * (360.0F / Math.max(1, count));
        };
    }

    static double spreadOffset(int index, int count, double spacing) {
        if (count <= 1) {
            return 0.0D;
        }
        double center = (count - 1) * 0.5D;
        return (index - center) * spacing;
    }

    static Vec3 rotateHorizontal(Vec3 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = direction.x * cosine - direction.z * sine;
        double z = direction.x * sine + direction.z * cosine;
        Vec3 rotated = new Vec3(x, direction.y, z);
        return rotated.lengthSqr() < 1.0E-8D ? direction : rotated.normalize();
    }

    static void spawnDrive(ServerPlayer player, Vec3 direction,
            double damage, int delay, float roll, float speed) {
        EntityDrive drive = Drive.doSlash(player, roll, DRIVE_LIFETIME, Vec3.ZERO,
                false, Math.max(0.0D, damage), speed);
        if (drive == null) {
            return;
        }
        drive.setDelay(Math.max(0, delay));
        SoulLegacyDamageGuard.markSecondary(drive);
        Vec3 normalized = safeDirection(direction, player.getLookAngle());
        drive.shoot(normalized.x, normalized.y, normalized.z, speed, 0.0F);
    }

    private static Vec3 safeDirection(Vec3 direction, Vec3 fallback) {
        if (direction != null && direction.lengthSqr() >= 1.0E-8D) {
            return direction.normalize();
        }
        if (fallback != null && fallback.lengthSqr() >= 1.0E-8D) {
            return fallback.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }

    private ProceduralSlashArtExecutor() {
    }
}
