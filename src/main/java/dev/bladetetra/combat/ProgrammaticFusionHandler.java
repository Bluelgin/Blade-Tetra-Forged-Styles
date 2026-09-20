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
    static final double FAN_ANGLE_DEGREES = 16.0D;

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
            spawnDrive(player, forward, plan.primaryDriveDamage(), 0, -90.0F);
        }

        int count = plan.responseCount();
        for (int index = 0; index < count; index++) {
            double offset = spreadOffset(index, count);
            Vec3 direction = rotateHorizontal(forward, offset);
            spawnDrive(player, direction, plan.responseDriveDamage(), index * 2,
                    (float) (45.0D + offset));
        }
    }

    static double spreadOffset(int index, int count) {
        if (count <= 1) {
            return 0.0D;
        }
        double center = (count - 1) * 0.5D;
        return (index - center) * FAN_ANGLE_DEGREES;
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
            double damage, int delay, float roll) {
        EntityDrive drive = Drive.doSlash(player, roll, DRIVE_LIFETIME, Vec3.ZERO,
                false, damage, DRIVE_SPEED);
        if (drive == null) {
            return;
        }
        drive.setDelay(delay);
        SoulLegacyDamageGuard.markSecondary(drive);
        Vec3 normalized = direction.normalize();
        drive.shoot(normalized.x, normalized.y, normalized.z, DRIVE_SPEED, 0.0F);
    }

    private ProgrammaticFusionHandler() {
    }
}
