package dev.bladetetra.combat;

import dev.bladetetra.easteregg.SoulLegacyDamageGuard;
import dev.bladetetra.forging.LegacyFusion;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.Drive;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Native SlashBlade-based behavior for the second signature fusion batch. */
final class SignatureFusionBatchHandler {
    static final double WITHERED_SWORD_DAMAGE = 0.375D;
    static final double WITHERED_FALLBACK_DAMAGE = 1.50D;
    static final int WITHERED_OVERHEAD_SWORD_COUNT = 3;
    static final double WITHERED_TARGET_RANGE = 32.0D;
    static final int WITHERED_DURATION_TICKS = 100;
    static final int WITHERED_AMPLIFIER = 1;

    static final long PIERCING_TRIGGER_WINDOW = 24L;
    static final int PIERCING_VOID_SWORD_COUNT = 3;
    static final double PIERCING_VOID_SWORD_DAMAGE = 0.32D;

    private static final int VOID_COLOR = 0xC8A7FF;
    private static final int WITHERED_COLOR = 0x72534A;
    private static final Map<UUID, PiercingCast> PIERCING_CASTS = new HashMap<>();

    static void onWitheredDrive(ServerPlayer player, ItemStack blade,
            ISlashBladeState state) {
        if (LegacyFusion.active(blade) != LegacyFusion.TAGAYASAN_SAYA_KOSEKI_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity locked = state.getTargetEntity(level);
        if (!(locked instanceof LivingEntity target)
                || target.level() != level
                || player.distanceToSqr(target)
                > WITHERED_TARGET_RANGE * WITHERED_TARGET_RANGE
                || !player.hasLineOfSight(target)
                || !LegacyFusionCombatSupport.canAffect(player, target)) {
            spawnForwardWitheredDrive(player);
            level.sendParticles(ParticleTypes.ASH,
                    player.getX(), player.getY() + 0.9D, player.getZ(),
                    6, 0.24D, 0.30D, 0.24D, 0.012D);
            return;
        }
        spawnWitheredSwords(level, player, target);
        level.sendParticles(ParticleTypes.ASH,
                target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
                16, 0.46D, 0.38D, 0.46D, 0.025D);
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT,
                SoundSource.PLAYERS, 0.62F, 1.22F);
    }

