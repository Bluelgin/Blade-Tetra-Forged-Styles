package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Executes bounded Blade Tetra-owned primitives for a generated fusion plan. */
final class ProgrammaticFusionHandler {
    static final float DRIVE_SPEED = 2.0F;
    static final int DRIVE_LIFETIME = 12;
    static final double FAN_ANGLE_DEGREES = 12.0D;

    static void onSlashArt(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (!ModSlashBladeAbilities.PROGRAMMATIC_FUSION.getId()
                .equals(state.getSlashArtsKey())) {
            return;
        }
        ProgrammaticFusionPlan plan = ProgrammaticFusionPlan.from(blade);
        if (plan == null) {
            return;
        }

        Vec3 forward = player.getLookAngle();
        if (plan.primaryDriveDamage() > 0.0D) {
            spawnDrive(player, forward, plan.primaryDriveDamage(), 0, -90.0F,
                    DRIVE_SPEED);
        }

        ProgrammaticFusionProfile.Response response = plan.response().response();
        for (int index = 0; index < plan.responseCount(); index++) {
            Vec3 direction = rotateHorizontal(forward, responseYaw(response, index));
            spawnDrive(player, direction, plan.responseDriveDamage(),
                    responseDelay(response, index), responseRoll(response, index),
                    responseSpeed(response, index));
        }
    }

    static double responseYaw(ProgrammaticFusionProfile.Response response, int index) {
        return switch (response) {
            case SAKURA_CROSS -> index == 0 ? -10.0D : 10.0D;
            case JUDGEMENT_ECHO -> spreadOffset(index, 3, 6.0D);
            case VOID_TRIDENT -> spreadOffset(index, 3, FAN_ANGLE_DEGREES);
            case CIRCLE_RING -> index * 90.0D;
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

    static float responseRoll(ProgrammaticFusionProfile.Response response, int index) {
        return switch (response) {
            case HORIZONTAL_DRIVE -> 0.0F;
            case VERTICAL_DRIVE, PIERCING_FOCUS, WAVE_EDGE, FOCUSED_DRIVE -> -90.0F;
            case SAKURA_CROSS -> index == 0 ? 22.5F : 157.5F;
            case JUDGEMENT_ECHO -> 45.0F + index * 120.0F;
            case VOID_TRIDENT -> -20.0F + index * 20.0F;
            case CIRCLE_RING -> index * 90.0F;
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

    private static void spawnDrive(ServerPlayer player, Vec3 direction,
            double damage, int delay, float roll, float speed) {
        EntityDrive drive = Drive.doSlash(player, roll, DRIVE_LIFETIME, Vec3.ZERO,
                false, damage, speed);
        if (drive == null) {
            return;
        }
        drive.setDelay(delay);
        SoulLegacyDamageGuard.markSecondary(drive);
        Vec3 normalized = direction.normalize();
        drive.shoot(normalized.x, normalized.y, normalized.z, speed, 0.0F);
    }

    private ProgrammaticFusionHandler() {
    }
}
