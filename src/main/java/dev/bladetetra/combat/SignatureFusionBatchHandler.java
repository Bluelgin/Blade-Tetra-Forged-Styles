package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Three deliberately compact fusions derived from the source blades' native
 * Slash-Art language instead of inventing unrelated effects:
 *
 * <ul>
 *   <li>Agito Wave Edge + Tsukumo Drive Horizontal -> Tsukumo Cross.</li>
 *   <li>Tagayasan Drive Vertical + Koseki Wither Edge -> Withered Drive.</li>
 *   <li>Black Fox Piercing + Sange Void Slash -> Piercing Void Moon.</li>
 * </ul>
 *
 * All authored damage is server-authoritative. SlashBlade slash entities are
 * cosmetic only and the delayed stages never retarget to unrelated entities.
 */
final class SignatureFusionBatchHandler {
    static final long TSUKUMO_VERTICAL_DELAY = 2L;
    static final long TSUKUMO_HORIZONTAL_DELAY = 6L;
    static final long TSUKUMO_CLOSE_DELAY = 9L;
    static final long WITHERED_FIRST_DELAY = 4L;
    static final long WITHERED_SECOND_DELAY = 12L;
    static final long VOID_CLOSE_DELAY = 6L;

    private static final double TSUKUMO_WAVE_RANGE = 8.0D;
    private static final double TSUKUMO_SWEEP_HALF_WIDTH = 3.2D;
    private static final double WITHERED_RANGE = 7.5D;
    private static final double PIERCING_MAX_DASH = 4.0D;
    private static final int TSUKUMO_WAVE_COLOR = 0xB7C9B2;
    private static final int TSUKUMO_DRIVE_COLOR = 0xE7D7B2;
    private static final int WITHERED_FIRST_COLOR = 0xD6C6A5;
    private static final int WITHERED_SECOND_COLOR = 0x4A444D;
    private static final int VOID_COLOR = 0xC8A7FF;
    private static final int FOX_COLOR = 0x4D4A59;

    private static final List<TsukumoCrossCast> TSUKUMO_CROSSES = new ArrayList<>();
    private static final List<WitheredDriveCast> WITHERED_DRIVES = new ArrayList<>();
    private static final List<VoidClosureCast> VOID_CLOSURES = new ArrayList<>();

    static void onTsukumoCross(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.AGITO_SAYA_TUKUMO_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position().add(0.0D, 0.85D, 0.0D);
        Vec3 direction = horizontalDirection(player);
        Vec3 center = acquireLockedCenter(player, state, 10.0D);
        if (center == null) {
            center = origin.add(direction.scale(5.4D));
        }
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        long now = level.getGameTime();

        TSUKUMO_CROSSES.removeIf(cast -> cast.playerId.equals(player.getUUID()));
        TSUKUMO_CROSSES.add(new TsukumoCrossCast(level.dimension(),
                player.getUUID(), origin, direction, center, attack,
                now + TSUKUMO_VERTICAL_DELAY,
                now + TSUKUMO_HORIZONTAL_DELAY,
                now + TSUKUMO_CLOSE_DELAY));

        level.sendParticles(ParticleTypes.ENCHANT,
                origin.x, origin.y, origin.z, 8,
                0.24D, 0.30D, 0.24D, 0.01D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.45F, 1.15F);
    }

