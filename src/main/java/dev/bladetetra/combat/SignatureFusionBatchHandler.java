package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime behavior for the remaining SA/SE-signature fusion batch.
 *
 * <ul>
 *   <li>Tagayasan Drive Vertical + Koseki Wither Edge -> Withered Drive.</li>
 *   <li>Black Fox Piercing + Sange Void Slash -> Piercing Void Moon.</li>
 * </ul>
 *
 * Tsukumo Cross is intentionally kept in {@link TsukumoCrossNativeHandler}
 * because its two damaging stages are authored by SlashBlade's native SA
 * projectiles instead of custom corridor damage.
 */
final class SignatureFusionBatchHandler {
    static final long VOID_CLOSE_DELAY = 6L;

    static final double WITHERED_DRIVE_DAMAGE = 1.50D;
    static final int WITHERED_DURATION_TICKS = 100;
    static final int WITHERED_AMPLIFIER = 1;
    private static final double PIERCING_MAX_DASH = 4.0D;
    private static final int VOID_COLOR = 0xC8A7FF;
    private static final int FOX_COLOR = 0x4D4A59;

    private static final List<VoidClosureCast> VOID_CLOSURES = new ArrayList<>();

    static void onWitheredDrive(ServerPlayer player, ItemStack blade) {
        if (LegacyFusion.active(blade) != LegacyFusion.TAGAYASAN_SAYA_KOSEKI_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // Match SlashBlade's native Drive Vertical parameters. EntityDrive owns
        // targeting, damage scaling, i-frames and enchantment callbacks; the
        // fusion only adds Koseki's Wither Edge signature to successful hits.
        EntityDrive drive = Drive.doSlash(player, -90.0F, 10, Vec3.ZERO,
                false, WITHERED_DRIVE_DAMAGE, 2.0F);
        if (drive != null) {
            drive.getPotionEffects().add(new MobEffectInstance(MobEffects.WITHER,
                    WITHERED_DURATION_TICKS, WITHERED_AMPLIFIER));
        }

        level.sendParticles(ParticleTypes.ASH,
                player.getX(), player.getY() + 0.9D, player.getZ(),
                10, 0.34D, 0.42D, 0.34D, 0.018D);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.64F, 0.78F);
    }

    static void onPiercingVoidMoon(ServerPlayer player, ItemStack blade) {
        if (LegacyFusion.active(blade) != LegacyFusion.BLACK_SAYA_SANGE_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 direction = horizontalDirection(player);
        Vec3 startFeet = player.position();
        double dashDistance = safeDashDistance(level, player, direction,
                PIERCING_MAX_DASH);
        Vec3 endFeet = startFeet.add(direction.scale(dashDistance));
        Vec3 attackStart = startFeet.add(0.0D, 0.85D, 0.0D);
        Vec3 attackEnd = endFeet.add(0.0D, 0.85D, 0.0D);
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float firstDamage = SignatureFusionBalance.piercingHit(attack);

        Set<UUID> hitTargets = new HashSet<>();
        double strikeLength = Math.max(1.25D, dashDistance + 0.85D);
        for (LivingEntity target : targetsInCorridor(level, player, attackStart,
                direction, strikeLength, 0.95D, 1.85D)) {
            if (LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, firstDamage)) {
                hitTargets.add(target.getUUID());
                Vec3 impact = target.getBoundingBox().getCenter();
                LegacyFusionCombatSupport.spawnVisualSlash(player, impact,
                        player.getYRot(), 0.0F, FOX_COLOR, 0.82F, 5);
                level.sendParticles(ParticleTypes.CRIT,
                        impact.x, impact.y, impact.z, 5,
                        0.22D, 0.22D, 0.22D, 0.035D);
            }
        }

        renderPiercingPath(player, attackStart, direction,
                Math.max(1.0D, dashDistance));
        if (dashDistance > 0.05D) {
            player.teleportTo(endFeet.x, endFeet.y, endFeet.z);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.48F, 1.48F);

        VOID_CLOSURES.removeIf(cast -> cast.playerId.equals(player.getUUID()));
        VOID_CLOSURES.add(new VoidClosureCast(level.dimension(), player.getUUID(),
                attackStart, attackEnd, attack,
                level.getGameTime() + VOID_CLOSE_DELAY, hitTargets));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        tickVoidClosures(event);
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        VOID_CLOSURES.removeIf(cast -> cast.dimension.equals(dimension));
    }

    static void clear() {
        VOID_CLOSURES.clear();
    }