    static void onPiercingVoidMoon(ServerPlayer player, ItemStack blade) {
        if (LegacyFusion.active(blade) != LegacyFusion.BLACK_SAYA_SANGE_HILT) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PIERCING_CASTS.put(player.getUUID(), new PiercingCast(level.dimension(),
                level.getGameTime() + PIERCING_TRIGGER_WINDOW));
        level.sendParticles(ParticleTypes.PORTAL,
                player.getX(), player.getY() + 0.9D, player.getZ(),
                8, 0.24D, 0.34D, 0.24D, 0.018D);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.42F, 1.42F);
    }

    static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || LegacyFusion.active(player.getMainHandItem())
                != LegacyFusion.BLACK_SAYA_SANGE_HILT
                || !isPiercingCombo(event.getSlashBladeState().getComboSeq())
                || !LegacyFusionCombatSupport.canAffect(player, event.getTarget())) {
            return;
        }
        PiercingCast cast = PIERCING_CASTS.get(player.getUUID());
        if (cast == null || !cast.dimension.equals(level.dimension())
                || level.getGameTime() > cast.expiresAt) {
            PIERCING_CASTS.remove(player.getUUID());
            return;
        }
        PIERCING_CASTS.remove(player.getUUID());
        spawnVoidSwords(level, player, event.getTarget());
    }

    static void tick(TickEvent.ServerTickEvent event) {
        PIERCING_CASTS.entrySet().removeIf(entry -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            PiercingCast cast = entry.getValue();
            return player == null || !player.isAlive()
                    || !player.level().dimension().equals(cast.dimension)
                    || player.serverLevel().getGameTime() > cast.expiresAt
                    || LegacyFusion.active(player.getMainHandItem())
                    != LegacyFusion.BLACK_SAYA_SANGE_HILT;
        });
    }

    static void onLevelUnload(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        PIERCING_CASTS.values().removeIf(cast -> cast.dimension.equals(dimension));
    }

    static void clear() {
        PIERCING_CASTS.clear();
    }

    private static boolean isPiercingCombo(ResourceLocation combo) {
        return combo != null && (combo.equals(ComboStateRegistry.PIERCING.getId())
                || combo.equals(ComboStateRegistry.PIERCING_2.getId())
                || combo.equals(ComboStateRegistry.PIERCING_JUST.getId()));
    }

    private static void spawnWitheredSwords(ServerLevel level,
            ServerPlayer player, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 towardTarget = center.subtract(player.getEyePosition());
        Vec3 flat = towardTarget.multiply(1.0D, 0.0D, 1.0D);
        if (flat.lengthSqr() < 1.0E-5D) {
            flat = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        }
        if (flat.lengthSqr() < 1.0E-5D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);
        double overheadY = target.getY() + target.getBbHeight() + 2.8D;
        for (int index = 0; index < WITHERED_OVERHEAD_SWORD_COUNT; index++) {
            double offset = (index - 1) * 0.72D;
            Vec3 start = new Vec3(center.x, overheadY, center.z)
                    .add(right.scale(offset));
            spawnWitheredDrive(level, player, target, start,
                    center.subtract(start), index * 2, index * 120.0F);
        }

        Vec3 front = player.getEyePosition().add(flat.scale(0.85D))
                .add(0.0D, -0.22D, 0.0D);
        spawnWitheredDrive(level, player, target, front,
                center.subtract(front), 1, 45.0F);
    }

    private static void spawnForwardWitheredDrive(ServerPlayer player) {
        EntityDrive drive = Drive.doSlash(player, -90.0F, 12, Vec3.ZERO,
                false, WITHERED_FALLBACK_DAMAGE, 2.05F);
        configureWitheredDrive(drive);
    }

    private static void spawnWitheredDrive(ServerLevel level,
            ServerPlayer player, LivingEntity target, Vec3 start,
            Vec3 direction, int delay, float roll) {
        EntityDrive sword = Drive.doSlash(player, roll, 12, Vec3.ZERO,
                false, WITHERED_SWORD_DAMAGE, 2.05F);
        if (sword == null) {
            return;
        }
        sword.setPos(start.x, start.y, start.z);
        sword.setDelay(delay);
        configureWitheredDrive(sword);
        Vec3 normalized = direction.normalize();
        sword.shoot(normalized.x, normalized.y, normalized.z, 2.05F, 0.0F);
    }

    private static void configureWitheredDrive(EntityDrive drive) {
        if (drive == null) {
            return;
        }
        drive.setColor(WITHERED_COLOR);
        drive.getPotionEffects().add(new MobEffectInstance(MobEffects.WITHER,
                WITHERED_DURATION_TICKS, WITHERED_AMPLIFIER));
        SoulLegacyDamageGuard.markSecondary(drive);
    }

    private static void spawnVoidSwords(ServerLevel level, ServerPlayer player,
            LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() < 1.0E-5D) {
            double yaw = Math.toRadians(player.getYRot());
            look = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        look = look.normalize();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x);
        Vec3[] starts = {
                center.add(right.scale(2.2D)).add(0.0D, 1.0D, 0.0D),
                center.subtract(right.scale(2.2D)).add(0.0D, 1.45D, 0.0D),
                center.subtract(look.scale(1.7D)).add(0.0D, 3.0D, 0.0D)
        };
        for (int index = 0; index < PIERCING_VOID_SWORD_COUNT; index++) {
            Vec3 start = starts[index];
            Vec3 direction = center.subtract(start).normalize();
            EntityAbstractSummonedSword sword = new EntityAbstractSummonedSword(
                    SlashBlade.RegistryEvents.SummonedSword, level);
            sword.setPos(start.x, start.y, start.z);
            sword.setOwner(player);
            sword.setShooter(player);
            sword.setHitEntity(target);
            sword.setDamage(PIERCING_VOID_SWORD_DAMAGE);
            sword.setColor(VOID_COLOR);
            sword.setRoll(index * 120.0F + 20.0F);
            sword.setDelay(index * 2);
            sword.setNoClip(true);
            SoulLegacyDamageGuard.markSecondary(sword);
            sword.shoot(direction.x, direction.y, direction.z, 2.15F, 0.0F);
            level.addFreshEntity(sword);
        }
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y, center.z, 14,
                0.55D, 0.65D, 0.55D, 0.035D);
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.62F, 1.48F);
    }

    private record PiercingCast(ResourceKey<Level> dimension, long expiresAt) {
    }

    private SignatureFusionBatchHandler() {
    }
}