    static void onWitheredDrive(ServerPlayer player, ItemStack blade) {
        if (LegacyFusion.active(blade) != LegacyFusion.TAGAYASAN_SAYA_KOSEKI_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position().add(0.0D, 0.80D, 0.0D);
        Vec3 direction = horizontalDirection(player);
        double attack = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        long now = level.getGameTime();

        WITHERED_DRIVES.removeIf(cast -> cast.playerId.equals(player.getUUID()));
        WITHERED_DRIVES.add(new WitheredDriveCast(level.dimension(),
                player.getUUID(), origin, direction, attack,
                now + WITHERED_FIRST_DELAY, now + WITHERED_SECOND_DELAY));

        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.15D, player.getZ(),
                7, 0.25D, 0.05D, 0.25D, 0.012D);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_HIT,
                SoundSource.PLAYERS, 0.44F, 0.82F);
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
        tickTsukumo(event);
        tickWithered(event);
        tickVoidClosures(event);
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        TSUKUMO_CROSSES.removeIf(cast -> cast.dimension.equals(dimension));
        WITHERED_DRIVES.removeIf(cast -> cast.dimension.equals(dimension));
        VOID_CLOSURES.removeIf(cast -> cast.dimension.equals(dimension));
    }

    static void clear() {
        TSUKUMO_CROSSES.clear();
        WITHERED_DRIVES.clear();
        VOID_CLOSURES.clear();
    }

    private static void tickTsukumo(TickEvent.ServerTickEvent event) {
        Iterator<TsukumoCrossCast> iterator = TSUKUMO_CROSSES.iterator();
        while (iterator.hasNext()) {
            TsukumoCrossCast cast = iterator.next();
            ServerLevel level = event.getServer().getLevel(cast.dimension);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(cast.playerId);
            if (level == null || player == null || player.level() != level
                    || !player.isAlive()
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.AGITO_SAYA_TUKUMO_HILT) {
                iterator.remove();
                continue;
            }
            long now = level.getGameTime();
            if (cast.stage == 0 && now >= cast.verticalTick) {
                performTsukumoVertical(level, player, cast);
                cast.stage = 1;
            }
            if (cast.stage == 1 && now >= cast.horizontalTick) {
                performTsukumoHorizontal(level, player, cast);
                cast.stage = 2;
            }
            if (cast.stage == 2 && now >= cast.closeTick) {
                performTsukumoClose(level, player, cast);
                iterator.remove();
            }
        }
    }

    private static void performTsukumoVertical(ServerLevel level,
            ServerPlayer player, TsukumoCrossCast cast) {
        float damage = SignatureFusionBalance.tsukumoVertical(cast.attackSnapshot);
        for (LivingEntity target : targetsInCorridor(level, player, cast.origin,
                cast.direction, TSUKUMO_WAVE_RANGE, 1.10D, 2.2D)) {
            if (LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, damage)) {
                cast.verticalHits.add(target.getUUID());
            }
        }
        for (int i = 1; i <= 3; i++) {
            Vec3 point = cast.origin.add(cast.direction.scale(i * 2.25D));
            LegacyFusionCombatSupport.spawnVisualSlash(player, point,
                    player.getYRot(), 90.0F, TSUKUMO_WAVE_COLOR,
                    1.12F + i * 0.08F, 6);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.62F, 0.96F);
    }

    private static void performTsukumoHorizontal(ServerLevel level,
            ServerPlayer player, TsukumoCrossCast cast) {
        float damage = SignatureFusionBalance.tsukumoHorizontal(cast.attackSnapshot);
        for (LivingEntity target : targetsInSweep(level, player, cast.center,
                cast.direction, TSUKUMO_SWEEP_HALF_WIDTH, 1.25D, 2.3D)) {
            if (LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, damage)
                    && cast.verticalHits.contains(target.getUUID())) {
                cast.crossHits.add(target.getUUID());
            }
        }
        LegacyFusionCombatSupport.spawnVisualSlash(player, cast.center,
                player.getYRot(), 0.0F, TSUKUMO_DRIVE_COLOR, 2.05F, 8);
        level.sendParticles(ParticleTypes.ENCHANT,
                cast.center.x, cast.center.y, cast.center.z,
                11, 1.15D, 0.45D, 1.15D, 0.025D);
        level.playSound(null, cast.center.x, cast.center.y, cast.center.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                0.74F, 1.18F);
    }

    private static void performTsukumoClose(ServerLevel level,
            ServerPlayer player, TsukumoCrossCast cast) {
        float damage = SignatureFusionBalance.tsukumoCrossClose(cast.attackSnapshot);
        for (UUID id : cast.crossHits) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity target)
                    || !validDelayedTarget(player, target, 12.0D)) {
                continue;
            }
            if (!LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, damage)) {
                continue;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    player.getYRot(), 90.0F, 0xF2EADB, 1.25F, 6);
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    player.getYRot(), 0.0F, 0xF2EADB, 1.25F, 6);
            level.sendParticles(ParticleTypes.CRIT,
                    center.x, center.y, center.z, 9,
                    0.38D, 0.38D, 0.38D, 0.06D);
        }
        if (!cast.crossHits.isEmpty()) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.72F, 1.45F);
        }
    }

    private static void tickWithered(TickEvent.ServerTickEvent event) {
        Iterator<WitheredDriveCast> iterator = WITHERED_DRIVES.iterator();
        while (iterator.hasNext()) {
            WitheredDriveCast cast = iterator.next();
            ServerLevel level = event.getServer().getLevel(cast.dimension);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(cast.playerId);
            if (level == null || player == null || player.level() != level
                    || !player.isAlive()
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.TAGAYASAN_SAYA_KOSEKI_HILT) {
                iterator.remove();
                continue;
            }
            long now = level.getGameTime();
            if (cast.stage == 0 && now >= cast.firstTick) {
                performWitheredFirst(level, player, cast);
                cast.stage = 1;
            }
            if (cast.stage == 1 && now >= cast.secondTick) {
                performWitheredSecond(level, player, cast);
                iterator.remove();
            }
        }
    }

    private static void performWitheredFirst(ServerLevel level,
            ServerPlayer player, WitheredDriveCast cast) {
        float damage = SignatureFusionBalance.witheredFirstDrive(cast.attackSnapshot);
        for (LivingEntity target : targetsInCorridor(level, player, cast.origin,
                cast.direction, WITHERED_RANGE, 1.20D, 2.35D)) {
            if (LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, damage)) {
                cast.firstHits.add(target.getUUID());
                target.knockback(0.32D, -cast.direction.x, -cast.direction.z);
            }
        }
        for (int i = 1; i <= 3; i++) {
            Vec3 point = cast.origin.add(cast.direction.scale(i * 2.15D));
            LegacyFusionCombatSupport.spawnVisualSlash(player, point,
                    player.getYRot(), 90.0F, WITHERED_FIRST_COLOR,
                    1.28F + i * 0.08F, 7);
            level.sendParticles(ParticleTypes.CLOUD,
                    point.x, point.y - 0.45D, point.z, 3,
                    0.35D, 0.06D, 0.35D, 0.012D);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.58F, 0.78F);
    }

    private static void performWitheredSecond(ServerLevel level,
            ServerPlayer player, WitheredDriveCast cast) {
        float damage = SignatureFusionBalance.witheredSecondDrive(cast.attackSnapshot);
        Vec3 pathEnd = cast.origin.add(cast.direction.scale(WITHERED_RANGE));
        for (UUID id : cast.firstHits) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity target)
                    || !validDelayedTarget(player, target, 12.0D)
                    || distanceToSegment(target.getBoundingBox().getCenter(),
                    cast.origin, pathEnd) > 2.35D) {
                continue;
            }
            if (!LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, damage)) {
                continue;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    player.getYRot(), 90.0F, WITHERED_SECOND_COLOR, 1.48F, 8);
            level.sendParticles(ParticleTypes.ASH,
                    center.x, center.y, center.z, 12,
                    0.45D, 0.55D, 0.45D, 0.015D);
            level.sendParticles(ParticleTypes.SMOKE,
                    center.x, center.y, center.z, 5,
                    0.30D, 0.38D, 0.30D, 0.018D);
        }
        if (!cast.firstHits.isEmpty()) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.60F, 0.72F);
        }
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

    private static Vec3 acquireLockedCenter(ServerPlayer player,
            ISlashBladeState state, double range) {
        Entity locked = state.getTargetEntity(player.level());
        if (!(locked instanceof LivingEntity target)
                || !validDelayedTarget(player, target, range)) {
            return null;
        }
        return target.getBoundingBox().getCenter();
    }

    private static List<LivingEntity> targetsInCorridor(ServerLevel level,
            ServerPlayer player, Vec3 start, Vec3 direction, double length,
            double halfWidth, double halfHeight) {
        Vec3 end = start.add(direction.scale(length));
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

    private static List<LivingEntity> targetsInSweep(ServerLevel level,
            ServerPlayer player, Vec3 center, Vec3 forward,
            double halfWidth, double halfDepth, double halfHeight) {
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        AABB search = AABB.ofSize(center,
                halfWidth * 2.0D + 1.0D,
                halfHeight * 2.0D,
                halfWidth * 2.0D + 1.0D);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                search, entity -> LegacyFusionCombatSupport.canAffect(player, entity)
                        && player.hasLineOfSight(entity))) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(center);
            if (Math.abs(delta.dot(right)) <= halfWidth
                    && Math.abs(delta.dot(forward)) <= halfDepth
                    && Math.abs(delta.y) <= halfHeight) {
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

    private static final class TsukumoCrossCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final Vec3 origin;
        private final Vec3 direction;
        private final Vec3 center;
        private final double attackSnapshot;
        private final long verticalTick;
        private final long horizontalTick;
        private final long closeTick;
        private final Set<UUID> verticalHits = new HashSet<>();
        private final Set<UUID> crossHits = new HashSet<>();
        private int stage;

        private TsukumoCrossCast(ResourceKey<Level> dimension, UUID playerId,
                Vec3 origin, Vec3 direction, Vec3 center,
                double attackSnapshot, long verticalTick,
                long horizontalTick, long closeTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.origin = origin;
            this.direction = direction;
            this.center = center;
            this.attackSnapshot = attackSnapshot;
            this.verticalTick = verticalTick;
            this.horizontalTick = horizontalTick;
            this.closeTick = closeTick;
        }
    }

    private static final class WitheredDriveCast {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final Vec3 origin;
        private final Vec3 direction;
        private final double attackSnapshot;
        private final long firstTick;
        private final long secondTick;
        private final Set<UUID> firstHits = new HashSet<>();
        private int stage;

        private WitheredDriveCast(ResourceKey<Level> dimension, UUID playerId,
                Vec3 origin, Vec3 direction, double attackSnapshot,
                long firstTick, long secondTick) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.origin = origin;
            this.direction = direction;
            this.attackSnapshot = attackSnapshot;
            this.firstTick = firstTick;
            this.secondTick = secondTick;
        }
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
