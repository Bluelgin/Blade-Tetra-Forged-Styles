package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rengeki's momentum layer: a stronger damage tradeoff for the native B chain
 * plus a small speed-scaled frontal slash while the player is sprinting idle.
 *
 * <p>The sprint slash is intentionally independent from combo progression. It
 * never rewrites ComboState, never grants ordinary chase / kill hand-off, and
 * respects normal hurt invulnerability instead of force-hitting every tick.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RengekiMomentumHandler {
    static final double NATIVE_B_DAMAGE_MULTIPLIER = 0.88D;

    static final int SPRINT_SLASH_INTERVAL_TICKS = 10;
    static final double SPRINT_SLASH_MIN_SPEED = 0.12D;
    static final double SPRINT_SLASH_SPEED_CAP = 0.36D;
    static final double SPRINT_SLASH_MIN_RANGE = 1.35D;
    static final double SPRINT_SLASH_MAX_RANGE = 2.75D;
    static final float SPRINT_SLASH_MIN_DAMAGE_RATIO = 0.06F;
    static final float SPRINT_SLASH_MAX_DAMAGE_RATIO = 0.14F;
    static final double MIN_SPRINT_SLASH_DOT = Math.cos(Math.toRadians(55.0D));

    private static final double SPRINT_SLASH_MAX_HEIGHT_DIFFERENCE = 1.25D;
    private static final float SPRINT_SLASH_MIN_VISUAL_SIZE = 0.28F;
    private static final float SPRINT_SLASH_MAX_VISUAL_SIZE = 0.58F;
    private static final double VISUAL_FORWARD_OFFSET = 0.65D;
    private static final Map<UUID, Long> NEXT_SPRINT_SLASH_AT = new HashMap<>();

    private static final TargetingConditions NATIVE_TARGET_FILTER =
            new TargetSelector.SlashBladeTargetingConditions()
                    .range(64.0D)
                    .ignoreInvisibilityTesting()
                    .selector(new TargetSelector.AttackablePredicate());

    /**
     * The native B tree and its authored recovery slashes trade more raw damage
     * for Rengeki's pursuit, kill hand-off and sprint-pressure tools. Other
     * attacks remain untouched.
     */
    @SubscribeEvent
    public static void onRengekiSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.RENGEKI
                || !RengekiShortStepHandler.isNativeBFlowState(
                        event.getSlashBladeState().getComboSeq())) {
            return;
        }

        event.setDamage(event.getDamage() * NATIVE_B_DAMAGE_MULTIPLIER);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        trySprintSlash(player);
    }

    private static void trySprintSlash(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ItemStack blade = player.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.RENGEKI) {
            NEXT_SPRINT_SLASH_AT.remove(playerId);
            return;
        }

        // The momentum slash is an idle-running tool, not a second damage layer
        // on top of B1-B7, directionals, aerials or Slash Arts.
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        if (!ComboStateRegistry.NONE.getId().equals(combo)
                || RengekiShortStepHandler.hasPendingKillTransfer(playerId)
                || !player.isSprinting()
                || !player.onGround()
                || player.isPassenger()
                || player.isUsingItem()
                || player.getAbilities().flying) {
            return;
        }

        double speed = horizontalSpeed(player);
        if (speed < SPRINT_SLASH_MIN_SPEED) {
            return;
        }

        long now = player.level().getGameTime();
        long nextAllowed = NEXT_SPRINT_SLASH_AT.getOrDefault(playerId, Long.MIN_VALUE);
        if (now < nextAllowed) {
            return;
        }
        NEXT_SPRINT_SLASH_AT.put(playerId, now + SPRINT_SLASH_INTERVAL_TICKS);

        double speedScale = speedScale(speed);
        double range = lerp(
                SPRINT_SLASH_MIN_RANGE,
                SPRINT_SLASH_MAX_RANGE,
                speedScale);
        float visualSize = (float) lerp(
                SPRINT_SLASH_MIN_VISUAL_SIZE,
                SPRINT_SLASH_MAX_VISUAL_SIZE,
                speedScale);
        float damageRatio = sprintSlashDamageRatioForSpeed(speed);

        spawnSprintSlashVisual(player, blade, visualSize, now);

        LivingEntity target = selectSprintSlashTarget(player, range);
        if (target != null) {
            applySprintSlashHit(player, target, damageRatio);
        }
    }

    /**
     * Visual-only Resharped slash. It deliberately has no shooter/owner, so the
     * base EntitySlashEffect never performs its own broad areaAttack. Real hit
     * range is handled by selectSprintSlashTarget instead of being faked by
     * BaseSize, which in Resharped is only visual scale.
     */
    private static void spawnSprintSlashVisual(
            ServerPlayer player,
            ItemStack blade,
            float visualSize,
            long gameTime) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = horizontalLook(player);
        Vec3 position = player.position()
                .add(0.0D, player.getBbHeight() * 0.55D, 0.0D)
                .add(forward.scale(VISUAL_FORWARD_OFFSET));

        EntitySlashEffect effect = new EntitySlashEffect(
                SlashBlade.RegistryEvents.SlashEffect,
                level);
        effect.setPos(position.x, position.y, position.z);
        effect.setYRot(player.getYRot());
        effect.setXRot(0.0F);
        effect.setRotationRoll((gameTime & 1L) == 0L ? -35.0F : 35.0F);
        effect.setColor(blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getColorCode())
                .orElse(0xFFFFFF));
        effect.setMute(true);
        effect.setIsCritical(false);
        effect.setBaseSize(visualSize);
        effect.setLifetime(3);
        level.addFreshEntity(effect);
    }

    /**
     * Select one target closest to the player's forward intent. Keeping this to
     * one target prevents a passive sprint from becoming a free mob-farm AoE.
     */
    private static LivingEntity selectSprintSlashTarget(
            ServerPlayer player,
            double range) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position();
        Vec3 look = horizontalLook(player);
        AABB searchBox = player.getBoundingBox().inflate(
                range,
                SPRINT_SLASH_MAX_HEIGHT_DIFFERENCE + 1.0D,
                range);

        LivingEntity best = null;
        double bestDot = MIN_SPRINT_SLASH_DOT;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                entity -> isBasicTarget(player, entity))) {
            if (Math.abs(candidate.getY() - player.getY())
                            > SPRINT_SLASH_MAX_HEIGHT_DIFFERENCE
                    || !player.hasLineOfSight(candidate)
                    || !NATIVE_TARGET_FILTER.test(player, candidate)) {
                continue;
            }

            double bodyDistanceSqr = distanceToBoxSqr(
                    origin,
                    candidate.getBoundingBox());
            if (bodyDistanceSqr > range * range) {
                continue;
            }

            Vec3 toTarget = new Vec3(
                    candidate.getX() - player.getX(),
                    0.0D,
                    candidate.getZ() - player.getZ());
            double dot;
            if (toTarget.lengthSqr() <= 1.0E-6D) {
                dot = 1.0D;
            } else {
                dot = look.dot(toTarget.normalize());
            }
            if (dot < MIN_SPRINT_SLASH_DOT) {
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

    /**
     * Reuse Resharped's melee attack path so the sprint slash scales from the
     * blade/player damage stack instead of using a fixed raw-damage ceiling.
     * Only the speed contribution is capped: the combo ratio grows from 0.06
     * to 0.14 across the bounded speed window.
     *
     * <p>The vanilla sprint-hit knockback branch is suppressed temporarily, then
     * both momentum and the exact pre-hit sprint flag are restored in finally.
     * Normal hurt invulnerability is respected: forceHit=false/resetHit=false.</p>
     */
    private static void applySprintSlashHit(
            ServerPlayer player,
            LivingEntity target,
            float damageRatio) {
        Vec3 momentum = player.getDeltaMovement();
        boolean sprinting = player.isSprinting();
        player.setSprinting(false);
        try {
            AttackManager.doMeleeAttack(
                    player,
                    target,
                    false,
                    false,
                    damageRatio);
        } finally {
            player.setDeltaMovement(momentum);
            player.setSprinting(sprinting);
        }
    }

    private static boolean isBasicTarget(
            ServerPlayer player,
            LivingEntity candidate) {
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

    static double sprintSlashRangeForSpeed(double speed) {
        return lerp(
                SPRINT_SLASH_MIN_RANGE,
                SPRINT_SLASH_MAX_RANGE,
                speedScale(speed));
    }

    static float sprintSlashDamageRatioForSpeed(double speed) {
        return (float) lerp(
                SPRINT_SLASH_MIN_DAMAGE_RATIO,
                SPRINT_SLASH_MAX_DAMAGE_RATIO,
                speedScale(speed));
    }

    private static double speedScale(double speed) {
        double clamped = Math.max(
                SPRINT_SLASH_MIN_SPEED,
                Math.min(SPRINT_SLASH_SPEED_CAP, speed));
        return (clamped - SPRINT_SLASH_MIN_SPEED)
                / (SPRINT_SLASH_SPEED_CAP - SPRINT_SLASH_MIN_SPEED);
    }

    private static double horizontalSpeed(ServerPlayer player) {
        Vec3 movement = player.getDeltaMovement();
        return Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        return horizontal.lengthSqr() <= 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : horizontal.normalize();
    }

    private static double distanceToBoxSqr(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0D), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0D), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0D), point.z - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double lerp(double min, double max, double value) {
        return min + (max - min) * value;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NEXT_SPRINT_SLASH_AT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        NEXT_SPRINT_SLASH_AT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        NEXT_SPRINT_SLASH_AT.remove(event.getOriginal().getUUID());
        NEXT_SPRINT_SLASH_AT.remove(event.getEntity().getUUID());
    }

    private RengekiMomentumHandler() {
    }
}
