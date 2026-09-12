package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyFusion;
import dev.bladetetra.network.BladeTechniqueVfxPacket;
import dev.bladetetra.network.ModNetwork;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Runtime implementation of Yasha saya + Kikouku hilt: Twin Phase. */
final class TwinPhaseFusionHandler {
    private static final List<PendingTwinPhase> PENDING_PHASES = new ArrayList<>();
    private static final List<PendingTwinPhaseSlash> PENDING_SLASHES = new ArrayList<>();

    static void onSlashArt(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(6.5D),
                target -> LegacyFusionCombatSupport.canAffect(player, target)
                        && player.hasLineOfSight(target));
        boolean crowd = nearby.size() >= 3;
        LivingEntity target = crowd ? null : acquireTarget(player, state);
        if (!crowd && target == null && !nearby.isEmpty()) {
            target = nearby.stream().min(java.util.Comparator.comparingDouble(
                    player::distanceToSqr)).orElse(null);
        }

        Vec3 center = player.position().add(0.0D, 0.85D, 0.0D);
        Vec3 end = crowd ? crowdCenter(nearby)
                : target == null ? center : target.getBoundingBox().getCenter();
        int type = crowd ? BladeTechniqueVfxPacket.TWIN_PHASE_KIKOUKU
                : BladeTechniqueVfxPacket.TWIN_PHASE_YASHA;

        // Snapshot all gameplay damage at release time. Swapping to another copy
        // of the same fusion during the animation can no longer change the finisher.
        double attack = Math.max(1.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float impactDamage = (float) Math.min(crowd ? 14.0D : 30.0D,
                attack * (crowd ? 0.72D : 1.45D));

        PENDING_PHASES.add(new PendingTwinPhase(level.dimension(),
                player.getUUID(), target == null ? null : target.getUUID(),
                level.getGameTime() + 9L, crowd, end, impactDamage));
        scheduleSlashes(level, player, end, crowd,
                target == null ? null : target.getUUID(), attack);
        sendVfx(level, player, target, center, end, type, 18,
                crowd ? 1.08F : 1.0F);
        level.playSound(null, player.blockPosition(),
                crowd ? SoundEvents.RESPAWN_ANCHOR_CHARGE
                        : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, crowd ? 0.72F : 0.58F,
                crowd ? 0.72F : 1.55F);
    }

    static void tick(TickEvent.ServerTickEvent event) {
        tickSlashes(event);
        tickFinisher(event);
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING_PHASES.removeIf(pending -> pending.dimension().equals(level.dimension()));
        PENDING_SLASHES.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING_PHASES.clear();
        PENDING_SLASHES.clear();
    }

    static float slashDamage(double attack, boolean crowd, boolean finisher) {
        double scale = crowd ? (finisher ? 0.16D : 0.13D) : 0.22D;
        double cap = crowd ? (finisher ? 4.0D : 3.5D) : 5.0D;
        return (float) Math.max(0.5D, Math.min(cap, attack * scale));
    }

