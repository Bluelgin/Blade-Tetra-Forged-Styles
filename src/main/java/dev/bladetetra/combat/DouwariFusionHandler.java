package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.forging.LegacyFusion;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Muramasa saya + Doutanuki hilt: a short-charge, anti-armor vertical cut. */
final class DouwariFusionHandler {
    private static final List<PendingDouwari> PENDING = new ArrayList<>();
    private static final double RANGE = 7.0D;
    private static final long CHARGE_TICKS = 7L;

    static void onSlashArt(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT) {
            return;
        }
        LivingEntity target = acquireTarget(player, state);
        if (target == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        double attackSnapshot = Math.max(0.0D,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));

        // One player can only keep one Douwari charge pending. Releasing it again
        // replaces the old wind-up instead of queueing several delayed heavy hits.
        PENDING.removeIf(pending -> pending.playerId().equals(player.getUUID()));
        PENDING.add(new PendingDouwari(level.dimension(), player.getUUID(),
                target.getUUID(), level.getGameTime() + CHARGE_TICKS,
                attackSnapshot));

        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.25D, player.getZ(),
                7, 0.28D, 0.05D, 0.28D, 0.018D);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_HIT,
                SoundSource.PLAYERS, 0.42F, 0.72F);
    }

    static void tick(TickEvent.ServerTickEvent event) {
        Iterator<PendingDouwari> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingDouwari pending = iterator.next();
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
                    != LegacyFusion.MURAMASA_SAYA_DOUTANUKI_HILT) {
                continue;
            }
            Entity entity = level.getEntity(pending.targetId());
            if (!(entity instanceof LivingEntity target)
                    || !validTarget(player, target, RANGE + 1.0D, 0.35D)) {
                continue;
            }

            AttributeInstance toughnessAttribute = target.getAttribute(
                    Attributes.ARMOR_TOUGHNESS);
            double toughness = toughnessAttribute == null
                    ? 0.0D : toughnessAttribute.getValue();
            float damage = DouwariBalance.damage(pending.attackSnapshot(),
                    target.getArmorValue(), toughness);
            if (damage <= 0.0F) {
                continue;
            }

            boolean hurt = SoulLegacyDamageGuard.apply(() -> target.hurt(
                    level.damageSources().playerAttack(player), damage));
            if (!hurt) {
                continue;
            }

            Vec3 impact = target.getBoundingBox().getCenter();
            LegacyFusionCombatSupport.spawnVisualSlash(player, impact,
                    player.getYRot(), 90.0F, 0xD9C8A5, 2.15F, 10);
            level.sendParticles(ParticleTypes.CRIT,
                    impact.x, target.getY() + 0.15D, impact.z,
                    18, 0.58D, 0.12D, 0.58D, 0.08D);
            level.sendParticles(ParticleTypes.CLOUD,
                    impact.x, target.getY() + 0.05D, impact.z,
                    10, 0.72D, 0.03D, 0.72D, 0.025D);
            BlockPos soundPos = target.blockPosition();
            level.playSound(null, soundPos, SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.72F, 0.72F);
            level.playSound(null, soundPos, SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 1.0F, 0.62F);
        }
    }

    static void onLevelUnload(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension().equals(level.dimension()));
    }

    static void clear() {
        PENDING.clear();
    }

    private static LivingEntity acquireTarget(ServerPlayer player,
            ISlashBladeState state) {
        Entity locked = state.getTargetEntity(player.level());
        if (locked instanceof LivingEntity living
                && validTarget(player, living, RANGE, 0.55D)) {
            return living;
        }

        Vec3 look = player.getLookAngle().normalize();
        AABB search = player.getBoundingBox()
                .expandTowards(look.scale(RANGE)).inflate(1.6D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(
                LivingEntity.class, search,
                target -> LegacyFusionCombatSupport.canAffect(player, target)
                        && player.hasLineOfSight(target))) {
            Vec3 delta = candidate.getBoundingBox().getCenter()
                    .subtract(player.getEyePosition());
            double distance = delta.length();
            if (distance <= 0.001D || distance > RANGE) {
                continue;
            }
            double alignment = look.dot(delta.scale(1.0D / distance));
            if (alignment < 0.90D) {
                continue;
            }
            double score = distance + (1.0D - alignment) * 8.0D;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean validTarget(ServerPlayer player, LivingEntity target,
            double range, double minimumAlignment) {
        if (!LegacyFusionCombatSupport.canAffect(player, target)
                || !player.hasLineOfSight(target)
                || player.distanceToSqr(target) > range * range) {
            return false;
        }
        Vec3 delta = target.getBoundingBox().getCenter()
                .subtract(player.getEyePosition());
        double length = delta.length();
        if (length <= 0.001D) {
            return true;
        }
        return player.getLookAngle().normalize()
                .dot(delta.scale(1.0D / length)) >= minimumAlignment;
    }

    private record PendingDouwari(ResourceKey<Level> dimension, UUID playerId,
            UUID targetId, long dueTick, double attackSnapshot) {
    }

    private DouwariFusionHandler() {
    }
}
