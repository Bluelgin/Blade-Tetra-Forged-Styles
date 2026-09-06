package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapts Resharped's Air Trick to grounded Iaido without replacing the native
 * ability. Resharped still owns targeting, timing, effects and avoidance; this
 * handler only corrects a successful teleport to a safe, attackable ground
 * position for modular Iaido blades.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IaidoTrickHandler {
    private static final double APPROACH_BONUS_MIN_DISTANCE = 3.0D;
    private static final int APPROACH_BONUS_TICKS = 12;
    private static final int RESIDUAL_TICKS = 8;
    private static final int PENDING_LIFETIME_TICKS = 10;
    private static final double SUPPORT_PROBE = 0.08D;
    private static final double[] ANGLE_OFFSETS = {0.0D, 30.0D, -30.0D, 60.0D, -60.0D, 90.0D, -90.0D, 180.0D};
    private static final double[] HEIGHT_OFFSETS = {0.0D, 0.25D, -0.25D, 0.5D, -0.5D, 0.75D, -0.75D, 1.0D, -1.0D, -1.5D, -2.0D};

    private static final String APPROACH_UNTIL = "blade_tetra_iaido_approach_until";
    private static final String APPROACH_TARGET = "blade_tetra_iaido_approach_target";
    private static final String RESIDUAL_UNTIL = "blade_tetra_iaido_residual_until";
    private static final String RESIDUAL_TARGET = "blade_tetra_iaido_residual_target";

    private static final Map<UUID, PendingAirTrick> PENDING = new HashMap<>();

    /**
     * Runs after Resharped's own input listener. A successful forward Air Trick
     * has already populated its bounded counter and target id at this point.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInputChange(InputCommandEvent event) {
        ServerPlayer player = event.getEntity();
        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            PENDING.remove(player.getUUID());
            clearCombatWindows(player.getPersistentData());
            return;
        }

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        if (isNativeBackTrick(event)
                && data.getLong(RESIDUAL_UNTIL) >= now
                && data.contains(SlayerStyleArts.AVOID_COUNTER_PATH, Tag.TAG_INT)) {
            blade.getCapability(ModularSlashBladeItem.BLADESTATE).ifPresent(
                    state -> state.updateComboSeq(player, ModComboStates.IAIDO_SHEATHE.getId()));
            clearResidual(data);
            clearApproach(data);
        }

        if (!isNativeForwardTrick(event)
                || !data.contains(SlayerStyleArts.AIRTRICK_COUNTER_PATH, Tag.TAG_INT)
                || !data.contains(SlayerStyleArts.AIRTRICK_TARGET_PATH, Tag.TAG_INT)) {
            return;
        }

        int targetId = data.getInt(SlayerStyleArts.AIRTRICK_TARGET_PATH);
        Entity target = player.level().getEntity(targetId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        PENDING.put(player.getUUID(), new PendingAirTrick(
                player.position(),
                targetId,
                player.distanceTo(living),
                now + PENDING_LIFETIME_TICKS));
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        expireWindows(data, now);

        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            PENDING.remove(player.getUUID());
            clearCombatWindows(data);
            return;
        }

        captureDelayedAirTrick(player, data, now);

        PendingAirTrick pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return;
        }
        if (now > pending.expiresAt()) {
            PENDING.remove(player.getUUID());
            return;
        }

        // Resharped clears this counter only after its delayed teleport has run.
        if (data.contains(SlayerStyleArts.AIRTRICK_COUNTER_PATH, Tag.TAG_INT)) {
            return;
        }

        Entity target = player.level().getEntity(pending.targetId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            PENDING.remove(player.getUUID());
            return;
        }

        // A failed Air Trick leaves the player at the recorded origin.
        if (player.position().distanceToSqr(pending.origin()) < 0.25D) {
            PENDING.remove(player.getUUID());
            return;
        }

        Vec3 corrected = findSafeApproach(player, living, pending.origin());
        if (corrected != null) {
            faceAndTeleport(player, living, corrected);
            StyleCombatHandler.primeIaidoTarget(player, living, now + APPROACH_BONUS_TICKS);
            if (pending.initialDistance() >= APPROACH_BONUS_MIN_DISTANCE) {
                data.putLong(APPROACH_UNTIL, now + APPROACH_BONUS_TICKS);
                data.putUUID(APPROACH_TARGET, living.getUUID());
            }
        }
        PENDING.remove(player.getUUID());
    }

    /**
     * The native ability may launch a summoned sword first and only schedule
     * its teleport when that sword reaches the lock-on target. Capture that
     * delayed path as soon as Resharped exposes the same bounded counter.
     */
    private static void captureDelayedAirTrick(
            ServerPlayer player,
            CompoundTag data,
            long now) {
        if (PENDING.containsKey(player.getUUID())
                || !data.contains(SlayerStyleArts.AIRTRICK_COUNTER_PATH, Tag.TAG_INT)
                || !data.contains(SlayerStyleArts.AIRTRICK_TARGET_PATH, Tag.TAG_INT)) {
            return;
        }
        int targetId = data.getInt(SlayerStyleArts.AIRTRICK_TARGET_PATH);
        Entity target = player.level().getEntity(targetId);
        if (target instanceof LivingEntity living && living.isAlive()) {
            PENDING.put(player.getUUID(), new PendingAirTrick(
                    player.position(),
                    targetId,
                    player.distanceTo(living),
                    now + PENDING_LIFETIME_TICKS));
        }
    }

    public static boolean consumeApproachBonus(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        boolean active = data.getLong(APPROACH_UNTIL) >= user.level().getGameTime()
                && data.hasUUID(APPROACH_TARGET);
        clearApproach(data);
        return active;
    }

    public static void markIaidoHit(LivingEntity user, LivingEntity target) {
        CompoundTag data = user.getPersistentData();
        long now = user.level().getGameTime();
        data.putLong(RESIDUAL_UNTIL, now + RESIDUAL_TICKS);
        data.putUUID(RESIDUAL_TARGET, target.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        clearCombatWindows(event.getEntity().getPersistentData());
    }

    private static boolean isNativeForwardTrick(InputCommandEvent event) {
        return event.getCurrent().contains(InputCommand.FORWARD)
                && event.getCurrent().contains(InputCommand.SNEAK)
                && event.getCurrent().contains(InputCommand.SPRINT);
    }

    private static boolean isNativeBackTrick(InputCommandEvent event) {
        return event.getCurrent().contains(InputCommand.BACK)
                && event.getCurrent().contains(InputCommand.SNEAK)
                && event.getCurrent().contains(InputCommand.SPRINT);
    }

    private static Vec3 findSafeApproach(
            ServerPlayer player,
            LivingEntity target,
            Vec3 origin) {
        Vec3 radial = new Vec3(
                origin.x - target.getX(),
                0.0D,
                origin.z - target.getZ());
        if (radial.lengthSqr() < 1.0E-6D) {
            Vec3 targetLook = target.getLookAngle();
            radial = new Vec3(targetLook.x, 0.0D, targetLook.z);
        }
        if (radial.lengthSqr() < 1.0E-6D) {
            radial = new Vec3(0.0D, 0.0D, 1.0D);
        }
        radial = radial.normalize();

        double contactDistance = Math.max(
                1.2D,
                target.getBbWidth() * 0.5D
                        + player.getBbWidth() * 0.5D
                        + 0.35D);
        AABB currentBox = player.getBoundingBox();

        for (double angle : ANGLE_OFFSETS) {
            Vec3 direction = rotateHorizontal(radial, angle);
            double x = target.getX() + direction.x * contactDistance;
            double z = target.getZ() + direction.z * contactDistance;
            for (double heightOffset : HEIGHT_OFFSETS) {
                double y = target.getY() + heightOffset;
                BlockPos blockPos = BlockPos.containing(x, y, z);
                if (!player.serverLevel().hasChunkAt(blockPos)) {
                    continue;
                }
                AABB candidateBox = currentBox.move(
                        x - player.getX(),
                        y - player.getY(),
                        z - player.getZ());
                if (player.serverLevel().noCollision(player, candidateBox)
                        && !player.serverLevel().noCollision(
                                player, candidateBox.move(0.0D, -SUPPORT_PROBE, 0.0D))) {
                    return new Vec3(x, y, z);
                }
            }
        }
        return null;
    }

    private static void faceAndTeleport(
            ServerPlayer player,
            LivingEntity target,
            Vec3 destination) {
        double dx = target.getX() - destination.x;
        double dz = target.getZ() - destination.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double targetCenterY = target.getY() + target.getBbHeight() * 0.5D;
        double playerEyeY = destination.y + player.getEyeHeight();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Mth.atan2(targetCenterY - playerEyeY, horizontal)
                * Mth.RAD_TO_DEG));

        player.connection.teleport(
                destination.x,
                destination.y,
                destination.z,
                yaw,
                Mth.clamp(pitch, -35.0F, 35.0F));
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }

    private static Vec3 rotateHorizontal(Vec3 vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vec3(
                vector.x * cosine - vector.z * sine,
                0.0D,
                vector.x * sine + vector.z * cosine);
    }

    private static void expireWindows(CompoundTag data, long now) {
        if (data.contains(APPROACH_UNTIL, Tag.TAG_LONG)
                && data.getLong(APPROACH_UNTIL) < now) {
            clearApproach(data);
        }
        if (data.contains(RESIDUAL_UNTIL, Tag.TAG_LONG)
                && data.getLong(RESIDUAL_UNTIL) < now) {
            clearResidual(data);
        }
    }

    private static void clearApproach(CompoundTag data) {
        data.remove(APPROACH_UNTIL);
        data.remove(APPROACH_TARGET);
    }

    private static void clearResidual(CompoundTag data) {
        data.remove(RESIDUAL_UNTIL);
        data.remove(RESIDUAL_TARGET);
    }

    private static void clearCombatWindows(CompoundTag data) {
        clearApproach(data);
        clearResidual(data);
    }

    private record PendingAirTrick(
            Vec3 origin,
            int targetId,
            double initialDistance,
            long expiresAt) {
    }

    private IaidoTrickHandler() {
    }
}