    private static void tickSlashes(TickEvent.ServerTickEvent event) {
        Iterator<PendingTwinPhaseSlash> iterator = PENDING_SLASHES.iterator();
        while (iterator.hasNext()) {
            PendingTwinPhaseSlash pending = iterator.next();
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
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
                continue;
            }

            Vec3 visualPosition = pending.position();
            if (!pending.crowd() && pending.targetId() != null) {
                Entity entity = level.getEntity(pending.targetId());
                if (entity instanceof LivingEntity target && target.isAlive()
                        && LegacyFusionCombatSupport.canAffect(player, target)
                        && player.distanceToSqr(target) <= 24.0D * 24.0D
                        && player.hasLineOfSight(target)) {
                    visualPosition = target.getBoundingBox().getCenter();
                }
            }

            LegacyFusionCombatSupport.spawnVisualSlash(player, visualPosition,
                    pending.yaw(), pending.roll(), pending.color(),
                    pending.size(), pending.lifetime());
            applySlashDamage(level, player, pending);
        }
    }

    private static void tickFinisher(TickEvent.ServerTickEvent event) {
        Iterator<PendingTwinPhase> iterator = PENDING_PHASES.iterator();
        while (iterator.hasNext()) {
            PendingTwinPhase pending = iterator.next();
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
            if (player == null || player.level() != level
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.YASHA_SAYA_KIKOUKU_HILT) {
                continue;
            }

            if (pending.crowd()) {
                int hits = 0;
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(pending.impactCenter(), 13.5D, 8.0D, 13.5D),
                        candidate -> LegacyFusionCombatSupport.canAffect(player, candidate)
                                && player.hasLineOfSight(candidate))) {
                    if (hits++ >= 8) {
                        break;
                    }
                    LegacyFusionCombatSupport.hurtPreservingIFrames(
                            level, player, target, pending.impactDamage());
                }
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pending.impactCenter().x, pending.impactCenter().y + 0.55D,
                        pending.impactCenter().z,
                        30, 2.1D, 0.7D, 2.1D, 0.025D);
                level.playSound(null, BlockPos.containing(pending.impactCenter()),
                        SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                        1.05F, 0.62F);
                continue;
            }

            if (pending.targetId() == null) {
                continue;
            }
            Entity entity = level.getEntity(pending.targetId());
            if (entity instanceof LivingEntity target
                    && LegacyFusionCombatSupport.canAffect(player, target)
                    && player.distanceToSqr(target) <= 24.0D * 24.0D
                    && player.hasLineOfSight(target)) {
                LegacyFusionCombatSupport.hurtPreservingIFrames(
                        level, player, target, pending.impactDamage());
                level.sendParticles(ParticleTypes.CHERRY_LEAVES,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                        target.getZ(), 24, 0.65D, 0.75D, 0.65D, 0.018D);
                level.playSound(null, target.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        0.95F, 1.18F);
            }
        }
    }

    private static void scheduleSlashes(ServerLevel level,
            ServerPlayer player, Vec3 center, boolean crowd, UUID targetId,
            double attack) {
        long now = level.getGameTime();
        if (!crowd) {
            for (int index = 0; index < 5; index++) {
                PENDING_SLASHES.add(new PendingTwinPhaseSlash(
                        level.dimension(), player.getUUID(), targetId, center,
                        now + 1L + index * 2L, player.getYRot() + index * 7.0F,
                        -56.0F + index * 28.0F,
                        index == 2 ? 0xFFF5E9 : 0xF06A9B,
                        1.20F + index * 0.10F, 9,
                        slashDamage(attack, false, false), false));
            }
            return;
        }
        for (int index = 0; index < 4; index++) {
            float angle = player.getYRot() + index * 90.0F;
            double radians = Math.toRadians(angle);
            Vec3 position = center.add(Math.sin(radians) * 1.15D,
                    0.62D + (index & 1) * 0.22D,
                    Math.cos(radians) * 1.15D);
            PENDING_SLASHES.add(new PendingTwinPhaseSlash(
                    level.dimension(), player.getUUID(), null, position,
                    now + 2L + index, angle + 180.0F,
                    index % 2 == 0 ? 24.0F : -24.0F,
                    index == 1 ? 0xF4DAEA : 0x7C184A,
                    1.75F, 11,
                    slashDamage(attack, true, false), true));
        }
        for (int index = 0; index < 3; index++) {
            PENDING_SLASHES.add(new PendingTwinPhaseSlash(
                    level.dimension(), player.getUUID(), null,
                    center.add(0.0D, 0.72D + index * 0.26D, 0.0D),
                    now + 7L + index, player.getYRot() + index * 60.0F,
                    -48.0F + index * 48.0F,
                    index == 1 ? 0xFFF4FA : 0xB72B68,
                    2.15F + index * 0.16F, 12,
                    slashDamage(attack, true, true), true));
        }
    }

    private static void applySlashDamage(ServerLevel level,
            ServerPlayer player, PendingTwinPhaseSlash pending) {
        if (pending.damage() <= 0.0F) {
            return;
        }
        if (!pending.crowd()) {
            Entity entity = pending.targetId() == null
                    ? null : level.getEntity(pending.targetId());
            if (entity instanceof LivingEntity target && target.isAlive()
                    && LegacyFusionCombatSupport.canAffect(player, target)
                    && player.distanceToSqr(target) <= 24.0D * 24.0D
                    && player.hasLineOfSight(target)) {
                LegacyFusionCombatSupport.hurtPreservingIFrames(
                        level, player, target, pending.damage());
            }
            return;
        }

        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(pending.position(), 8.0D, 6.0D, 8.0D),
                candidate -> LegacyFusionCombatSupport.canAffect(player, candidate)
                        && player.hasLineOfSight(candidate))) {
            if (hits++ >= 8) {
                break;
            }
            LegacyFusionCombatSupport.hurtPreservingIFrames(
                    level, player, target, pending.damage());
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

    private static void sendVfx(ServerLevel level, ServerPlayer player,
            LivingEntity target, Vec3 start, Vec3 end, int type,
            int duration, float intensity) {
        BladeTechniqueVfxPacket packet = new BladeTechniqueVfxPacket(type,
                start.x, start.y, start.z, end.x, end.y, end.z,
                player.getYRot(), intensity, player.getId(),
                target == null ? -1 : target.getId(), duration,
                level.random.nextInt());
        for (ServerPlayer viewer : level.players()) {
            if (viewer == player || viewer.distanceToSqr(start) <= 72.0D * 72.0D) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> viewer), packet);
            }
        }
    }

    private static Vec3 crowdCenter(List<LivingEntity> targets) {
        Vec3 sum = Vec3.ZERO;
        for (LivingEntity target : targets) {
            sum = sum.add(target.position());
        }
        return targets.isEmpty() ? Vec3.ZERO
                : sum.scale(1.0D / targets.size()).add(0.0D, 0.08D, 0.0D);
    }

    private record PendingTwinPhase(ResourceKey<Level> dimension, UUID playerId,
            UUID targetId, long dueTick, boolean crowd, Vec3 impactCenter,
            float impactDamage) {
    }

    private record PendingTwinPhaseSlash(ResourceKey<Level> dimension,
            UUID playerId, UUID targetId, Vec3 position, long dueTick, float yaw,
            float roll, int color, float size, int lifetime, float damage,
            boolean crowd) {
    }

    private TwinPhaseFusionHandler() {
    }
}
