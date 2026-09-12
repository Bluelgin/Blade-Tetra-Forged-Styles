package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.registry.ModSlashBladeAbilities;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Runtime implementation of the ordered black/white fox legacy fusions. */
final class TwinFoxFusionHandler {
    private static final String PURSUIT_TARGET = "blade_tetra_twin_fox_pursuit_target";
    private static final String PURSUIT_DIRECTION_X = "blade_tetra_twin_fox_pursuit_direction_x";
    private static final String PURSUIT_DIRECTION_Z = "blade_tetra_twin_fox_pursuit_direction_z";
    private static final String LEGACY_PURSUIT_COMBO = "blade_tetra_twin_fox_pursuit_combo";
    private static final String PURSUIT_EXPIRES = "blade_tetra_twin_fox_pursuit_expires";
    private static final String PURSUIT_COOLDOWN = "blade_tetra_twin_fox_pursuit_cooldown";

    private static final int HUNT_DURATION_TICKS = 20;
    private static final int PURSUIT_MARK_TICKS = 60;
    private static final int PURSUIT_DELAY_TICKS = 5;
    private static final int PURSUIT_COOLDOWN_TICKS = 30;

    private static final List<PendingHunt> PENDING_HUNTS = new ArrayList<>();
    private static final List<PendingPursuit> PENDING_PURSUITS = new ArrayList<>();

