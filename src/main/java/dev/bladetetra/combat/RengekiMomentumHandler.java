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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rengeki's momentum layer: a stronger damage tradeoff for the native B chain
 * plus a speed-scaled, B-styled visual flurry while the player keeps sprinting
 * in neutral.
 *
 * <p>The sprint flow deliberately does not install or advance a real ComboState.
 * Its B1-B7 visual rhythm is decoupled from a denser single-target hit pulse, so
 * the passive feels like an actual running flurry instead of many visual slashes
 * hiding one sparse damage event. Real hits remain bounded, frontal and silent.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RengekiMomentumHandler {
    static final double NATIVE_B_DAMAGE_MULTIPLIER = 0.85D;

    static final int SPRINT_VISUAL_INTERVAL_TICKS = 10;
    static final int SPRINT_HIT_INTERVAL_TICKS = 4;
    static final int SPRINT_B_CHAIN_LENGTH = 7;
    static final int SPRINT_B_BURST_TICKS = 7;
    static final int SPRINT_DURABILITY_DIVISOR = 10;
    static final double SPRINT_SLASH_MIN_SPEED = 0.12D;
    static final double SPRINT_SLASH_SPEED_CAP = 0.36D;
    static final double SPRINT_SLASH_MIN_RANGE = 1.35D;
    static final double SPRINT_SLASH_MAX_RANGE = 2.75D;
    static final float SPRINT_SLASH_MIN_DAMAGE_RATIO = 0.03F;
    static final float SPRINT_SLASH_MAX_DAMAGE_RATIO = 0.07F;
    static final double MIN_SPRINT_SLASH_DOT = Math.cos(Math.toRadians(55.0D));

    private static final double SPRINT_SLASH_MAX_HEIGHT_DIFFERENCE = 1.25D;
    private static final float SPRINT_SLASH_MIN_VISUAL_SIZE = 0.28F;
    private static final float SPRINT_SLASH_MAX_VISUAL_SIZE = 0.58F;
    private static final double VISUAL_FORWARD_OFFSET = 0.65D;
    private static final Map<UUID, MovementSample> MOVEMENT_SAMPLES = new HashMap<>();
    private static final Map<UUID, SprintChainState> SPRINT_CHAINS = new HashMap<>();
    private static final Map<UUID, Integer> SPRINT_DURABILITY_PHASE = new HashMap<>();
    private static final ThreadLocal<SprintHitContext> SPRINT_HIT_CONTEXT = new ThreadLocal<>();

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
            clearMovementState(playerId);
            return;
        }

        long now = player.level().getGameTime();
        double speed = sampleHorizontalDisplacement(playerId, player.position(), now);

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        if (!ComboStateRegistry.NONE.getId().equals(combo)
                || RengekiShortStepHandler.hasPendingKillTransfer(playerId)
                || !player.isSprinting()
                || !player.onGround()
                || player.isPassenger()
                || player.isUsingItem()
                || player.getAbilities().flying
                || speed < SPRINT_SLASH_MIN_SPEED) {
            resetSprintChain(playerId);
            return;
        }

        SprintChainState chain = SPRINT_CHAINS.computeIfAbsent(
                playerId,
                ignored -> new SprintChainState());

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

        if (chain.activeBeat >= 0) {
            emitSprintBVisualTick(
                    player,
                    blade,
                    chain.activeBeat,
                    chain.burstTick,
                    chain.visualSize);
            chain.burstTick++;
            if (chain.burstTick >= SPRINT_B_BURST_TICKS) {
                chain.activeBeat = -1;
                chain.burstTick = 0;
            }
        }

        if (chain.activeBeat < 0 && now >= chain.nextVisualBeatAt) {
            chain.activeBeat = chain.nextBeat;
            chain.nextBeat = (chain.nextBeat + 1) % SPRINT_B_CHAIN_LENGTH;
            chain.burstTick = 0;
            chain.visualSize = visualSize;
            chain.nextVisualBeatAt = now + SPRINT_VISUAL_INTERVAL_TICKS;

            emitSprintBVisualTick(
                    player,
                    blade,
                    chain.activeBeat,
                    chain.burstTick,
                    chain.visualSize);
            chain.burstTick++;
        }

        if (now < chain.nextHitAt) {
            return;
        }
        chain.nextHitAt = now + SPRINT_HIT_INTERVAL_TICKS;

        LivingEntity target = selectSprintSlashTarget(player, range);
        if (target != null) {
            applySprintSlashHit(player, target, damageRatio);
        }
    }

    /**
     * Server END-tick deltaMovement is already damped by ground friction, which
     * made ordinary sprinting fall below the old trigger threshold while a V
     * rush/teleport left enough residual motion to pass it. Measure real
     * horizontal displacement between consecutive server ticks instead.
     */
    private static double sampleHorizontalDisplacement(
            UUID playerId,
            Vec3 position,
            long gameTime) {
        MovementSample previous = MOVEMENT_SAMPLES.put(
                playerId,
                new MovementSample(position, gameTime));
        if (previous == null || gameTime - previous.gameTime() != 1L) {
            return 0.0D;
        }

        double dx = position.x - previous.position().x;
        double dz = position.z - previous.position().z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Visually follows the authored B-series language without installing the
     * actual native combo: B1 opens with a crossed pair, B2-B6 use the same
     * alternating random-roll rush cadence, and B7 adds a compact finisher.
     * All spawned slash entities remain visual-only, ownerless and silent.
     */
    private static void emitSprintBVisualTick(
            ServerPlayer player,
            ItemStack blade,
            int beat,
            int burstTick,
            float visualSize) {
        if (beat == 0 && burstTick == 0) {
            spawnBVisual(player, blade, visualSize, -30.0F, -0.08D, 0.00D, 0.00D);
            spawnBVisual(player, blade, visualSize, 145.0F, 0.08D, 0.02D, 0.00D);
            return;
        }

        int rushTick = beat == 0 ? burstTick - 1 : burstTick;
        if (rushTick >= 0) {
            boolean mirrored = (rushTick & 1) != 0;
            float roll = (mirrored ? 90.0F : -90.0F)
                    + 180.0F * player.getRandom().nextFloat();
            double sideOffset = (player.getRandom().nextDouble() - 0.5D) * 0.85D;
            double heightOffset = (player.getRandom().nextDouble() - 0.5D) * 0.45D;
            double forwardOffset = 0.10D + player.getRandom().nextDouble() * 0.20D;
            spawnBVisual(
                    player,
                    blade,
                    visualSize,
                    roll,
                    sideOffset,
                    heightOffset,
                    forwardOffset);
        }

        if (beat == SPRINT_B_CHAIN_LENGTH - 1
                && burstTick == SPRINT_B_BURST_TICKS - 1) {
            double side = (player.getRandom().nextDouble() - 0.5D) * 0.20D;
            spawnBVisual(player, blade, visualSize, 0.0F, side, 0.34D, 0.05D);
            spawnBVisual(player, blade, visualSize, 5.0F, -side, 0.38D, 0.08D);
        }
    }

    /**
     * Visual-only slash effect. No shooter/owner means EntitySlashEffect cannot
     * run its built-in broad areaAttack; real damage is resolved by the separate
     * sprint hit cadence. Every visual is muted.
     */
    private static void spawnBVisual(
            ServerPlayer player,
            ItemStack blade,
            float visualSize,
            float roll,
            double sideOffset,
            double heightOffset,
            double forwardOffset) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = horizontalLook(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 position = player.position()
                .add(0.0D, player.getBbHeight() * 0.55D + heightOffset, 0.0D)
                .add(forward.scale(VISUAL_FORWARD_OFFSET + forwardOffset))
                .add(right.scale(sideOffset));

        EntitySlashEffect effect = new EntitySlashEffect(
                SlashBlade.RegistryEvents.SlashEffect,
                level);
        effect.setPos(position.x, position.y, position.z);
        effect.setYRot(player.getYRot());
        effect.setXRot(0.0F);
        effect.setRotationRoll(roll);
        effect.setColor(blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getColorCode())
                .orElse(0xFFFFFF));
        effect.setMute(true);
        effect.setIsCritical(false);
        effect.setBaseSize(visualSize);
        effect.setLifetime(3);
        level.addFreshEntity(effect);
    }

    /** Select one legal frontal target; the visual flurry itself never damages. */
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
     * Reuse Resharped's melee attack path so damage scales from the normal
     * weapon/player panel path. Only the speed-provided ratio is capped.
     *
     * <p>Temporarily suppress vanilla sprint-hit knockback, then restore both
     * the exact pre-hit motion vector and sprint flag in finally so the passive
     * cannot cancel or visibly slow the sprint that powered it. A synchronous
     * sprint-hit context also lets the sound and durability hooks below identify
     * this one passive hit without touching ordinary left/right attacks.</p>
     *
     * <p>forceHit stays false so a sprint pulse does not bulldoze an unrelated
     * pre-existing hurt window. resetHit is true so, after this pulse resolves,
     * Rengeki's own four-tick cadence can continue instead of being throttled by
     * the ten-tick vanilla hurt window.</p>
     */
    private static void applySprintSlashHit(
            ServerPlayer player,
            LivingEntity target,
            float damageRatio) {
        Vec3 momentum = player.getDeltaMovement();
        boolean sprinting = player.isSprinting();
        SprintHitContext previousContext = SPRINT_HIT_CONTEXT.get();
        SPRINT_HIT_CONTEXT.set(new SprintHitContext(player.getUUID(), player.getMainHandItem()));
        player.setSprinting(false);
        try {
            AttackManager.doMeleeAttack(
                    player,
                    target,
                    false,
                    true,
                    damageRatio);
        } finally {
            player.setDeltaMovement(momentum);
            player.setSprinting(sprinting);
            if (previousContext == null) {
                SPRINT_HIT_CONTEXT.remove();
            } else {
                SPRINT_HIT_CONTEXT.set(previousContext);
            }
        }
    }

    /**
     * Resharped posts HitEvent after damage succeeds but before ItemSlashBlade
     * spends durability. The denser sprint cadence uses one normal durability
     * opportunity per ten successful hits, keeping wear near the old per-second
     * budget instead of multiplying it with the new hit frequency.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onSprintHitDurability(SlashBladeEvent.HitEvent event) {
        SprintHitContext context = SPRINT_HIT_CONTEXT.get();
        if (context == null
                || event.isCanceled()
                || !(event.getUser() instanceof ServerPlayer player)
                || !player.getUUID().equals(context.playerId())
                || event.getBlade() != context.blade()) {
            return;
        }

        int phase = (SPRINT_DURABILITY_PHASE.getOrDefault(context.playerId(), 0) + 1)
                % SPRINT_DURABILITY_DIVISOR;
        SPRINT_DURABILITY_PHASE.put(context.playerId(), phase);
        if (phase != 0) {
            event.setCanceled(true);
        }
    }

    /**
     * The visual slash entities are already muted. Resharped's compatibility
     * melee path still emits vanilla player attack sounds, so suppress only
     * those synchronous attack sounds while a sprint hit context is active.
     * Target hurt/death sounds and ordinary Rengeki attacks remain untouched.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSprintHitSound(PlayLevelSoundEvent.AtPosition event) {
        if (SPRINT_HIT_CONTEXT.get() == null || event.getSound() == null) {
            return;
        }

        if (event.getSound() == SoundEvents.PLAYER_ATTACK_CRIT
                || event.getSound() == SoundEvents.PLAYER_ATTACK_NODAMAGE
                || event.getSound() == SoundEvents.PLAYER_ATTACK_KNOCKBACK
                || event.getSound() == SoundEvents.PLAYER_ATTACK_STRONG
                || event.getSound() == SoundEvents.PLAYER_ATTACK_WEAK
                || event.getSound() == SoundEvents.PLAYER_ATTACK_SWEEP) {
            event.setCanceled(true);
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

    private static void resetSprintChain(UUID playerId) {
        SPRINT_CHAINS.remove(playerId);
    }

    private static void clearMovementState(UUID playerId) {
        MOVEMENT_SAMPLES.remove(playerId);
        SPRINT_CHAINS.remove(playerId);
    }

    private static void clearAllState(UUID playerId) {
        clearMovementState(playerId);
        SPRINT_DURABILITY_PHASE.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearAllState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearAllState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        clearAllState(event.getOriginal().getUUID());
        clearAllState(event.getEntity().getUUID());
    }

    private record MovementSample(Vec3 position, long gameTime) {
    }

    private record SprintHitContext(UUID playerId, ItemStack blade) {
    }

    private static final class SprintChainState {
        private long nextVisualBeatAt = Long.MIN_VALUE;
        private long nextHitAt = Long.MIN_VALUE;
        private int nextBeat;
        private int activeBeat = -1;
        private int burstTick;
        private float visualSize;
    }

    private RengekiMomentumHandler() {
    }
}