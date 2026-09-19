package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
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

    private static final Map<UUID, Long> CHASE_WINDOWS = new HashMap<>();

    /**
     * Only native B-series hits arm the chase. Directional attacks, aerial
     * attacks and Slash Arts can still be used normally, but do not create a
     * Rengeki short-step window.
     */
    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)
                || player.level().isClientSide()
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.RENGEKI
                || !isNativeBCombo(event.getSlashBladeState().getComboSeq())) {
            return;
        }

        CHASE_WINDOWS.put(
                player.getUUID(),
                player.level().getGameTime() + CHASE_WINDOW_TICKS);
    }

    /**
     * InputCommandEvent is server-authoritative and fires before the next combo
     * beat is resolved. We consume the chase on the next fresh attack press so
     * repeated callbacks cannot teleport the player more than once per hit.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInputChange(InputCommandEvent event) {
        ServerPlayer player = event.getEntity();
        ItemStack blade = player.getMainHandItem();

        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.RENGEKI) {
            CHASE_WINDOWS.remove(player.getUUID());
            return;
        }

        if (!isFreshAttackPress(event)) {
            return;
        }

        Long expiresAt = CHASE_WINDOWS.remove(player.getUUID());
        if (expiresAt == null || expiresAt < player.level().getGameTime()) {
            return;
        }

        // Any deliberate alternate attack consumes the opportunity but does
        // not receive pursuit. This prevents a stored chase from firing later
        // after the player intentionally broke out into a direction/aerial move.
        if (!isOrdinaryGroundAttack(event.getCurrent())
                || !canAdvanceNativeBCombo(blade)) {
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

    private static boolean isFreshAttackPress(InputCommandEvent event) {
        return (event.getCurrent().contains(InputCommand.L_CLICK)
                        && !event.getOld().contains(InputCommand.L_CLICK))
                || (event.getCurrent().contains(InputCommand.R_CLICK)
                        && !event.getOld().contains(InputCommand.R_CLICK));
    }

    private static boolean isOrdinaryGroundAttack(EnumSet<InputCommand> commands) {
        if (!commands.contains(InputCommand.ON_GROUND)
                || commands.contains(InputCommand.ON_AIR)) {
            return false;
        }

        if (commands.contains(InputCommand.R_CLICK)
                && commands.contains(InputCommand.SNEAK)
                && (commands.contains(InputCommand.FORWARD)
                        || commands.contains(InputCommand.BACK))) {
            return false;
        }

        return commands.contains(InputCommand.L_CLICK)
                || commands.contains(InputCommand.R_CLICK);
    }

    private static boolean canAdvanceNativeBCombo(ItemStack blade) {
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return ComboStateRegistry.COMBO_B1.getId().equals(combo)
                || ComboStateRegistry.COMBO_B2.getId().equals(combo)
                || ComboStateRegistry.COMBO_B3.getId().equals(combo)
                || ComboStateRegistry.COMBO_B4.getId().equals(combo)
                || ComboStateRegistry.COMBO_B5.getId().equals(combo)
                || ComboStateRegistry.COMBO_B6.getId().equals(combo);
    }

    private static boolean isNativeBCombo(ResourceLocation combo) {
        return canAdvanceBComboId(combo)
                || ComboStateRegistry.COMBO_B7.getId().equals(combo);
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
                entity -> isValidTarget(player, entity))) {
            Vec3 targetCenter = new Vec3(
                    candidate.getX(),
                    candidate.getY() + candidate.getBbHeight() * 0.5D,
                    candidate.getZ());
            Vec3 toTarget = targetCenter.subtract(eye);
            double distanceSqr = toTarget.lengthSqr();
            if (distanceSqr <= 1.0E-6D
                    || distanceSqr > TARGET_SEARCH_DISTANCE * TARGET_SEARCH_DISTANCE
                    || Math.abs(candidate.getY() - player.getY()) > MAX_HEIGHT_DIFFERENCE
                    || !player.hasLineOfSight(candidate)) {
                continue;
            }

            double dot = look.dot(toTarget.normalize());
            if (dot < MIN_TARGET_DOT) {
                continue;
            }

            if (best == null
                    || dot > bestDot + 1.0E-4D
                    || (Math.abs(dot - bestDot) <= 1.0E-4D
                            && distanceSqr < bestDistanceSqr)) {
                best = candidate;
                bestDot = dot;
                bestDistanceSqr = distanceSqr;
            }
        }

        return best;
    }

    private static boolean isValidTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player
                || !candidate.isAlive()
                || candidate.isSpectator()
                || !candidate.isAttackable()
                || candidate.isAlliedTo(player)) {
            return false;
        }
        return !(candidate instanceof Player other) || player.canHarmPlayer(other);
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
            if (!level.hasChunkAt(BlockPos.containing(sample))) {
                return false;
            }

            AABB sampleBox = baseBox.move(
                    sample.x - player.getX(),
                    sample.y - player.getY(),
                    sample.z - player.getZ());
            if (!level.noCollision(player, sampleBox)) {
                return false;
            }
            if (level.noCollision(player, sampleBox.move(0.0D, -SUPPORT_PROBE, 0.0D))) {
                return false;
            }
        }

        return true;
    }

    private RengekiShortStepHandler() {
    }
}
