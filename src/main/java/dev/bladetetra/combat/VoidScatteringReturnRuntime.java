package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns delayed visual-flight and bounded return damage for Void Scattering. */
final class VoidScatteringReturnRuntime {
    private static final double RETURN_RANGE = 40.0D;
    private static final int VOID_COLOR = 0xC8A7FF;
    private static final int MAX_DAMAGE_ATTEMPTS = 2;

    private static final List<PendingReturn> PENDING = new ArrayList<>();
    private static final Map<Long, ReturnVolley> VOLLEYS = new HashMap<>();
    private static long nextVolleyId = 1L;

    static void scheduleVolley(ServerPlayer player, List<UUID> targetIds,
            float damage, int initialDelay, int staggerTicks) {
        if (targetIds.isEmpty() || damage <= 0.0F) {
            return;
        }
        long volleyId = nextVolleyId++;
        VOLLEYS.put(volleyId, new ReturnVolley(
                player.level().dimension(), player.getUUID(), targetIds.size()));
        for (int index = 0; index < targetIds.size(); index++) {
            schedule(player, targetIds.get(index), damage,
                    initialDelay + index * staggerTicks, index, volleyId);
        }
    }

    static void schedule(ServerPlayer player, UUID targetId, float damage, int delay) {
        schedule(player, targetId, damage, delay,
                Math.max(0, delay / Math.max(1,
                        VoidScatteringFusionHandler.COUNTER_STAGGER_TICKS)), -1L);
    }

    static void schedule(ServerPlayer player, UUID targetId,
            float damage, int delay, int sequence, long volleyId) {
        if (damage <= 0.0F) {
            return;
        }
        PENDING.add(new PendingReturn(
                player.level().dimension(), player.getUUID(), targetId, damage,
                player.getServer().overworld().getGameTime() + Math.max(0, delay),
                Math.max(0, sequence), volleyId));
    }

