package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dangaku's spatial-control layer.
 *
 * <p>Plain grounded right-click is captured before ItemSlashBlade.use can run
 * its native progressCombo path. Every armed release resolves Dangaku's own
 * formation sweep; when the blade also qualifies for a native Slash Art, the
 * Stop event remains uncanceled so Resharped releases that SA on top. No charge
 * state is serialized to the blade or player.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DangakuFormationHandler {
    static final double ORDINARY_SWEEP_DAMAGE_FACTOR = 0.58D;
    static final double CLEAVE_DAMAGE_FACTOR = 0.80D;
    static final double ORDINARY_FOCUS_DISTANCE = 2.70D;
    static final double ORDINARY_PULL_STRENGTH = 0.52D;
    static final double CLUSTER_RADIUS = 1.80D;
    static final int CLUSTER_REQUIRED_TARGETS = 3;
    static final float CLUSTER_DAMAGE_MULTIPLIER = 1.06F;
    static final float SLASH_ART_SWEEP_DAMAGE_FACTOR = 0.55F;
    static final int CHARGED_STRIKE_DELAY_TICKS = 4;
    static final int MAX_CHARGED_TARGETS = 24;
    static final double CHARGED_MAX_HEIGHT_DIFFERENCE = 3.0D;
    static final double CHARGED_SWEEP_MIN_DOT = Math.cos(Math.toRadians(135.0D));

    private static final String LEGACY_BROKEN_STANCE_OWNER = "blade_tetra_broken_stance_owner";
    private static final String LEGACY_BROKEN_STANCE_READY = "blade_tetra_broken_stance_ready";
    private static final String LEGACY_BROKEN_STANCE_UNTIL = "blade_tetra_broken_stance_until";

    private static final Map<UUID, ChargeState> ACTIVE_CHARGES = new HashMap<>();
    private static final Map<UUID, PendingStrike> PENDING_STRIKES = new HashMap<>();
    private static final ThreadLocal<ChargedSweepHitContext> CHARGED_SWEEP_HIT_CONTEXT =
            new ThreadLocal<>();

    private static final TargetingConditions NATIVE_TARGET_FILTER =
            new TargetSelector.SlashBladeTargetingConditions()
                    .range(64.0D)
                    .ignoreInvisibilityTesting()
                    .selector(new TargetSelector.AttackablePredicate());

    /** Native Dangaku slashes keep their authored timelines, but their roles diverge. */
    @SubscribeEvent
    public static void onDangakuSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.DANGAKU) {
            return;
        }

        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        if (ModComboStates.isDangakuSweep(combo)) {
            event.setDamage(event.getDamage() * ORDINARY_SWEEP_DAMAGE_FACTOR);
        } else if (ModComboStates.isDangakuCleave(combo)) {
            event.setDamage(event.getDamage() * CLEAVE_DAMAGE_FACTOR);
        }
    }

    /**
     * Capture plain grounded Dangaku right-click before ItemSlashBlade.use can
     * add R_CLICK and call progressCombo. Sneak-directional inputs stay native.
     *
     * <p>If the previous Dangaku attack has not actually returned to neutral,
     * the use is still captured so it cannot leak into the native combo tree,
     * but item use does not start yet. Minecraft retries held right-click once
     * recovery ends, so the actual charge timer cannot include recovery time.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        ItemStack blade = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND
                || !isDangakuBlade(blade)
                || !player.onGround()
                || player.isShiftKeyDown()) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!isNeutralForCharge(player, blade)) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            ACTIVE_CHARGES.put(
                    serverPlayer.getUUID(),
                    new ChargeState(
                            blade,
                            serverPlayer.level().getGameTime(),
                            true));
        }

        player.startUsingItem(hand);
    }

    /**
     * Resolve Dangaku's sweep first, then optionally let native Slash Art release
     * continue through ItemSlashBlade.releaseUsing. Dangaku never reimplements SA.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChargeStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ChargeState state = ACTIVE_CHARGES.remove(player.getUUID());
        if (state == null) {
            return;
        }

        ItemStack blade = player.getMainHandItem();
        if (blade != state.blade()
                || !isDangakuBlade(blade)
                || !player.isAlive()
                || !state.armed()) {
            event.setCanceled(true);
            return;
        }

        long heldTicks = Math.max(0L, player.level().getGameTime() - state.startedAt());
        int chargedSweepStartTicks = Math.min(
                nativeSlashArtStartTicks(player, blade),
                DangakuChargeMath.FULL_CHARGE_TICKS);
        boolean slashArtRelease = canReleaseSlashArt(player, blade, heldTicks);

        if (heldTicks < chargedSweepStartTicks) {
            event.setCanceled(true);
            beginCombo(player, blade, ModComboStates.getDangakuSweepId());
            return;
        }

        double charge = DangakuChargeMath.chargeForHeldTicks(heldTicks);
        double panelDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!slashArtRelease) {
            event.setCanceled(true);
            beginCombo(player, blade, ModComboStates.getDangakuChargedSweepId());
        }

        PENDING_STRIKES.put(
                player.getUUID(),
                new PendingStrike(
                        blade,
                        player.level().getGameTime() + CHARGED_STRIKE_DELAY_TICKS,
                        charge,
                        panelDamage,
                        slashArtRelease));
        // If SA is valid, leave Stop uncanceled. Resharped remains authoritative
        // for SA timing, cost, ChargeActionEvent and third-party modifications.
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        ChargeState charging = ACTIVE_CHARGES.get(playerId);
        if (charging != null && charging.armed() && !player.onGround()) {
            charging = new ChargeState(charging.blade(), charging.startedAt(), false);
            ACTIVE_CHARGES.put(playerId, charging);
        }
        if (charging != null
                && (!player.isUsingItem()
                        || player.getMainHandItem() != charging.blade()
                        || !isDangakuBlade(charging.blade()))) {
            ACTIVE_CHARGES.remove(playerId);
        }

        PendingStrike pending = PENDING_STRIKES.get(playerId);
        if (pending == null || player.level().getGameTime() < pending.executeAt()) {
            return;
        }
        PENDING_STRIKES.remove(playerId);

        ItemStack blade = player.getMainHandItem();
        if (blade != pending.blade()
                || !isDangakuBlade(blade)
                || !player.isAlive()) {
            return;
        }

        executeChargedSweep(
                player,
                blade,
                pending.charge(),
                pending.panelDamage(),
                pending.withSlashArt());
    }

    /**
     * Position replaces the old +5% broken-stance tag as Dangaku's payoff.
     * A cleave through a naturally or deliberately compressed group gets a small
     * positional bonus; no mark/timer is needed.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDangakuHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        ItemStack blade = attacker.getMainHandItem();
        if (!isDangakuBlade(blade)) {
            return;
        }

        clearLegacyBrokenStance(event.getEntity().getPersistentData());
        if (!ModComboStates.isDangakuCleave(currentCombo(blade))) {
            return;
        }

        int clustered = countClusteredTargets(attacker, event.getEntity());
        if (clustered >= CLUSTER_REQUIRED_TARGETS) {
            event.setAmount(event.getAmount() * CLUSTER_DAMAGE_MULTIPLIER);
        }
    }

    /**
     * Ordinary sweep hits move targets toward a point in front of the player.
     * The legacy broken-stance tag is cleared after the older handler has had its
     * callback so the new flow does not carry persistent per-target state.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDangakuHit(SlashBladeEvent.HitEvent event) {
        if (event.getUser().level().isClientSide()
                || !isDangakuBlade(event.getBlade())) {
            return;
        }

        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        if (ModComboStates.isDangakuCleave(combo)) {
            clearLegacyBrokenStance(event.getTarget().getPersistentData());
            return;
        }
        if (event.isCanceled() || !ModComboStates.isDangakuSweep(combo)) {
            return;
        }

        Vec3 focus = event.getUser().position()
                .add(horizontalLook(event.getUser()).scale(ORDINARY_FOCUS_DISTANCE));
        pullTowardFocus(event.getTarget(), focus, ORDINARY_PULL_STRENGTH);
    }

    /**
     * Charged formation damage is a sidecar to the current combo. Cancel only
     * the exact post-damage HitEvent generated by our synchronous melee call so
     * ItemSlashBlade does not apply the current SA hitEffect or durability wear.
     * The damage itself has already been resolved by Resharped at this point.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onChargedSweepHitResolved(SlashBladeEvent.HitEvent event) {
        ChargedSweepHitContext context = CHARGED_SWEEP_HIT_CONTEXT.get();
        if (context == null
                || !(event.getUser() instanceof ServerPlayer player)
                || !player.getUUID().equals(context.playerId)
                || event.getBlade() != context.blade
                || event.getTarget() != context.target) {
            return;
        }

        context.hitSucceeded = true;
        if (!event.isCanceled()) {
            event.setCanceled(true);
        }
    }

    private static void executeChargedSweep(
            ServerPlayer player,
            ItemStack blade,
            double charge,
            double panelDamage,
            boolean withSlashArt) {
        ServerLevel level = player.serverLevel();
        double range = DangakuChargeMath.sweepRange(panelDamage, charge);
        float damageRatio = DangakuChargeMath.sweepDamageRatio(panelDamage, charge);
        if (withSlashArt) {
            damageRatio *= SLASH_ART_SWEEP_DAMAGE_FACTOR;
        }
        Vec3 look = horizontalLook(player);
        Vec3 focus = player.position()
                .add(look.scale(DangakuChargeMath.focusDistance(range)));
        AABB searchBox = player.getBoundingBox().inflate(
                range,
                CHARGED_MAX_HEIGHT_DIFFERENCE + 1.0D,
                range);

        List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class,
                        searchBox,
                        candidate -> isBasicTarget(player, candidate))
                .stream()
                .filter(candidate -> isChargedSweepTarget(player, candidate, look, range))
                .sorted(Comparator.comparingDouble(candidate ->
                        distanceToBoxSqr(player.position(), candidate.getBoundingBox())))
                .limit(MAX_CHARGED_TARGETS)
                .toList();

        spawnChargedSweepVisual(player, blade, range, charge);
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                1.05F,
                0.72F + (float) charge * 0.18F);

        double pullStrength = 0.42D + 0.48D * charge;
        for (LivingEntity target : targets) {
            ChargedSweepHitContext previousContext = CHARGED_SWEEP_HIT_CONTEXT.get();
            ChargedSweepHitContext context = new ChargedSweepHitContext(
                    player.getUUID(),
                    blade,
                    target);
            CHARGED_SWEEP_HIT_CONTEXT.set(context);
            try {
                AttackManager.doMeleeAttack(player, target, false, false, damageRatio);
            } finally {
                if (previousContext == null) {
                    CHARGED_SWEEP_HIT_CONTEXT.remove();
                } else {
                    CHARGED_SWEEP_HIT_CONTEXT.set(previousContext);
                }
            }
            pullTowardFocus(target, focus, pullStrength);
        }
    }

    private static boolean isChargedSweepTarget(
            ServerPlayer player,
            LivingEntity target,
            Vec3 look,
            double range) {
        if (Math.abs(target.getY() - player.getY()) > CHARGED_MAX_HEIGHT_DIFFERENCE
                || !player.hasLineOfSight(target)
                || !NATIVE_TARGET_FILTER.test(player, target)
                || distanceToBoxSqr(player.position(), target.getBoundingBox()) > range * range) {
            return false;
        }

        Vec3 toward = new Vec3(
                target.getX() - player.getX(),
                0.0D,
                target.getZ() - player.getZ());
        return toward.lengthSqr() <= 1.0E-6D
                || look.dot(toward.normalize()) >= CHARGED_SWEEP_MIN_DOT;
    }

    private static int countClusteredTargets(
            LivingEntity attacker,
            LivingEntity center) {
        AABB box = center.getBoundingBox().inflate(CLUSTER_RADIUS);
        int count = 0;
        for (LivingEntity candidate : center.level().getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> isBasicTarget(attacker, entity))) {
            if (NATIVE_TARGET_FILTER.test(attacker, candidate)) {
                count++;
                if (count >= CLUSTER_REQUIRED_TARGETS) {
                    return count;
                }
            }
        }
        return count;
    }

    private static void beginCombo(
            ServerPlayer player,
            ItemStack blade,
            ResourceLocation combo) {
        blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .ifPresent(state -> state.updateComboSeq(player, combo));
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static void spawnChargedSweepVisual(
            ServerPlayer player,
            ItemStack blade,
            double range,
            double charge) {
        ServerLevel level = player.serverLevel();
        Vec3 look = horizontalLook(player);
        Vec3 position = player.position()
                .add(0.0D, player.getBbHeight() * 0.56D, 0.0D)
                .add(look.scale(1.05D));

        EntitySlashEffect effect = new EntitySlashEffect(
                SlashBlade.RegistryEvents.SlashEffect,
                level);
        effect.setPos(position.x, position.y, position.z);
        effect.setYRot(player.getYRot());
        effect.setXRot(0.0F);
        // Match COMBO_A1 / ordinary Dangaku sweep instead of the vertical 90° plane.
        effect.setRotationRoll(-10.0F);
        effect.setColor(MaterialSlashEffectResolver.resolve(blade).color());
        effect.setIsCritical(charge >= 0.98D);
        effect.setBaseSize(DangakuChargeMath.visualSize(range));
        effect.setLifetime(5);
        level.addFreshEntity(effect);
    }

    private static void pullTowardFocus(
            LivingEntity target,
            Vec3 focus,
            double strength) {
        Vec3 delta = new Vec3(
                focus.x - target.getX(),
                0.0D,
                focus.z - target.getZ());
        if (delta.lengthSqr() <= 1.0E-6D) {
            return;
        }

        double resistance = Math.max(0.0D, Math.min(
                1.0D,
                target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
        double effectiveStrength = strength * (1.0D - resistance);
        if (effectiveStrength <= 0.01D) {
            return;
        }

        double distance = Math.sqrt(delta.lengthSqr());
        double speed = Math.min(1.10D, effectiveStrength + distance * 0.055D);
        Vec3 direction = delta.scale(1.0D / distance);
        double y = Math.max(0.04D, target.getDeltaMovement().y * 0.35D);
        target.setDeltaMovement(
                direction.x * speed,
                y,
                direction.z * speed);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(target));
            target.hurtMarked = false;
        }
    }

    private static boolean isDangakuBlade(ItemStack blade) {
        return blade.getItem() instanceof ModularSlashBladeItem
                && StyleResolver.resolve(blade) == BladeStyle.DANGAKU;
    }

    private static boolean isNeutralForCharge(
            Player player,
            ItemStack blade) {
        return blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> ComboStateRegistry.NONE.getId().equals(
                        state.resolvCurrentComboState(player)))
                .orElse(false);
    }

    private static int nativeSlashArtStartTicks(
            LivingEntity user,
            ItemStack blade) {
        return blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getFullChargeTicks(user))
                .orElse(DangakuChargeMath.FULL_CHARGE_TICKS);
    }

    private static boolean canReleaseSlashArt(
            LivingEntity user,
            ItemStack blade,
            long heldTicks) {
        if (!SwordType.from(blade).contains(SwordType.ENCHANTED)) {
            return false;
        }
        return blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> !state.isBroken()
                        && !state.isSealed()
                        && heldTicks >= state.getFullChargeTicks(user))
                .orElse(false);
    }

    private static ResourceLocation currentCombo(ItemStack blade) {
        return blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
    }

    private static boolean isBasicTarget(
            LivingEntity attacker,
            LivingEntity candidate) {
        if (candidate == attacker
                || !candidate.isAlive()
                || candidate.isSpectator()
                || !candidate.isAttackable()
                || candidate.isAlliedTo(attacker)
                || attacker.isAlliedTo(candidate)) {
            return false;
        }
        if (attacker instanceof Player player && candidate instanceof Player other) {
            return player.canHarmPlayer(other);
        }
        return true;
    }

    private static Vec3 horizontalLook(LivingEntity entity) {
        Vec3 look = entity.getViewVector(1.0F);
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

    private static void clearLegacyBrokenStance(CompoundTag data) {
        data.remove(LEGACY_BROKEN_STANCE_OWNER);
        data.remove(LEGACY_BROKEN_STANCE_READY);
        data.remove(LEGACY_BROKEN_STANCE_UNTIL);
    }

    private static void clearState(UUID playerId) {
        ACTIVE_CHARGES.remove(playerId);
        PENDING_STRIKES.remove(playerId);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clearState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearState(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        clearState(event.getOriginal().getUUID());
        clearState(event.getEntity().getUUID());
    }

    private record ChargeState(
            ItemStack blade,
            long startedAt,
            boolean armed) {
    }

    private record PendingStrike(
            ItemStack blade,
            long executeAt,
            double charge,
            double panelDamage,
            boolean withSlashArt) {
    }

    private static final class ChargedSweepHitContext {
        private final UUID playerId;
        private final ItemStack blade;
        private final LivingEntity target;
        private boolean hitSucceeded;

        private ChargedSweepHitContext(UUID playerId, ItemStack blade, LivingEntity target) {
            this.playerId = playerId;
            this.blade = blade;
            this.target = target;
        }
    }

    private DangakuFormationHandler() {
    }
}
