package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared bounded attack grammar used by generated Slash Arts.
 *
 * <p>Programmatic legacy fusion keeps the conservative Drive-only response path.
 * Player-authored forged Slash Arts use a richer semantic layer: native-looking
 * drives, converging summoned swords, fan swords and localized cross cuts. Source
 * SlashArts are never invoked here; callers own timing and total damage budget.</p>
 */
final class ProceduralSlashArtExecutor {
    static final float DRIVE_SPEED = 2.0F;
    static final int DRIVE_LIFETIME = 12;
    static final double FAN_ANGLE_DEGREES = 12.0D;

    /**
     * Conservative semantic fallback retained for programmatic legacy fusion.
     */
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

    /**
     * Rich authored path. Technique chooses the attack primitive while modifier
     * changes how that primitive is laid out without increasing the compiled budget.
     */
    static void executeForged(ServerPlayer player, ISlashBladeState state,
            Vec3 forward, LivingEntity target,
            ForgedSlashArtPlan.Technique technique,
            ForgedSlashArtPlan.Modifier modifier,
            int count, double damagePerHit, int baseDelay, double angleScale) {
        int boundedCount = Math.max(1, Math.min(12, count));
        Vec3 aim = safeDirection(forward, player.getLookAngle());

        if (modifier.condensed() && target != null && target.isAlive()) {
            Vec3 towardTarget = target.getBoundingBox().getCenter()
                    .subtract(player.getEyePosition());
            if (towardTarget.lengthSqr() > 1.0E-8D) {
                aim = towardTarget.normalize();
            }
        }

        switch (technique.primitive()) {
            case TARGETED_SWORDS -> executeTargetedSwords(
                    player, state, aim, target, modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
            case CROSS_SLASH -> executeCrossSlash(
                    player, state, aim, target, modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
            case SUMMONED_FAN -> executeSummonedFan(
                    player, state, aim, target, modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
            case RADIAL_DRIVE -> executeRadialDrives(
                    player, modifier, boundedCount, damagePerHit,
                    baseDelay, angleScale);
            case DRIVE -> executeForgedDrives(
                    player, aim, technique.response(), modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
        }
    }

    private static void executeTargetedSwords(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        ServerLevel level = player.serverLevel();
        Vec3 focus = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : player.getEyePosition().add(aim.scale(5.0D));
        double radius = modifier.condensed() ? 1.35D
                : modifier.spread() ? 3.25D : 2.15D;

        // Judgement's recognizable local-space cue remains cosmetic; the
        // converging swords own the bounded damage.
        for (int index = 0; index < 3; index++) {
            LegacyFusionCombatSupport.spawnVisualSlash(
                    player, focus, player.getYRot() + index * 120.0F,
                    30.0F, state.getColorCode(), 1.55F, 6);
        }

        for (int index = 0; index < count; index++) {
            double angle = Math.toRadians(index * (360.0D / count));
            Vec3 start = focus.add(
                    Math.cos(angle) * radius,
                    1.2D + ((index & 1) == 0 ? 0.65D : -0.15D),
                    Math.sin(angle) * radius);
            int delay = baseDelay + index * 2 + shatterDelay(modifier, index, count);
            spawnSword(level, player, target, start,
                    focus.subtract(start), damagePerHit,
                    state.getColorCode(), delay,
                    (float) (index * 360.0D / count));
        }
    }

    private static void executeCrossSlash(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        ServerLevel level = player.serverLevel();
        Vec3 focus = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : player.getEyePosition().add(aim.scale(4.0D));

        LegacyFusionCombatSupport.spawnVisualSlash(player, focus,
                player.getYRot() - 22.0F, 58.0F,
                state.getColorCode(), modifier.spread() ? 2.2F : 1.75F, 8);
        LegacyFusionCombatSupport.spawnVisualSlash(player, focus,
                player.getYRot() + 22.0F, 122.0F,
                state.getColorCode(), modifier.spread() ? 2.2F : 1.75F, 8);

        Vec3 horizontal = new Vec3(-aim.z, 0.0D, aim.x);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        double width = modifier.condensed() ? 1.0D
                : modifier.spread() ? 2.8D : 1.65D;

        for (int index = 0; index < count; index++) {
            double normalized = count <= 1 ? 0.0D
                    : index / (double) (count - 1) * 2.0D - 1.0D;
            Vec3 start = focus.add(horizontal.scale(normalized * width))
                    .add(0.0D, 2.15D + Math.abs(normalized) * 0.45D, 0.0D);
            int delay = baseDelay + shatterDelay(modifier, index, count);
            spawnSword(level, player, target, start,
                    focus.subtract(start), damagePerHit,
                    state.getColorCode(), delay,
                    index % 2 == 0 ? 45.0F : 135.0F);
        }
    }

    private static void executeSummonedFan(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        ServerLevel level = player.serverLevel();
        Vec3 right = new Vec3(-aim.z, 0.0D, aim.x);
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }

        double width = (modifier.spread() ? 2.4D : modifier.condensed() ? 0.7D : 1.3D)
                * Math.max(0.5D, Math.min(2.0D, angleScale));
        Vec3 targetCenter = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter() : null;

        for (int index = 0; index < count; index++) {
            double normalized = count <= 1 ? 0.0D
                    : index / (double) (count - 1) * 2.0D - 1.0D;
            Vec3 start = player.getEyePosition()
                    .add(right.scale(normalized * width))
                    .add(0.0D, 0.55D + (1.0D - Math.abs(normalized)) * 0.65D, 0.0D);
            Vec3 direction = targetCenter != null
                    ? targetCenter.subtract(start)
                    : rotateHorizontal(aim, normalized * 14.0D * angleScale);
            int delay = baseDelay + index + shatterDelay(modifier, index, count);
            spawnSword(level, player, target, start, direction,
                    damagePerHit, state.getColorCode(), delay,
                    (float) (normalized * 35.0D));
        }
    }

    private static void executeRadialDrives(ServerPlayer player,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        double scale = Math.max(0.45D, Math.min(2.4D, angleScale));
        for (int index = 0; index < count; index++) {
            double yaw = index * (360.0D / count);
            Vec3 direction = rotateHorizontal(player.getLookAngle(), yaw * scale);
            int delay = baseDelay + shatterDelay(modifier, index, count);
            spawnDrive(player, direction, damagePerHit, delay,
                    index * (360.0F / count), 1.60F);
        }
    }

    private static void executeForgedDrives(ServerPlayer player, Vec3 aim,
            ProgrammaticFusionProfile.Response response,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        for (int index = 0; index < count; index++) {
            Vec3 direction = rotateHorizontal(
                    aim, responseYaw(response, index, count, angleScale));
            int delay = baseDelay + responseDelay(response, index)
                    + shatterDelay(modifier, index, count);
            spawnDrive(player, direction, damagePerHit, delay,
                    responseRoll(response, index, count),
                    responseSpeed(response, index));
        }
    }

    private static int shatterDelay(ForgedSlashArtPlan.Modifier modifier,
            int index, int count) {
        if (!modifier.shatter() || count < 2) {
            return 0;
        }
        return index >= (count + 1) / 2 ? 3 : 0;
    }

    private static void spawnSword(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 start, Vec3 direction,
            double damage, int color, int delay, float roll) {
        Vec3 normalized = safeDirection(direction, player.getLookAngle());
        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, level);
        sword.setPos(start.x, start.y, start.z);
        sword.setOwner(player);
        sword.setShooter(player);
        if (target != null && target.isAlive()) {
            sword.setHitEntity(target);
        }
        sword.setDamage(Math.max(0.0D, damage));
        sword.setColor(color);
        sword.setRoll(roll);
        sword.setDelay(Math.max(0, delay));
        sword.setNoClip(true);
        SoulLegacyDamageGuard.markSecondary(sword);
        sword.shoot(normalized.x, normalized.y, normalized.z, 1.9F, 0.0F);
        level.addFreshEntity(sword);
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