    static void tick(TickEvent.ServerTickEvent event) {
        long now = event.getServer().overworld().getGameTime();
        Iterator<PendingReturn> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingReturn pending = iterator.next();
            if (now < pending.dueTick) {
                continue;
            }
            ServerPlayer player = event.getServer().getPlayerList()
                    .getPlayer(pending.playerId);
            ServerLevel level = event.getServer().getLevel(pending.dimension);
            Entity entity = level == null ? null : level.getEntity(pending.targetId);
            if (player == null || player.level() != level
                    || !(entity instanceof LivingEntity target)
                    || !target.isAlive()
                    || !LegacyFusionCombatSupport.canAffect(player, target)
                    || player.distanceToSqr(target) > RETURN_RANGE * RETURN_RANGE) {
                finishVolley(pending, false, level, player, null);
                iterator.remove();
                continue;
            }
            if (!pending.launched) {
                pending.launched = true;
                pending.dueTick = now + launchVisualSword(player, target, pending.sequence);
                continue;
            }

            pending.damageAttempts++;
            boolean damaged = LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, pending.damage);
            if (shouldRetryDamage(damaged, pending.damageAttempts)) {
                pending.dueTick = now + 1L;
                continue;
            }
            if (damaged) {
                Vec3 center = target.getBoundingBox().getCenter();
                LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                        player.getYRot(), 90.0F, VOID_COLOR, 1.08F, 7);
                level.sendParticles(ParticleTypes.PORTAL,
                        center.x, center.y, center.z, 8,
                        0.32D, 0.42D, 0.32D, 0.045D);
                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                        center.x, center.y, center.z, 6,
                        0.38D, 0.4D, 0.38D, 0.028D);
            }
            finishVolley(pending, damaged, level, player, target);
            iterator.remove();
        }
    }

    static boolean shouldRetryDamage(boolean damaged, int attempts) {
        return !damaged && attempts < MAX_DAMAGE_ATTEMPTS;
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        PENDING.removeIf(state -> state.dimension.equals(dimension));
        VOLLEYS.values().removeIf(state -> state.dimension.equals(dimension));
    }

    static void clear() {
        PENDING.clear();
        VOLLEYS.clear();
        nextVolleyId = 1L;
    }

    private static int launchVisualSword(
            ServerPlayer player, LivingEntity target, int sequence) {
        ServerLevel level = player.serverLevel();
        Vec3 center = target.getBoundingBox().getCenter();
        double angle = Math.toRadians(
                (sequence * 61.0D + player.tickCount * 19.0D) % 360.0D);
        Vec3 start = player.position().add(
                Math.cos(angle) * 1.62D,
                1.18D + (sequence % 3) * 0.24D,
                Math.sin(angle) * 1.62D);
        Vec3 direction = center.subtract(start).normalize();

        LegacyFusionCombatSupport.spawnVisualSlash(player, start,
                (float) Math.toDegrees(angle) + 90.0F, 0.0F,
                VOID_COLOR, 0.72F, 5);
        level.sendParticles(ParticleTypes.PORTAL,
                start.x, start.y, start.z, 7,
                0.18D, 0.28D, 0.18D, 0.045D);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.28F, 1.46F + (sequence % 3) * 0.08F);

        EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                SlashBlade.RegistryEvents.SummonedSword, level);
        sword.setPos(start.x, start.y, start.z);
        sword.setOwner(player);
        sword.setShooter(player);
        sword.setHitEntity(target);
        sword.setDamage(0.0D);
        sword.setColor(VOID_COLOR);
        sword.setRoll((float) ((sequence * 61) % 360));
        sword.setDelay(0);
        sword.setNoClip(true);
        LegacyFusionCombatSupport.markVisualOnly(sword);
        sword.shoot(direction.x, direction.y, direction.z, 2.15F, 0.0F);
        level.addFreshEntity(sword);
        return Math.max(2, (int) Math.ceil(start.distanceTo(center) / 2.15D));
    }

    private static void finishVolley(PendingReturn pending, boolean damaged,
            ServerLevel level, ServerPlayer player, LivingEntity target) {
        if (pending.volleyId < 0L) {
            return;
        }
        ReturnVolley volley = VOLLEYS.get(pending.volleyId);
        if (volley == null) {
            return;
        }
        if (damaged && target != null
                && !volley.hitTargets.contains(target.getUUID())) {
            volley.hitTargets.add(target.getUUID());
        }
        volley.remaining--;
        if (volley.remaining > 0) {
            return;
        }
        VOLLEYS.remove(pending.volleyId);
        if (level == null || player == null) {
            return;
        }

        for (UUID targetId : volley.hitTargets) {
            Entity entity = level.getEntity(targetId);
            if (!(entity instanceof LivingEntity hit) || !hit.isAlive()) {
                continue;
            }
            Vec3 center = hit.getBoundingBox().getCenter();
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    player.getYRot() - 45.0F, 90.0F,
                    VOID_COLOR, 1.42F, 8);
            LegacyFusionCombatSupport.spawnVisualSlash(player, center,
                    player.getYRot() + 45.0F, 90.0F,
                    VOID_COLOR, 1.24F, 8);
            level.sendParticles(ParticleTypes.PORTAL,
                    center.x, center.y, center.z, 16,
                    0.48D, 0.62D, 0.48D, 0.08D);
            level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                    center.x, center.y, center.z, 9,
                    0.46D, 0.48D, 0.46D, 0.05D);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.AMETHYST_CLUSTER_BREAK,
                    SoundSource.PLAYERS, 0.62F, 0.88F);
        }
    }

    private static final class PendingReturn {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final UUID targetId;
        private final float damage;
        private final int sequence;
        private final long volleyId;
        private long dueTick;
        private boolean launched;
        private int damageAttempts;

        private PendingReturn(ResourceKey<Level> dimension, UUID playerId,
                UUID targetId, float damage, long dueTick, int sequence,
                long volleyId) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.targetId = targetId;
            this.damage = damage;
            this.dueTick = dueTick;
            this.sequence = sequence;
            this.volleyId = volleyId;
        }
    }

    private static final class ReturnVolley {
        private final ResourceKey<Level> dimension;
        private final UUID playerId;
        private final List<UUID> hitTargets = new ArrayList<>();
        private int remaining;

        private ReturnVolley(ResourceKey<Level> dimension, UUID playerId, int remaining) {
            this.dimension = dimension;
            this.playerId = playerId;
            this.remaining = remaining;
        }
    }

    private VoidScatteringReturnRuntime() {
    }
}
