package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps Rengeki on Resharped's native B combo while adding one bounded,
 * hit-gated positioning correction between successful beats.
 *
 * <p>The short-step is deliberately conservative: it never replaces combo
 * state, never adds damage, never crosses an unsupported path, and simply does
 * nothing when no safe destination exists. A failed short-step therefore falls
 * back to the untouched native B combo.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RengekiShortStepHandler {
    static final int CHASE_WINDOW_TICKS = 10;
    static final double TARGET_SEARCH_DISTANCE = 5.5D;
    static final double MAX_STEP_DISTANCE = 2.75D;
    static final double MAX_HEIGHT_DIFFERENCE = 1.25D;
    static final double MIN_TARGET_DOT = Math.cos(Math.toRadians(50.0D));

    private static final double MIN_STEP_DISTANCE = 0.15D;
    private static final double SUPPORT_PROBE = 0.08D;
    private static final double PATH_SAMPLE_SPACING = 0.35D;
    private static final double TARGET_STANDOFF_EXTRA = 0.35D;
    private static final double CHUNK_EDGE_EPSILON = 1.0E-6D;

    /**
     * Preserve Resharped's full area-attack eligibility semantics (including
     * PVP/friendly config and its revenge-target exception) while leaving the
     * actual 5.5-block pursuit range to our collision-box distance check.
     */
    private static final TargetingConditions NATIVE_TARGET_FILTER =
            new TargetSelector.SlashBladeTargetingConditions()
                    .range(64.0D)
                    .ignoreInvisibilityTesting()
                    .selector(new TargetSelector.AttackablePredicate());
    private static final Map<UUID, ChaseWindow> CHASE_WINDOWS = new HashMap<>();

    /**
     * Only successful, non-terminal native B-series hits arm the chase.
     * B7 is deliberately excluded because it has no following B beat.
     */
    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.RENGEKI
                || !canAdvanceBComboId(event.getSlashBladeState().getComboSeq())) {
            return;
        }

        CHASE_WINDOWS.put(
                player.getUUID(),
                new ChaseWindow(
                        player.level().getGameTime() + CHASE_WINDOW_TICKS,
                        event.getBlade()));
    }

    /**
     * Resharped posts BladeMotionEvent from its authoritative updateComboSeq
     * path before the next combo node is installed. Hooking the real B-to-B
     * transition avoids guessing at client input synchronization: MoveInput's
     * raw command packet contains L_DOWN/R_DOWN, while transient L_CLICK/R_CLICK
     * are injected directly by ItemSlashBlade when progressCombo is called.
     *
     * <p>Any other combo transition consumes the stored opportunity without
     * pursuit, so direction/aerial/SA/timeout breakouts cannot leave a stale
     * teleport waiting for a later attack.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBladeMotion(BladeMotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        ItemStack blade = player.getMainHandItem();
        if (event.isCanceled()
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.RENGEKI) {
            CHASE_WINDOWS.remove(playerId);
            return;
        }

        ChaseWindow window = CHASE_WINDOWS.remove(playerId);
        if (window == null
                || window.expiresAt() < player.level().getGameTime()
                || window.blade() != blade) {
            return;
        }

        ResourceLocation current = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        ResourceLocation next = event.getCombo();
        if (!isExpectedBAdvance(current, next)) {
            return;
        }

        // Native B nodes can survive a short airborne interval. Pursuit is a
        // grounded style mechanic, so never use it to snap a falling/riding
        // player back to ground or erase fall momentum.
        if (!player.onGround() || player.isPassenger()) {
            return;
        }

        LivingEntity target = selectTarget(player);
        if (target == null) {
            return;
        }

        Vec3 destination = findSafeDestination(player, target);
        if (destination == null) {
            return;
        }

        player.connection.teleport(
                destination.x,
                destination.y,
                destination.z,
                player.getYRot(),
                player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHASE_WINDOWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CHASE_WINDOWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CHASE_WINDOWS.remove(event.getOriginal().getUUID());
        CHASE_WINDOWS.remove(event.getEntity().getUUID());
    }

    /**
     * The current combo is still installed while BladeMotionEvent is being
     * dispatched. Only these six native transitions are ordinary Rengeki
     * continuation inputs; every other transition is a breakout or timeout.
     */
    static boolean isExpectedBAdvance(ResourceLocation current, ResourceLocation next) {
        return (ComboStateRegistry.COMBO_B1.getId().equals(current)
                        && ComboStateRegistry.COMBO_B2.getId().equals(next))
                || (ComboStateRegistry.COMBO_B2.getId().equals(current)
                        && ComboStateRegistry.COMBO_B3.getId().equals(next))
                || (ComboStateRegistry.COMBO_B3.getId().equals(current)
                        && ComboStateRegistry.COMBO_B4.getId().equals(next))
                || (ComboStateRegistry.COMBO_B4.getId().equals(current)
                        && ComboStateRegistry.COMBO_B5.getId().equals(next))
                || (ComboStateRegistry.COMBO_B5.getId().equals(current)
                        && ComboStateRegistry.COMBO_B6.getId().equals(next))
                || (ComboStateRegistry.COMBO_B6.getId().equals(current)
                        && ComboStateRegistry.COMBO_B7.getId().equals(next));
    }

    private static boolean canAdvanceBComboId(ResourceLocation combo) {
        return ComboStateRegistry.COMBO_B1.getId().equals(combo)
                || ComboStateRegistry.COMBO_B2.getId().equals(combo)
                || ComboStateRegistry.COMBO_B3.getId().equals(combo)
                || ComboStateRegistry.COMBO_B4.getId().equals(combo)
                || ComboStateRegistry.COMBO_B5.getId().equals(combo)
                || ComboStateRegistry.COMBO_B6.getId().equals(combo);
    }

    private static LivingEntity selectTarget(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        AABB searchBox = player.getBoundingBox().inflate(
                TARGET_SEARCH_DISTANCE,
                MAX_HEIGHT_DIFFERENCE + 1.0D,
                TARGET_SEARCH_DISTANCE);

        LivingEntity best = null;
        double bestDot = MIN_TARGET_DOT;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> isBasicTarget(player, entity))) {
            if (Math.abs(candidate.getY() - player.getY()) > MAX_HEIGHT_DIFFERENCE
                    || !player.hasLineOfSight(candidate)) {
                continue;
            }

            double bodyDistanceSqr = distanceToBoxSqr(player.position(), candidate.getBoundingBox());
            if (bodyDistanceSqr > TARGET_SEARCH_DISTANCE * TARGET_SEARCH_DISTANCE) {
                continue;
            }

            Vec3 targetCenter = new Vec3(
                    candidate.getX(),
                    candidate.getY() + candidate.getBbHeight() * 0.5D,
                    candidate.getZ());
            Vec3 toTarget = targetCenter.subtract(eye);
            if (toTarget.lengthSqr() <= 1.0E-6D) {
                continue;
            }

            double dot = look.dot(toTarget.normalize());
            if (dot < MIN_TARGET_DOT || !NATIVE_TARGET_FILTER.test(player, candidate)) {
                continue;
            }

            if (best == null
                    || dot > bestDot + 1.0E-4D
                    || (Math.abs(dot - bestDot) <= 1.0E-4D
                            && bodyDistanceSqr < bestDistanceSqr)) {
                best = candidate;
                bestDot = dot;
                bestDistanceSqr = bodyDistanceSqr;
            }
        }

        return best;
    }

    private static boolean isBasicTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player
                || !candidate.isAlive()
                || candidate.isSpectator()
                || !candidate.isAttackable()
                || candidate.isAlliedTo(player)
                || player.isAlliedTo(candidate)) {
            return false;
        }
        return !(candidate instanceof Player other) || player.canHarmPlayer(other);
    }

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0D), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0D), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0D), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static Vec3 findSafeDestination(ServerPlayer player, LivingEntity target) {
        if (Math.abs(target.getY() - player.getY()) > MAX_HEIGHT_DIFFERENCE) {
            return null;
        }

        Vec3 origin = player.position();
        Vec3 horizontal = new Vec3(
                target.getX() - origin.x,
                0.0D,
                target.getZ() - origin.z);
        double horizontalDistance = horizontal.length();
        if (horizontalDistance <= 1.0E-6D) {
            return null;
        }

        double standOff = Math.max(
                1.2D,
                target.getBbWidth() * 0.5D
                        + player.getBbWidth() * 0.5D
                        + TARGET_STANDOFF_EXTRA);
        double requestedStep = Math.min(
                MAX_STEP_DISTANCE,
                Math.max(0.0D, horizontalDistance - standOff));
        if (requestedStep < MIN_STEP_DISTANCE) {
            return null;
        }

        Vec3 direction = horizontal.normalize();
        Vec3 destination = origin.add(direction.scale(requestedStep));
        return isSafeGroundPath(player, origin, destination)
                ? destination
                : null;
    }

    /**
     * Sample the complete short-step path instead of validating only the end
     * point. A wall, unloaded chunk, missing floor or gap at any sample cancels
     * the movement and leaves the native B combo untouched.
     */
    private static boolean isSafeGroundPath(
            ServerPlayer player,
            Vec3 origin,
            Vec3 destination) {
        ServerLevel level = player.serverLevel();
        Vec3 delta = destination.subtract(origin);
        double distance = delta.length();
        int samples = Math.max(1, (int) Math.ceil(distance / PATH_SAMPLE_SPACING));
        AABB baseBox = player.getBoundingBox();

        for (int i = 1; i <= samples; i++) {
            double fraction = i / (double) samples;
            Vec3 sample = origin.add(delta.scale(fraction));
            AABB sampleBox = baseBox.move(
                    sample.x - player.getX(),
                    sample.y - player.getY(),
                    sample.z - player.getZ());

            if (!isCollisionAreaLoaded(level, sampleBox)
                    || !level.noCollision(player, sampleBox)) {
                return false;
            }
            if (level.noCollision(player, sampleBox.move(0.0D, -SUPPORT_PROBE, 0.0D))) {
                return false;
            }
        }

        return true;
    }

    /** A player's box may straddle a neighboring chunk even when its center does not. */
    private static boolean isCollisionAreaLoaded(ServerLevel level, AABB box) {
        double minX = box.minX + CHUNK_EDGE_EPSILON;
        double maxX = box.maxX - CHUNK_EDGE_EPSILON;
        double minZ = box.minZ + CHUNK_EDGE_EPSILON;
        double maxZ = box.maxZ - CHUNK_EDGE_EPSILON;
        double y = box.minY;

        return level.hasChunkAt(BlockPos.containing(minX, y, minZ))
                && level.hasChunkAt(BlockPos.containing(minX, y, maxZ))
                && level.hasChunkAt(BlockPos.containing(maxX, y, minZ))
                && level.hasChunkAt(BlockPos.containing(maxX, y, maxZ));
    }

    private record ChaseWindow(long expiresAt, ItemStack blade) {
    }

    private RengekiShortStepHandler() {
    }
}