    private static void tickVoidClosures(TickEvent.ServerTickEvent event) {
        Iterator<VoidClosureCast> iterator = VOID_CLOSURES.iterator();
        while (iterator.hasNext()) {
            VoidClosureCast cast = iterator.next();
            ServerLevel level = event.getServer().getLevel(cast.dimension);
            if (level == null || level.getGameTime() < cast.closeTick) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(cast.playerId);
            if (player == null || player.level() != level || !player.isAlive()
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.BLACK_SAYA_SANGE_HILT) {
                continue;
            }

            Vec3 direction = cast.end.subtract(cast.start);
            double length = direction.length();
            if (length > 0.001D) {
                direction = direction.scale(1.0D / length);
            } else {
                direction = horizontalDirection(player);
                length = 1.0D;
            }
            for (int i = 1; i <= 3; i++) {
                Vec3 point = cast.start.add(direction.scale(length * i / 4.0D));
                LegacyFusionCombatSupport.spawnVisualSlash(player, point,
                        player.getYRot(), 90.0F, VOID_COLOR, 0.95F, 6);
                level.sendParticles(ParticleTypes.PORTAL,
                        point.x, point.y, point.z, 4,
                        0.18D, 0.28D, 0.18D, 0.025D);
            }

            float damage = SignatureFusionBalance.voidClosure(cast.attackSnapshot);
            for (UUID id : cast.targets) {
                Entity entity = level.getEntity(id);
                if (!(entity instanceof LivingEntity target)
                        || !validDelayedTarget(player, target, 14.0D)
                        || distanceToSegment(target.getBoundingBox().getCenter(),
                        cast.start, cast.end) > 2.8D) {
                    continue;
                }
                if (!LegacyFusionCombatSupport.hurtPreservingIFrames(
                        level, player, target, damage)) {
                    continue;
                }
                Vec3 center = target.getBoundingBox().getCenter();
                LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                        player.getYRot(), 90.0F, VOID_COLOR, 1.38F, 8);
                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                        center.x, center.y, center.z, 5,
                        0.32D, 0.34D, 0.32D, 0.018D);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.72F, 1.32F);
        }
    }

    private static List<LivingEntity> targetsInCorridor(ServerLevel level,
            ServerPlayer player, Vec3 start, Vec3 direction, double length,
            double halfWidth, double halfHeight) {
        AABB search = player.getBoundingBox().expandTowards(
                direction.scale(length)).inflate(halfWidth, halfHeight, halfWidth);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                search, entity -> LegacyFusionCombatSupport.canAffect(player, entity)
                        && player.hasLineOfSight(entity))) {
            Vec3 center = target.getBoundingBox().getCenter();
            double projection = center.subtract(start).dot(direction);
            if (projection < -0.35D || projection > length + 0.75D) {
                continue;
            }
            Vec3 nearest = start.add(direction.scale(
                    Math.max(0.0D, Math.min(length, projection))));
            Vec3 offset = center.subtract(nearest);
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (horizontal <= halfWidth
                    && Math.abs(offset.y) <= halfHeight) {
                result.add(target);
            }
        }
        return result;
    }

    private static boolean validDelayedTarget(ServerPlayer player,
            LivingEntity target, double range) {
        return LegacyFusionCombatSupport.canAffect(player, target)
                && player.distanceToSqr(target) <= range * range
                && player.hasLineOfSight(target);
    }

    private static Vec3 horizontalDirection(ServerPlayer player) {
        Vec3 direction = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-5D) {
            double yaw = Math.toRadians(player.getYRot());
            direction = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return direction.normalize();
    }

    private static double safeDashDistance(ServerLevel level,
            ServerPlayer player, Vec3 direction, double maximum) {
        double safe = 0.0D;
        for (double distance = 0.25D; distance <= maximum + 1.0E-6D;
                distance += 0.25D) {
            AABB moved = player.getBoundingBox().move(direction.scale(distance));
            if (!level.noCollision(player, moved)) {
                break;
            }
            safe = distance;
        }
        return safe;
    }

    private static void renderPiercingPath(ServerPlayer player,
            Vec3 start, Vec3 direction, double length) {
        ServerLevel level = player.serverLevel();
        for (int i = 1; i <= 3; i++) {
            Vec3 point = start.add(direction.scale(length * i / 4.0D));
            LegacyFusionCombatSupport.spawnVisualSlash(player, point,
                    player.getYRot(), 0.0F, FOX_COLOR, 0.72F, 4);
            level.sendParticles(ParticleTypes.PORTAL,
                    point.x, point.y, point.z, 2,
                    0.10D, 0.16D, 0.10D, 0.02D);
        }
    }

    static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr <= 1.0E-8D) {
            return point.distanceTo(start);
        }
        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceTo(start.add(segment.scale(t)));
    }

    private static final class VoidClosureCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final Vec3 start;
        private final Vec3 end;
        private final double attackSnapshot;
        private final long closeTick;
        private final Set<UUID> targets;

        private VoidClosureCast(ResourceKey<Level> dimension, UUID playerId,
                Vec3 start, Vec3 end, double attackSnapshot,
                long closeTick, Set<UUID> targets) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.start = start;
            this.end = end;
            this.attackSnapshot = attackSnapshot;
            this.closeTick = closeTick;
            this.targets = Set.copyOf(targets);
        }
    }

    private SignatureFusionBatchHandler() {
    }
}
