package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.slasharts.Drive;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared bounded attack grammar used by generated Slash Arts.
 *
 * <p>Programmatic legacy fusion keeps the conservative Drive-only response path.
 * Player-authored forged Slash Arts reuse Resharped's native presentation language
 * wherever it can be separated safely from source damage, then layer bounded
 * phantom swords/melee hits under the forged core budget.</p>
 */
final class ProceduralSlashArtExecutor {
    static final float DRIVE_SPEED = 2.0F;
    static final int DRIVE_LIFETIME = 12;
    static final double FAN_ANGLE_DEGREES = 12.0D;
    static final double JUDGEMENT_CENTER_SHARE = 0.5D;
    private static final double JUDGEMENT_CENTER_RADIUS = 2.5D;
    private static final int JUDGEMENT_CENTER_TARGET_LIMIT = 8;
    private static final double PIERCING_REACH = 4.25D;

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
     * changes layout/timing without increasing the compiled budget.
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
                    boundedCount, damagePerHit, baseDelay);
            case NATIVE_SAKURA -> executeSakura(
                    player, state, aim, target, modifier,
                    boundedCount, damagePerHit, baseDelay);
            case NATIVE_VOID -> executeVoid(
                    player, state, aim, target, modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
            case NATIVE_CIRCLE -> executeCircle(
                    player, state, boundedCount, damagePerHit, baseDelay, modifier);
            case NATIVE_PIERCING -> executePiercing(
                    player, aim, target, boundedCount, damagePerHit);
            case DRIVE -> executeForgedDrives(
                    player, aim, technique.response(), modifier,
                    boundedCount, damagePerHit, baseDelay, angleScale);
        }
    }

    private static void executeTargetedSwords(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay) {
        ServerLevel level = player.serverLevel();
        Vec3 focus = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : player.getEyePosition().add(aim.scale(5.0D));
        double radius = modifier.condensed() ? 1.35D
                : modifier.spread() ? 3.25D : 2.15D;

        ForgedJudgementPresentation.spawn(player, focus, state.getColorCode());
        // The native Judgement Cut entity is cosmetic. Give its center one
        // server-owned hit, paid for by this phase's existing sword budget.
        double centerDamage = judgementCenterDamage(damagePerHit, count);
        AABB area = new AABB(
                focus.x - JUDGEMENT_CENTER_RADIUS,
                focus.y - JUDGEMENT_CENTER_RADIUS,
                focus.z - JUDGEMENT_CENTER_RADIUS,
                focus.x + JUDGEMENT_CENTER_RADIUS,
                focus.y + JUDGEMENT_CENTER_RADIUS,
                focus.z + JUDGEMENT_CENTER_RADIUS);
        level.getEntitiesOfClass(LivingEntity.class, area,
                        candidate -> candidate.getBoundingBox().getCenter()
                                .distanceToSqr(focus)
                                <= JUDGEMENT_CENTER_RADIUS * JUDGEMENT_CENTER_RADIUS
                                && LegacyFusionCombatSupport.canAffect(player, candidate)
                                && player.hasLineOfSight(candidate))
                .stream()
                .sorted((left, right) -> Double.compare(
                        left.distanceToSqr(focus), right.distanceToSqr(focus)))
                .limit(JUDGEMENT_CENTER_TARGET_LIMIT)
                .forEach(candidate -> LegacyFusionCombatSupport.hurtPreservingIFrames(
                        level, player, candidate, (float) centerDamage));

        for (int index = 0; index < count; index++) {
            double angle = Math.toRadians(index * (360.0D / count));
            Vec3 start = focus.add(
                    Math.cos(angle) * radius,
                    1.2D + ((index & 1) == 0 ? 0.65D : -0.15D),
                    Math.sin(angle) * radius);
            int delay = baseDelay + index * 2 + shatterDelay(modifier, index, count);
            spawnSword(level, player, start,
                    focus.subtract(start), judgementSwordDamage(damagePerHit),
                    state.getColorCode(), delay,
                    (float) (index * 360.0D / count));
        }
    }

    private static void executeSakura(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay) {
        ServerLevel level = player.serverLevel();
        Vec3 focus = target != null && target.isAlive()
                ? target.getBoundingBox().getCenter()
                : player.getEyePosition().add(aim.scale(4.0D));

        ForgedNativePresentation.spawnSakuraCross(
                player, focus, state.getColorCode(),
                modifier.spread() ? 2.15F : modifier.condensed() ? 1.45F : 1.75F);

        // The native cross is presentation-only. Compact summoned swords carry
        // the exact forged budget and preserve Shatter's delayed second burst.
        Vec3 horizontal = new Vec3(-aim.z, 0.0D, aim.x);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        double width = modifier.condensed() ? 0.85D
                : modifier.spread() ? 2.4D : 1.35D;

        for (int index = 0; index < count; index++) {
            double normalized = count <= 1 ? 0.0D
                    : index / (double) (count - 1) * 2.0D - 1.0D;
            Vec3 start = focus.add(horizontal.scale(normalized * width))
                    .add(0.0D, 1.8D + Math.abs(normalized) * 0.35D, 0.0D);
            int delay = baseDelay + shatterDelay(modifier, index, count);
            spawnSword(level, player, start,
                    focus.subtract(start), damagePerHit,
                    state.getColorCode(), delay,
                    index % 2 == 0 ? 22.5F : 157.5F);
        }
    }

    private static void executeVoid(ServerPlayer player,
            ISlashBladeState state, Vec3 aim, LivingEntity target,
            ForgedSlashArtPlan.Modifier modifier, int count,
            double damagePerHit, int baseDelay, double angleScale) {
        ServerLevel level = player.serverLevel();
        ForgedNativePresentation.spawnVoid(player, state.getColorCode());

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
            spawnSword(level, player, start, direction,
                    damagePerHit, state.getColorCode(), delay,
                    (float) (normalized * 35.0D));
        }
    }

    private static void executeCircle(ServerPlayer player,
            ISlashBladeState state, int count, double damagePerHit,
            int baseDelay, ForgedSlashArtPlan.Modifier modifier) {
        ServerLevel level = player.serverLevel();
        ForgedNativePresentation.spawnCircle(
                player, state.getColorCode(),
                modifier.spread() ? 2.0F : modifier.condensed() ? 1.35F : 1.65F);

        // Replace the old radial Drive ring with phantom swords travelling along
        // the same 360-degree topology. SlashEffect is now the dominant native cue.
        for (int index = 0; index < count; index++) {
            double yaw = index * (360.0D / count);
            Vec3 direction = rotateHorizontal(player.getLookAngle(), yaw);
            Vec3 start = player.getEyePosition()
                    .add(direction.scale(0.55D))
                    .add(0.0D, -0.15D, 0.0D);
            int delay = baseDelay + index + shatterDelay(modifier, index, count);
            spawnSword(level, player, start, direction,
                    damagePerHit, state.getColorCode(), delay,
                    (float) yaw);
        }
    }

    private static void executePiercing(ServerPlayer player, Vec3 aim,
            LivingEntity lockedTarget, int count, double damagePerHit) {
        // Resharped PIERCING_2: forward rush for the opening ticks plus a close
        // area attack. Reuse that movement/sound language but keep damage exact.
        player.moveRelative(player.isInWater() ? 0.35F : 0.8F,
                new Vec3(0.0D, 0.0D, 1.0D));
        player.hasImpulse = true;
        AttackManager.playPiercingSoundAction(player);

        LivingEntity target = validPiercingTarget(player, lockedTarget)
                ? lockedTarget : findPiercingTarget(player, aim);
        if (target == null) {
            return;
        }
        for (int index = 0; index < count; index++) {
            LegacyFusionCombatSupport.hurtPreservingIFrames(
                    player.serverLevel(), player, target,
                    (float) Math.max(0.0D, damagePerHit));
        }
    }

    private static boolean validPiercingTarget(
            ServerPlayer player, LivingEntity target) {
        return target != null
                && LegacyFusionCombatSupport.canAffect(player, target)
                && player.hasLineOfSight(target)
                && player.distanceToSqr(target) <= PIERCING_REACH * PIERCING_REACH;
    }

    private static LivingEntity findPiercingTarget(
            ServerPlayer player, Vec3 aim) {
        AABB search = player.getBoundingBox()
                .expandTowards(aim.scale(PIERCING_REACH))
                .inflate(0.9D);
        return player.serverLevel().getEntitiesOfClass(
                        LivingEntity.class, search,
                        target -> validPiercingTarget(player, target))
                .stream()
                .min((first, second) -> Double.compare(
                        player.distanceToSqr(first),
                        player.distanceToSqr(second)))
                .orElse(null);
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

    static double judgementCenterDamage(double damagePerHit, int count) {
        return Math.max(0.0D, damagePerHit) * Math.max(0, count)
                * JUDGEMENT_CENTER_SHARE;
    }

    static double judgementSwordDamage(double damagePerHit) {
        return Math.max(0.0D, damagePerHit)
                * (1.0D - JUDGEMENT_CENTER_SHARE);
    }

    private static void spawnSword(ServerLevel level, ServerPlayer player,
            Vec3 start, Vec3 direction,
            double damage, int color, int delay, float roll) {
        Vec3 normalized = safeDirection(direction, player.getLookAngle());
        ForgedSummonedSword sword = new ForgedSummonedSword(level, damage);
        sword.setPos(start.x, start.y, start.z);
        sword.setOwner(player);
        sword.setShooter(player);
        // setHitEntity is an attachment state, not a homing target: pre-setting
        // it skips SlashBlade's collision path and makes the sword burst without
        // ever delivering its on-hit damage.
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