    static void onSlashArt(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (!ModSlashBladeAbilities.TWIN_FOX_PIERCING.getId().equals(
                state.getSlashArtsKey())
                || LegacyFusion.active(blade) != LegacyFusion.BLACK_SAYA_WHITE_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = acquireTarget(player, state);
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(0.9D));
        Vec3 aim = target == null
                ? player.pick(18.0D, 0.0F, false).getLocation()
                : target.getBoundingBox().getCenter();

        // Snapshot damage on cast so later equipment changes cannot alter this SA.
        double perFoxDamage = fusionDamage(player, 0.30D, 8.0D);
        double finisherDamage = fusionDamage(player, 0.75D, 20.0D);
        PENDING_HUNTS.add(new PendingHunt(level.dimension(), player.getUUID(),
                target == null ? null : target.getUUID(), start, aim,
                level.getGameTime(), player.getYRot(),
                perFoxDamage, finisherDamage));
        sendHuntVfx(level, player, target, start, aim,
                BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT,
                HUNT_DURATION_TICKS, 1.0F);
        foxDust(level, start, false, 26, 0.48D);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.48F, 1.65F);
    }

    static void onBladeHit(SlashBladeEvent.HitEvent event) {
        LivingEntity target = event.getTarget();
        if (!(event.getUser() instanceof ServerPlayer player)
                || !LegacyFusionCombatSupport.canAffect(player, target)) {
            return;
        }
        ItemStack blade = event.getBlade();
        if (LegacyFusion.active(blade) != LegacyFusion.WHITE_SAYA_BLACK_HILT
                || !event.getSlashBladeState().hasSpecialEffect(
                ModSlashBladeAbilities.TWIN_FOX_REFLECTION.getId())) {
            return;
        }
        long now = player.level().getGameTime();
        CompoundTag tag = blade.getOrCreateTag();
        if (tag.getLong(PURSUIT_COOLDOWN) > now) {
            return;
        }
        String targetId = target.getUUID().toString();
        Vec3 direction = pursuitDirection(player, target);
        boolean matchingMark = targetId.equals(tag.getString(PURSUIT_TARGET))
                && tag.getLong(PURSUIT_EXPIRES) >= now;
        Vec3 markedDirection = new Vec3(tag.getDouble(PURSUIT_DIRECTION_X), 0.0D,
                tag.getDouble(PURSUIT_DIRECTION_Z));
        float targetSpan = Math.max(target.getBbWidth(), target.getBbHeight());
        if (matchingMark && formsPincer(markedDirection, direction, targetSpan)) {
            clearStoredPursuit(tag);
            tag.putLong(PURSUIT_COOLDOWN, now + PURSUIT_COOLDOWN_TICKS);
            PENDING_PURSUITS.add(new PendingPursuit(player.serverLevel().dimension(),
                    player.getUUID(), target.getUUID(), now + PURSUIT_DELAY_TICKS,
                    pursuitDamage(player), markedDirection, direction));
            sendPursuitVfx(player.serverLevel(), player, target, markedDirection,
                    BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_CROSS, 12, 1.0F);
            foxDust(player.serverLevel(), target.getBoundingBox().getCenter(),
                    false, 16, 0.30D);
        } else if (!matchingMark) {
            tag.putString(PURSUIT_TARGET, targetId);
            tag.putDouble(PURSUIT_DIRECTION_X, direction.x);
            tag.putDouble(PURSUIT_DIRECTION_Z, direction.z);
            tag.putLong(PURSUIT_EXPIRES, now + PURSUIT_MARK_TICKS);
            sendPursuitVfx(player.serverLevel(), player, target, direction,
                    BladeTechniqueVfxPacket.TWIN_FOX_PURSUIT_MARK,
                    PURSUIT_MARK_TICKS, 0.72F);
            foxDust(player.serverLevel(), target.getBoundingBox().getCenter(),
                    true, 12, 0.24D);
        }
    }

    static void tick(TickEvent.ServerTickEvent event) {
        tickHunts(event);
        tickPursuits(event);
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING_HUNTS.removeIf(pending -> pending.dimension.equals(level.dimension()));
        PENDING_PURSUITS.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING_HUNTS.clear();
        PENDING_PURSUITS.clear();
    }

    static void clearStoredPursuit(CompoundTag tag) {
        tag.remove(PURSUIT_TARGET);
        tag.remove(PURSUIT_DIRECTION_X);
        tag.remove(PURSUIT_DIRECTION_Z);
        tag.remove(LEGACY_PURSUIT_COMBO);
        tag.remove(PURSUIT_EXPIRES);
    }

    static boolean formsPincer(Vec3 first, Vec3 second, float targetWidth) {
        if (first.lengthSqr() < 0.5D || second.lengthSqr() < 0.5D) {
            return false;
        }
        double maximumDot = targetWidth >= 2.5F
                ? Math.cos(Math.toRadians(80.0D))
                : Math.cos(Math.toRadians(100.0D));
        return first.normalize().dot(second.normalize()) <= maximumDot;
    }

    private static void tickHunts(TickEvent.ServerTickEvent event) {
        Iterator<PendingHunt> iterator = PENDING_HUNTS.iterator();
        while (iterator.hasNext()) {
            PendingHunt pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            if (level == null) {
                iterator.remove();
                continue;
            }
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.BLACK_SAYA_WHITE_HILT) {
                iterator.remove();
                continue;
            }
            long elapsed = level.getGameTime() - pending.startTick;
            double progress = Math.min(1.0D,
                    Math.max(0.0D, elapsed / (double) HUNT_DURATION_TICKS));
            Vec3 endpoint = huntEndpoint(level, pending);
            Vec3 white = huntPath(pending.start, endpoint, progress, 1.0D);
            Vec3 black = huntPath(pending.start, endpoint, progress, -1.0D);
            if (elapsed > 0L && elapsed < HUNT_DURATION_TICKS) {
                double previousProgress = Math.max(0.0D,
                        (elapsed - 1.0D) / HUNT_DURATION_TICKS);
                Vec3 previousWhite = huntPath(pending.start, endpoint,
                        previousProgress, 1.0D);
                Vec3 previousBlack = huntPath(pending.start, endpoint,
                        previousProgress, -1.0D);
                if (!pending.whiteBlocked && pathBlocked(level, player,
                        previousWhite, white)) {
                    pending.whiteBlocked = true;
                }
                if (!pending.blackBlocked && pathBlocked(level, player,
                        previousBlack, black)) {
                    pending.blackBlocked = true;
                }
                LivingEntity victim = huntVictim(level, player, pending, endpoint);
                if (elapsed >= 7L && victim != null) {
                    double reach = 0.82D + victim.getBbWidth() * 0.55D;
                    Vec3 center = victim.getBoundingBox().getCenter();
                    if (!pending.whiteBlocked && !pending.whiteHit
                            && white.distanceTo(center) <= reach) {
                        pending.whiteHit = true;
                        foxDust(level, white, true, 16, 0.28D);
                    }
                    if (!pending.blackBlocked && !pending.blackHit
                            && black.distanceTo(center) <= reach) {
                        pending.blackHit = true;
                        foxDust(level, black, false, 16, 0.28D);
                    }
                }
                if ((elapsed & 3L) == 0L) {
                    foxTrail(level, white, true);
                    foxTrail(level, black, false);
                }
            }

            if (elapsed < HUNT_DURATION_TICKS) {
                continue;
            }
            iterator.remove();
            LivingEntity victim = huntVictim(level, player, pending, endpoint);
            if (victim != null) {
                Vec3 center = victim.getBoundingBox().getCenter();
                double reach = 1.0D + victim.getBbWidth() * 0.55D;
                pending.whiteHit |= !pending.whiteBlocked
                        && white.distanceTo(center) <= reach;
                pending.blackHit |= !pending.blackBlocked
                        && black.distanceTo(center) <= reach;
            }

            LegacyFusionCombatSupport.spawnVisualSlash(player, white,
                    pending.yaw + 96.0F, 0.0F, 0xF3E8E2, 1.58F, 12);
            LegacyFusionCombatSupport.spawnVisualSlash(player, black,
                    pending.yaw - 96.0F, 0.0F, 0x371A30, 1.62F, 12);
            int hitCount = (pending.whiteHit ? 1 : 0) + (pending.blackHit ? 1 : 0);
            double damage = hitCount * pending.perFoxDamage;
            if (hitCount == 2) {
                damage += pending.finisherDamage;
            }
            Vec3 impact = victim == null
                    ? endpoint : victim.getBoundingBox().getCenter();
            if (damage > 0.0D) {
                LegacyFusionCombatSupport.spawnVisualSlash(player,
                        impact.add(0.0D, 0.12D, 0.0D), pending.yaw,
                        0.0F, 0xA62846, hitCount == 2 ? 2.15F : 1.45F, 12);
                if (victim != null) {
                    LegacyFusionCombatSupport.hurtPreservingIFrames(
                            level, player, victim, (float) Math.min(36.0D, damage));
                }
                if (hitCount == 2 && victim != null
                        && victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 0.8D) {
                    Vec3 pull = endpoint.subtract(victim.position())
                            .multiply(1.0D, 0.0D, 1.0D);
                    if (pull.lengthSqr() > 0.01D) {
                        Vec3 normalized = pull.normalize();
                        victim.push(normalized.x * 0.18D, 0.04D,
                                normalized.z * 0.18D);
                    }
                }
                sendHuntVfx(level, player, victim, pending.start, impact,
                        BladeTechniqueVfxPacket.TWIN_FOX_MOONHUNT_IMPACT,
                        12, hitCount == 2 ? 1.25F : 0.72F);
            }
            foxDust(level, endpoint, true, hitCount == 2 ? 54 : 28, 0.62D);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    endpoint.x, endpoint.y, endpoint.z,
                    hitCount == 2 ? 4 : 2, 0.28D, 0.24D, 0.28D, 0.0D);
            level.playSound(null, BlockPos.containing(endpoint),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, hitCount == 2 ? 1.05F : 0.68F, 1.45F);
            level.playSound(null, BlockPos.containing(endpoint),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, hitCount == 2 ? 1.1F : 0.72F, 0.72F);
            if (hitCount == 2) {
                level.playSound(null, BlockPos.containing(endpoint),
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.58F, 1.65F);
            }
        }
    }

    private static void tickPursuits(TickEvent.ServerTickEvent event) {
        Iterator<PendingPursuit> iterator = PENDING_PURSUITS.iterator();
        while (iterator.hasNext()) {
            PendingPursuit pending = iterator.next();
            ServerLevel level = event.getServer().getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId());
            Entity entity = level.getEntity(pending.targetId());
            if (player == null || player.level() != level
                    || !(entity instanceof LivingEntity target)
                    || !LegacyFusionCombatSupport.canAffect(player, target)
                    || player.distanceToSqr(target) > 32.0D * 32.0D
                    || !player.hasLineOfSight(target)
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.WHITE_SAYA_BLACK_HILT) {
                continue;
            }
            Vec3 center = target.getBoundingBox().getCenter();
            float whiteYaw = directionYaw(pending.markedDirection());
            float blackYaw = directionYaw(pending.triggerDirection());
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    blackYaw + 90.0F, 0.0F, 0x371A30, 1.38F, 12);
            LegacyFusionCombatSupport.spawnVisualSlash(player,
                    center.add(0.0D, 0.10D, 0.0D),
                    whiteYaw - 90.0F, 0.0F, 0xF3E8E2, 1.08F, 12);
            LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, (float) pending.damage());
            foxDust(level, center, false, 26, 0.42D);
            level.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.72F, 1.30F);
        }
    }

    private static LivingEntity acquireTarget(ServerPlayer player,
            ISlashBladeState state) {
        Entity locked = state.getTargetEntity(player.level());
        if (locked instanceof LivingEntity living
                && player.distanceToSqr(living) <= 24.0D * 24.0D
                && player.hasLineOfSight(living)
                && LegacyFusionCombatSupport.canAffect(player, living)) {
            return living;
        }
        Vec3 look = player.getLookAngle().normalize();
        AABB search = player.getBoundingBox()
                .expandTowards(look.scale(24.0D)).inflate(4.0D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(
                LivingEntity.class, search,
                target -> LegacyFusionCombatSupport.canAffect(player, target)
                        && player.hasLineOfSight(target))) {
            Vec3 delta = candidate.getBoundingBox().getCenter()
                    .subtract(player.getEyePosition());
            double distance = delta.length();
            if (distance <= 0.001D || distance > 24.0D) {
                continue;
            }
            double alignment = look.dot(delta.scale(1.0D / distance));
            if (alignment < 0.82D) {
                continue;
            }
            double score = distance * (2.0D - alignment);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static Vec3 huntEndpoint(ServerLevel level, PendingHunt pending) {
        if (pending.targetId == null) {
            return pending.aim;
        }
        Entity entity = level.getEntity(pending.targetId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
            return pending.aim;
        }
        return pending.aim.lerp(target.getBoundingBox().getCenter(), 0.35D);
    }

    private static Vec3 huntPath(Vec3 start, Vec3 end,
            double progress, double direction) {
        Vec3 delta = end.subtract(start);
        Vec3 side = new Vec3(-delta.z, 0.0D, delta.x);
        if (side.lengthSqr() < 0.0001D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        double arc = Math.min(6.0D, Math.max(2.2D, delta.length() * 0.34D));
        Vec3 control = start.add(end).scale(0.5D)
                .add(side.scale(arc * direction))
                .add(0.0D, 1.15D, 0.0D);
        double inverse = 1.0D - progress;
        Vec3 curve = start.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * progress))
                .add(end.scale(progress * progress));
        return curve.add(side.scale(direction * 0.58D * progress))
                .add(0.0D, direction > 0.0D ? 0.22D * progress : -0.08D * progress,
                        0.0D);
    }

    private static LivingEntity huntVictim(ServerLevel level, ServerPlayer player,
            PendingHunt pending, Vec3 endpoint) {
        if (pending.targetId != null) {
            Entity entity = level.getEntity(pending.targetId);
            if (entity instanceof LivingEntity living
                    && LegacyFusionCombatSupport.canAffect(player, living)
                    && player.hasLineOfSight(living)
                    && living.distanceToSqr(endpoint) <= 4.5D * 4.5D) {
                return living;
            }
        }
        return level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(endpoint, endpoint).inflate(1.75D),
                        target -> LegacyFusionCombatSupport.canAffect(player, target)
                                && player.hasLineOfSight(target))
                .stream().min(java.util.Comparator.comparingDouble(
                        target -> target.distanceToSqr(endpoint))).orElse(null);
    }

    private static boolean pathBlocked(ServerLevel level, LivingEntity owner,
            Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS;
    }

    private static Vec3 pursuitDirection(LivingEntity player, LivingEntity target) {
        Vec3 direction = player.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 0.0001D) {
            direction = Vec3.directionFromRotation(0.0F, player.getYRot())
                    .multiply(-1.0D, 0.0D, -1.0D);
        }
        return direction.normalize();
    }

    private static double pursuitDamage(LivingEntity player) {
        return Math.max(0.5D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.55D);
    }

    private static float directionYaw(Vec3 direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    private static double fusionDamage(LivingEntity player, double ratio, double cap) {
        return Math.max(0.5D, Math.min(cap,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ratio));
    }

    private static void sendHuntVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 start, Vec3 end, int type,
            int duration, float intensity) {
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                start.x, start.y, start.z, end.x, end.y, end.z,
                player.getYRot(), intensity, player.getId(),
                target == null ? -1 : target.getId(), duration,
                level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(end) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void sendPursuitVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 direction, int type,
            int duration, float intensity) {
        Vec3 center = target.getBoundingBox().getCenter();
        double radius = Math.max(0.85D, target.getBbWidth() * 0.5D + 0.55D);
        Vec3 anchor = center.add(direction.scale(radius));
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                anchor.x, anchor.y, anchor.z, center.x, center.y, center.z,
                player.getYRot(), intensity, -1, target.getId(), duration,
                level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(center) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static void foxTrail(ServerLevel level, Vec3 position, boolean white) {
        DustParticleOptions dust = white
                ? new DustParticleOptions(new Vector3f(0.94F, 0.87F, 0.84F), 0.72F)
                : new DustParticleOptions(new Vector3f(0.24F, 0.045F, 0.12F), 0.78F);
        level.sendParticles(dust, position.x, position.y, position.z,
                7, 0.13D, 0.13D, 0.13D, 0.008D);
    }

    private static void foxDust(ServerLevel level, Vec3 center, boolean whiteFirst,
            int count, double spread) {
        DustParticleOptions white = new DustParticleOptions(
                new Vector3f(0.94F, 0.87F, 0.84F), 0.82F);
        DustParticleOptions dark = new DustParticleOptions(
                new Vector3f(0.24F, 0.045F, 0.12F), 0.90F);
        level.sendParticles(whiteFirst ? white : dark,
                center.x, center.y, center.z, count,
                spread, spread * 0.72D, spread, 0.025D);
        level.sendParticles(whiteFirst ? dark : white,
                center.x, center.y, center.z, Math.max(6, count / 2),
                spread * 0.72D, spread * 0.52D, spread * 0.72D, 0.018D);
    }

    private static final class PendingHunt {
        final ResourceKey<Level> dimension;
        final UUID playerId;
        final UUID targetId;
        final Vec3 start;
        final Vec3 aim;
        final long startTick;
        final float yaw;
        final double perFoxDamage;
        final double finisherDamage;
        boolean whiteHit;
        boolean blackHit;
        boolean whiteBlocked;
        boolean blackBlocked;

        PendingHunt(ResourceKey<Level> dimension, UUID playerId, UUID targetId,
                Vec3 start, Vec3 aim, long startTick, float yaw,
                double perFoxDamage, double finisherDamage) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.start = start;
            this.aim = aim;
            this.startTick = startTick;
            this.yaw = yaw;
            this.perFoxDamage = perFoxDamage;
            this.finisherDamage = finisherDamage;
        }
    }

    private record PendingPursuit(ResourceKey<Level> dimension, UUID playerId,
            UUID targetId, long dueTick, double damage, Vec3 markedDirection,
            Vec3 triggerDirection) {
    }

    private TwinFoxFusionHandler() {
    }
}
