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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps Rengeki on Resharped's native B combo while adding bounded movement
 * continuity between successful beats and across confirmed kills.
 *
 * <p>The style deliberately trades a small amount of native-B damage for
 * stronger flow. Normal successful hits can earn one conservative short-step
 * on the next B advance; a confirmed kill can instead hand the player off to
 * the next valid target without adding another attack or rewriting combo
 * state.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RengekiShortStepHandler {
    static final double NATIVE_B_DAMAGE_MULTIPLIER = 0.92D;
    static final int CHASE_WINDOW_TICKS = 10;
    static final int KILL_TRANSFER_DELAY_TICKS = 1;
    static final int KILL_TRANSFER_WINDOW_TICKS = 6;
    static final double TARGET_SEARCH_DISTANCE = 5.5D;
    static final double KILL_TRANSFER_SEARCH_DISTANCE = 6.5D;
    static final double MAX_STEP_DISTANCE = 2.75D;
    static final double MAX_KILL_TRANSFER_DISTANCE = 4.5D;
    static final double MAX_HEIGHT_DIFFERENCE = 1.25D;
    static final double MIN_TARGET_DOT = Math.cos(Math.toRadians(50.0D));
    static final double MIN_KILL_TRANSFER_DOT = Math.cos(Math.toRadians(80.0D));

    private static final double MIN_STEP_DISTANCE = 0.15D;
    private static final double SUPPORT_PROBE = 0.08D;
    private static final double PATH_SAMPLE_SPACING = 0.35D;
    private static final double TARGET_STANDOFF_EXTRA = 0.35D;
    private static final double CHUNK_EDGE_EPSILON = 1.0E-6D;

    /**
     * Preserve Resharped's full area-attack eligibility semantics (including
     * PVP/friendly config and its revenge-target exception) while leaving our
     * pursuit ranges to the collision-box distance checks below.
     */
    private static final TargetingConditions NATIVE_TARGET_FILTER =
            new TargetSelector.SlashBladeTargetingConditions()
                    .range(64.0D)
                    .ignoreInvisibilityTesting()
                    .selector(new TargetSelector.AttackablePredicate());
    private static final Map<UUID, ChaseWindow> CHASE_WINDOWS = new HashMap<>();
    private static final Map<UUID, KillTransfer> KILL_TRANSFERS = new HashMap<>();

    /**
     * Rengeki's native B chain gets a small per-slash damage reduction in
     * exchange for its much stronger positioning continuity. The authored B
     * recovery nodes can still emit their own finishing slash effects, so they
     * are included in the same damage tradeoff. Directional, aerial and Slash
     * Art attacks are intentionally untouched.
     */
    @SubscribeEvent
    public static void onRengekiSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.RENGEKI
                || !isNativeBFlowState(event.getSlashBladeState().getComboSeq())) {
            return;
        }
        event.setDamage(event.getDamage() * NATIVE_B_DAMAGE_MULTIPLIER);
    }

    /**
     * HitEvent arrives after Resharped has applied the melee damage. Native B
     * slash effects can land a few ticks after they were created, so the raw
     * combo may already be in combo_b1_end / combo_b_end / combo_b7_end when a
     * late slash lands. Those native B recovery states are therefore treated as
     * valid B provenance for kill hand-off.
     *
     * <p>If the player deliberately interrupts into a directional move, aerial
     * move or Slash Art, the current state is no longer a B flow state and the
     * old residual slash cannot force an unexpected teleport. This preserves
     * player intent while still covering normal delayed B hits.</p>
     */
    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.RENGEKI) {
            return;
        }

        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        if (!isNativeBFlowState(combo)) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!event.getTarget().isAlive() || event.getTarget().getHealth() <= 0.0F) {
            scheduleKillTransfer(player, event.getBlade());
            return;
        }

        // Recovery nodes may receive delayed non-lethal hits, but they cannot
        // advance B1 -> B2 etc. Only active B1-B6 nodes arm ordinary chase.
        if (KILL_TRANSFERS.containsKey(playerId) || !canAdvanceBComboId(combo)) {
            return;
        }

        CHASE_WINDOWS.put(
                playerId,
                new ChaseWindow(
                        player.level().getGameTime() + CHASE_WINDOW_TICKS,
                        event.getBlade()));
    }

    private static void scheduleKillTransfer(ServerPlayer player, ItemStack blade) {
        UUID playerId = player.getUUID();
        long now = player.level().getGameTime();
        CHASE_WINDOWS.remove(playerId);
        KILL_TRANSFERS.put(
                playerId,
                new KillTransfer(
                        now + KILL_TRANSFER_DELAY_TICKS,
                        now + KILL_TRANSFER_WINDOW_TICKS,
                        blade));
    }

    /**
     * Resharped posts BladeMotionEvent from its authoritative updateComboSeq
     * path before the next combo node is installed. Hooking the real B-to-B
     * transition avoids guessing at client input synchronization: MoveInput's
     * raw command packet contains L_DOWN/R_DOWN, while transient L_CLICK/R_CLICK
     * are injected directly by ItemSlashBlade when progressCombo is called.
     *
     * <p>A pending kill hand-off has priority over the ordinary chase. This is
     * important when the player presses the next B beat immediately after a
     * kill: the stronger hand-off happens before that next beat instead of
     * waiting for the one-tick automatic fallback.</p>
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
            clearTransientState(playerId);
            return;
        }

        ResourceLocation current = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        ResourceLocation next = event.getCombo();
        boolean expectedBAdvance = isExpectedBAdvance(current, next);

        KillTransfer killTransfer = KILL_TRANSFERS.get(playerId);
        if (killTransfer != null) {
            if (killTransfer.expiresAt() < player.level().getGameTime()
                    || killTransfer.blade() != blade
                    || !expectedBAdvance) {
                KILL_TRANSFERS.remove(playerId);
            } else if (canGroundTransfer(player)) {
                KILL_TRANSFERS.remove(playerId);
                CHASE_WINDOWS.remove(playerId);
                tryMoveToTarget(
                        player,
                        KILL_TRANSFER_SEARCH_DISTANCE,
                        MIN_KILL_TRANSFER_DOT,
                        MAX_KILL_TRANSFER_DISTANCE);
                return;
            }
        }

        ChaseWindow window = CHASE_WINDOWS.remove(playerId);
        if (window == null
                || window.expiresAt() < player.level().getGameTime()
                || window.blade() != blade
                || !expectedBAdvance
                || !canGroundTransfer(player)) {
            return;
        }

        tryMoveToTarget(
                player,
                TARGET_SEARCH_DISTANCE,
                MIN_TARGET_DOT,
                MAX_STEP_DISTANCE);
    }

    /**
     * If the player does not immediately press the next B beat after a kill,
     * perform the hand-off one server tick later. Deferring by one tick avoids
     * moving the attacker while Resharped is still iterating the current
     * slash-effect hit list.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        KillTransfer transfer = KILL_TRANSFERS.get(playerId);
        if (transfer == null) {
            return;
        }

        long now = player.level().getGameTime();
        if (now < transfer.readyAt()) {
            return;
        }

        KILL_TRANSFERS.remove(playerId);
        CHASE_WINDOWS.remove(playerId);
        ItemStack blade = player.getMainHandItem();
        if (now > transfer.expiresAt()
                || transfer.blade() != blade
                || !(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.RENGEKI
                || !canGroundTransfer(player)) {
            return;
        }

        tryMoveToTarget(
                player,
                KILL_TRANSFER_SEARCH_DISTANCE,
                MIN_KILL_TRANSFER_DOT,
                MAX_KILL_TRANSFER_DISTANCE);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearTransientState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearTransientState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        clearTransientState(event.getOriginal().getUUID());
        clearTransientState(event.getEntity().getUUID());
    }

    private static void clearTransientState(UUID playerId) {
        CHASE_WINDOWS.remove(playerId);
        KILL_TRANSFERS.remove(playerId);
    }

    private static boolean canGroundTransfer(ServerPlayer player) {
        // Native B nodes can survive a short airborne interval. Pursuit is a
        // grounded style mechanic, so never use it to snap a falling/riding
        // player back to ground or erase fall momentum.
        return player.onGround() && !player.isPassenger();
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

    static boolean isNativeBCombo(ResourceLocation combo) {
        return canAdvanceBComboId(combo)
                || ComboStateRegistry.COMBO_B7.getId().equals(combo);
    }

    /**
     * Active B nodes plus the authored B recovery states. These tail states are
     * where delayed native-B slash effects can still land after the attack node
     * itself has timed out.
     */
    static boolean isNativeBFlowState(ResourceLocation combo) {
        return isNativeBCombo(combo)
                || ComboStateRegistry.COMBO_B1_END.getId().equals(combo)
                || ComboStateRegistry.COMBO_B1_END2.getId().equals(combo)
                || ComboStateRegistry.COMBO_B1_END3.getId().equals(combo)
                || ComboStateRegistry.COMBO_B_END.getId().equals(combo)
                || ComboStateRegistry.COMBO_B_END2.getId().equals(combo)
                || ComboStateRegistry.COMBO_B_END3.getId().equals(combo)
                || ComboStateRegistry.COMBO_B7_END3.getId().equals(combo);
    }

    private static boolean canAdvanceBComboId(ResourceLocation combo) {
        return ComboStateRegistry.COMBO_B1.getId().equals(combo)
                || ComboStateRegistry.COMBO_B2.getId().equals(combo)
                || ComboStateRegistry.COMBO_B3.getId().equals(combo)
                || ComboStateRegistry.COMBO_B4.getId().equals(combo)
                || ComboStateRegistry.COMBO_B5.getId().equals(combo)
                || ComboStateRegistry.COMBO_B6.getId().equals(combo);
    }

    private static boolean tryMoveToTarget(
            ServerPlayer player,
            double searchDistance,
            double minDot,
            double maxStepDistance) {
        LivingEntity target = selectTarget(player, searchDistance, minDot);
        if (target == null) {
            return false;
        }

        Vec3 destination = findSafeDestination(player, target, maxStepDistance);
        if (destination == null) {
            return false;
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
        return true;
    }

    private static LivingEntity selectTarget(
            ServerPlayer player,
            double searchDistance,
            double minDot) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        AABB searchBox = player.getBoundingBox().inflate(
                searchDistance,
                MAX_HEIGHT_DIFFERENCE + 1.0D,
                searchDistance);

        LivingEntity best = null;
        double bestDot = minDot;
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
            if (bodyDistanceSqr > searchDistance * searchDistance) {
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
            if (dot < minDot || !NATIVE_TARGET_FILTER.test(player, candidate)) {
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

    private static Vec3 findSafeDestination(
            ServerPlayer player,
            LivingEntity target,
            double maxStepDistance) {
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
                maxStepDistance,
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
     * Sample the complete movement path instead of validating only the end
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

    private record KillTransfer(long readyAt, long expiresAt, ItemStack blade) {
    }

    private RengekiShortStepHandler() {
    }
}
