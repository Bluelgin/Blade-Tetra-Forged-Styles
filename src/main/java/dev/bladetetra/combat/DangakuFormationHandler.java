package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
 * <p>Plain grounded right-click deliberately enters SlashBlade's native held-use
 * state without committing an attack. Releasing quickly performs the ordinary
 * gathering sweep; holding at least a short threshold releases a manually bounded
 * charged sweep whose range and ratio scale softly from the player's panel damage.
 * No charge state is serialized to the blade or player.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DangakuFormationHandler {
    static final double ORDINARY_SWEEP_DAMAGE_FACTOR = 0.82D;
    static final double CLEAVE_DAMAGE_FACTOR = 1.05D;
    static final double ORDINARY_FOCUS_DISTANCE = 2.70D;
    static final double ORDINARY_PULL_STRENGTH = 0.52D;
    static final double CLUSTER_RADIUS = 1.80D;
    static final int CLUSTER_REQUIRED_TARGETS = 3;
    static final float CLUSTER_DAMAGE_MULTIPLIER = 1.10F;
    static final int CHARGED_STRIKE_DELAY_TICKS = 4;
    static final int MAX_CHARGED_TARGETS = 24;
    static final double CHARGED_MAX_HEIGHT_DIFFERENCE = 3.0D;
    static final double CHARGED_SWEEP_MIN_DOT = Math.cos(Math.toRadians(135.0D));

    private static final String LEGACY_BROKEN_STANCE_OWNER = "blade_tetra_broken_stance_owner";
    private static final String LEGACY_BROKEN_STANCE_READY = "blade_tetra_broken_stance_ready";
    private static final String LEGACY_BROKEN_STANCE_UNTIL = "blade_tetra_broken_stance_until";

    private static final Map<UUID, ChargeState> ACTIVE_CHARGES = new HashMap<>();
    private static final Map<UUID, PendingStrike> PENDING_STRIKES = new HashMap<>();

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
     * ItemSlashBlade.use already starts the held-use state. A neutral Dangaku
     * right-click leaves the combo at NONE, which is the exact signal that this
     * use belongs to formation charging rather than a directional native action.
     */
    @SubscribeEvent
    public static void onChargeStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack blade = event.getItem();
        if (!isDangakuBlade(blade)
                || blade != player.getMainHandItem()
                || !player.onGround()
                || player.isShiftKeyDown()
                || !ComboStateRegistry.NONE.getId().equals(currentCombo(blade))) {
            return;
        }

        ACTIVE_CHARGES.put(
                player.getUUID(),
                new ChargeState(blade, player.level().getGameTime()));
    }

    /** Cancel Resharped's normal charge-release/Slash-Art path only for our owned use. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChargeStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ChargeState state = ACTIVE_CHARGES.remove(player.getUUID());
        if (state == null) {
            return;
        }

        event.setCanceled(true);
        ItemStack blade = player.getMainHandItem();
        if (blade != state.blade()
                || !isDangakuBlade(blade)
                || !player.isAlive()) {
            return;
        }

        long heldTicks = Math.max(0L, player.level().getGameTime() - state.startedAt());
        if (heldTicks < DangakuChargeMath.SHORT_PRESS_TICKS) {
            beginCombo(player, blade, ModComboStates.getDangakuSweepId());
            return;
        }

        double charge = DangakuChargeMath.chargeForHeldTicks(heldTicks);
        double panelDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        beginCombo(player, blade, ModComboStates.getDangakuChargedSweepId());
        PENDING_STRIKES.put(
                player.getUUID(),
                new PendingStrike(
                        blade,
                        player.level().getGameTime() + CHARGED_STRIKE_DELAY_TICKS,
                        charge,
                        panelDamage));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        ChargeState charging = ACTIVE_CHARGES.get(playerId);
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
                || !ModComboStates.isDangakuChargedSweep(currentCombo(blade))) {
            return;
        }

        executeChargedSweep(player, blade, pending.charge(), pending.panelDamage());
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

    private static void executeChargedSweep(
            ServerPlayer player,
            ItemStack blade,
            double charge,
            double panelDamage) {
        ServerLevel level = player.serverLevel();
        double range = DangakuChargeMath.sweepRange(panelDamage, charge);
        float damageRatio = DangakuChargeMath.sweepDamageRatio(panelDamage, charge);
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
            AttackManager.doMeleeAttack(player, target, false, false, damageRatio);
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
        effect.setRotationRoll(90.0F);
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
    }

    private static boolean isDangakuBlade(ItemStack blade) {
        return blade.getItem() instanceof ModularSlashBladeItem
                && StyleResolver.resolve(blade) == BladeStyle.DANGAKU;
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

    private record ChargeState(ItemStack blade, long startedAt) {
    }

    private record PendingStrike(
            ItemStack blade,
            long executeAt,
            double charge,
            double panelDamage) {
    }

    private DangakuFormationHandler() {
    }
}
