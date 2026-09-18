package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.IaidoImpactPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Applies style-only combat rules. Every handler exits immediately for regular
 * Resharped blades, so the addon never changes their balance or combo behavior.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StyleCombatHandler {
    private static final double IAIDO_RANGE = 4.25D;
    private static final double IAIDO_MIN_DOT = Math.cos(Math.toRadians(35.0D));
    private static final double DANGAKU_CLEAVE_RANGE = 6.25D;
    private static final double DANGAKU_CLEAVE_MIN_DOT =
            Math.cos(Math.toRadians(45.0D));
    private static final double DANGAKU_SWEEP_RANGE = 5.25D;
    private static final double DANGAKU_SWEEP_MIN_DOT =
            Math.cos(Math.toRadians(110.0D));
    private static final int DANGAKU_ARMOR_END_TICK = 11;
    private static final float IAIDO_DEFLECT_DAMAGE_MULTIPLIER = 0.35F;

    private static final String IAIDO_TARGET = "blade_tetra_iaido_target";
    private static final String IAIDO_TARGET_UNTIL = "blade_tetra_iaido_target_until";
    private static final String IAIDO_READY_SINCE = "blade_tetra_iaido_ready_since";
    private static final String IAIDO_PERFECT_UNTIL = "blade_tetra_iaido_perfect_until";
    private static final String IAIDO_FEEDBACK_TIER = "blade_tetra_iaido_feedback_tier";
    private static final String IAIDO_FEEDBACK_UNTIL = "blade_tetra_iaido_feedback_until";
    private static final String IAIDO_CHAIN_ACTIVE = "blade_tetra_iaido_chain_active";
    private static final String IAIDO_DRAW_MULTIPLIER = "blade_tetra_iaido_draw_multiplier";
    private static final String IAIDO_DRAW_POWER_UNTIL = "blade_tetra_iaido_draw_power_until";
    private static final String IAIDO_DRAW_SLASH_COUNT = "blade_tetra_iaido_draw_slash_count";
    private static final String IAIDO_SPACING = "blade_tetra_iaido_spacing";
    private static final String IAIDO_SPACING_UNTIL = "blade_tetra_iaido_spacing_until";
    private static final String IAIDO_DISRUPTED_UNTIL = "blade_tetra_iaido_disrupted_until";
    private static final String IAIDO_DEFLECT_UNTIL = "blade_tetra_iaido_deflect_until";
    private static final String BROKEN_STANCE_OWNER = "blade_tetra_broken_stance_owner";
    private static final String BROKEN_STANCE_READY = "blade_tetra_broken_stance_ready";
    private static final String BROKEN_STANCE_UNTIL = "blade_tetra_broken_stance_until";

    @SubscribeEvent
    public static void onSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        BladeStyle style = StyleResolver.resolve(event.getBlade());
        ResourceLocation combo = event.getSlashBladeState().getComboSeq();

        switch (style) {
            case IAIDO -> {
                if (isIaidoSlash(combo)) {
                    IaidoDrawPower draw = resolveIaidoDrawPower(event.getUser());
                    int feedbackTier = draw.tier();
                    double share = consumeIaidoDrawShare(event.getUser(), draw.fresh());
                    event.setDamage(event.getDamage() * draw.multiplier() * share);
                    event.setCritical(true);
                    markIaidoFeedback(event.getUser(), feedbackTier);
                    playIaidoDrawFeedback(event.getUser(), feedbackTier);
                    if (draw.fresh() && feedbackTier >= 2) {
                        markPerfectIaido(event.getUser());
                    }
                } else if (ModComboStates.isIaidoFinish(combo)) {
                    event.setDamage(event.getDamage() * 1.45D);
                    event.setCritical(true);
                } else if (ModComboStates.isIaidoAttack(combo)) {
                    event.setDamage(event.getDamage() * 1.22D);
                }
            }
            case RENGEKI -> {
                // The wakizashi already trades per-hit damage for speed and
                // multi-hit pressure; applying another style penalty made its
                // damage-per-durability disproportionately poor.
            }
            case DANGAKU -> {
                if (isDangakuSlash(combo)) {
                    event.setDamage(event.getDamage() * 1.12D);
                    event.setKnockback(KnockBacks.smash);
                } else if (isDangakuSweep(combo)) {
                    event.setDamage(event.getDamage() * 0.92D);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Resharped collects every target inside one shared reach box. Narrowing at
     * LivingAttackEvent keeps the normal animation and compatibility hooks while
     * making Iaido a true single-target cone and Dangaku a broad frontal sweep.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }

        ItemStack blade = attacker.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        BladeStyle style = StyleResolver.resolve(blade);

        if (style == BladeStyle.IAIDO && ModComboStates.isIaidoAttack(combo)) {
            if (!isInsideFrontArc(
                    attacker, event.getEntity(), IAIDO_RANGE, IAIDO_MIN_DOT)
                    || !lockIaidoTarget(attacker, event.getEntity())) {
                event.setCanceled(true);
            } else if (isIaidoSlash(combo)) {
                markIaidoSpacing(attacker, event.getEntity());
            }
        } else if (style == BladeStyle.DANGAKU) {
            if (isDangakuSlash(combo)
                    && !isInsideFrontArc(
                            attacker,
                            event.getEntity(),
                            DANGAKU_CLEAVE_RANGE,
                            DANGAKU_CLEAVE_MIN_DOT)) {
                event.setCanceled(true);
            } else if (isDangakuSweep(combo)
                    && !isInsideFrontArc(
                            attacker,
                            event.getEntity(),
                            DANGAKU_SWEEP_RANGE,
                            DANGAKU_SWEEP_MIN_DOT)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (event.getUser().level().isClientSide()
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        BladeStyle style = StyleResolver.resolve(event.getBlade());
        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        if (style == BladeStyle.IAIDO && isIaidoSlash(combo)) {
            IaidoTrickHandler.markIaidoHit(event.getUser(), event.getTarget());
            int feedbackTier = consumeIaidoFeedback(event.getUser());
            playIaidoImpactFeedback(
                    event.getUser(), event.getTarget(), event.getBlade(), feedbackTier);
            return;
        }
        if (style != BladeStyle.DANGAKU || !isDangakuSlash(combo)) {
            return;
        }

        long now = event.getUser().level().getGameTime();
        CompoundTag targetData = event.getTarget().getPersistentData();
        targetData.putUUID(BROKEN_STANCE_OWNER, event.getUser().getUUID());
        targetData.putLong(BROKEN_STANCE_READY, now + 3L);
        targetData.putLong(BROKEN_STANCE_UNTIL, now + 60L);

        if (event.getTarget().level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    event.getTarget().getX(),
                    event.getTarget().getY() + event.getTarget().getBbHeight() * 0.6D,
                    event.getTarget().getZ(),
                    6,
                    event.getTarget().getBbWidth() * 0.35D,
                    event.getTarget().getBbHeight() * 0.25D,
                    event.getTarget().getBbWidth() * 0.35D,
                    0.08D);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        applyBrokenStance(event);
        applyIaidoSpacing(event);
        applyPerfectIaidoBonus(event);

        LivingEntity defender = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != defender && isHoldingIaido(defender)) {
            if (!event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                    && isIaidoDeflectWindow(defender)) {
                event.setAmount(event.getAmount() * IAIDO_DEFLECT_DAMAGE_MULTIPLIER);
                markIaidoDeflect(defender);
            } else {
                disruptIaido(defender);
            }
        }
        if (attacker != null
                && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                && isDangakuArmorWindow(defender)) {
            event.setAmount(event.getAmount() * 0.65F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity defender = event.getEntity();
        if (isDangakuArmorWindow(defender) || hasJustDeflected(defender)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSlashEffectJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof EntitySlashEffect slashEffect)
                || !(slashEffect.getShooter() instanceof LivingEntity user)) {
            return;
        }

        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        BladeStyle style = StyleResolver.resolve(blade);
        MaterialSlashEffectResolver.SlashVisual visual =
                MaterialSlashEffectResolver.resolve(blade);
        int color = visual.color();

        if (style == BladeStyle.IAIDO) {
            boolean perfect = isIaidoSlash(combo) && isPerfectIaidoVisual(user);
            slashEffect.setBaseSize(isIaidoSlash(combo)
                    ? (perfect ? 0.96F : 0.76F) : 0.86F);
            if (isIaidoSlash(combo)) {
                color = MaterialSlashEffectResolver.blendTowardWhite(
                        color, perfect ? 0.62F : 0.24F);
            }
        } else if (style == BladeStyle.RENGEKI) {
            slashEffect.setBaseSize(0.82F);
        } else if (style == BladeStyle.DANGAKU) {
            slashEffect.setBaseSize(isDangakuSlash(combo) ? 1.55F : 1.18F);
            if (isDangakuSlash(combo)) {
                color = MaterialSlashEffectResolver.blendTowardBlack(color, 0.10F);
            }
        }
        slashEffect.setColor(color);
        spawnMaterialAccent(
                (ServerLevel) event.getLevel(),
                slashEffect,
                visual,
                style,
                combo);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        boolean modularBlade = entity.getMainHandItem().getItem() instanceof ModularSlashBladeItem;
        boolean hasIaidoState = hasIaidoTickState(data);
        boolean hasBrokenStanceState = hasBrokenStanceTickState(data);
        if (!modularBlade && !hasIaidoState && !hasBrokenStanceState) {
            return;
        }

        long now = entity.level().getGameTime();
        if (data.contains(IAIDO_TARGET_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_TARGET_UNTIL) < now) {
            data.remove(IAIDO_TARGET);
            data.remove(IAIDO_TARGET_UNTIL);
        }
        if (data.contains(IAIDO_PERFECT_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_PERFECT_UNTIL) < now) {
            data.remove(IAIDO_PERFECT_UNTIL);
        }
        if (data.contains(IAIDO_FEEDBACK_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_FEEDBACK_UNTIL) < now) {
            clearIaidoFeedback(data);
        }
        if (data.contains(IAIDO_DRAW_POWER_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_DRAW_POWER_UNTIL) < now) {
            data.remove(IAIDO_DRAW_MULTIPLIER);
            data.remove(IAIDO_DRAW_POWER_UNTIL);
            data.remove(IAIDO_DRAW_SLASH_COUNT);
        }
        if (data.contains(IAIDO_SPACING_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_SPACING_UNTIL) < now) {
            data.remove(IAIDO_SPACING);
            data.remove(IAIDO_SPACING_UNTIL);
        }
        if (data.contains(IAIDO_DEFLECT_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_DEFLECT_UNTIL) < now) {
            data.remove(IAIDO_DEFLECT_UNTIL);
        }
        if (data.contains(IAIDO_DISRUPTED_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_DISRUPTED_UNTIL) < now) {
            data.remove(IAIDO_DISRUPTED_UNTIL);
        }
        if (data.contains(BROKEN_STANCE_UNTIL, Tag.TAG_LONG)
                && data.getLong(BROKEN_STANCE_UNTIL) < now) {
            clearBrokenStance(data);
        }
        if (modularBlade || hasIaidoState) {
            updateIaidoReadiness(entity, data, now);
        }
    }

    private static boolean hasIaidoTickState(CompoundTag data) {
        return data.contains(IAIDO_TARGET_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_READY_SINCE, Tag.TAG_LONG)
                || data.contains(IAIDO_PERFECT_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_FEEDBACK_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_DRAW_POWER_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_SPACING_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_DEFLECT_UNTIL, Tag.TAG_LONG)
                || data.contains(IAIDO_DISRUPTED_UNTIL, Tag.TAG_LONG)
                || data.getBoolean(IAIDO_CHAIN_ACTIVE);
    }

    private static boolean hasBrokenStanceTickState(CompoundTag data) {
        return data.contains(BROKEN_STANCE_UNTIL, Tag.TAG_LONG)
                || data.contains(BROKEN_STANCE_READY, Tag.TAG_LONG)
                || data.hasUUID(BROKEN_STANCE_OWNER);
    }

    private static void applyBrokenStance(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || !(attacker.getMainHandItem().getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        CompoundTag targetData = event.getEntity().getPersistentData();
        long now = event.getEntity().level().getGameTime();
        long until = targetData.getLong(BROKEN_STANCE_UNTIL);
        if (until < now) {
            clearBrokenStance(targetData);
            return;
        }

        if (now >= targetData.getLong(BROKEN_STANCE_READY)
                && targetData.hasUUID(BROKEN_STANCE_OWNER)
                && attacker.getUUID().equals(targetData.getUUID(BROKEN_STANCE_OWNER))) {
            event.setAmount(event.getAmount() * 1.05F);
            clearBrokenStance(targetData);
        }
    }

    private static void applyPerfectIaidoBonus(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()) {
            return;
        }

        ItemStack blade = attacker.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO
                || !isPerfectIaidoVisual(attacker)) {
            return;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        if (!isIaidoSlash(combo)
                || !isLockedIaidoTarget(attacker, event.getEntity())
                || currentIaidoSpacing(attacker) != IaidoBalance.Spacing.OPTIMAL) {
            attacker.getPersistentData().remove(IAIDO_PERFECT_UNTIL);
            return;
        }

        event.setAmount(event.getAmount()
                + calculatePerfectIaidoBonus(event.getEntity().getMaxHealth()));
        attacker.getPersistentData().remove(IAIDO_PERFECT_UNTIL);
    }

    static float calculatePerfectIaidoBonus(float targetMaxHealth) {
        return IaidoBalance.perfectBonus(targetMaxHealth);
    }

    private static void applyIaidoSpacing(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || !isHoldingIaido(attacker)) {
            return;
        }
        CompoundTag data = attacker.getPersistentData();
        long now = attacker.level().getGameTime();
        if (data.getLong(IAIDO_DRAW_POWER_UNTIL) < now
                || !data.contains(IAIDO_DRAW_MULTIPLIER, Tag.TAG_DOUBLE)) {
            return;
        }
        if (!lockIaidoTarget(attacker, event.getEntity())) {
            // One draw gets one target. Native slash effects are area attacks,
            // but allowing every nearby entity to spend the full budget makes
            // both damage and feedback multiply unexpectedly.
            event.setAmount(0.0F);
            return;
        }
        markIaidoSpacing(attacker, event.getEntity());
        double drawMultiplier = data.getDouble(IAIDO_DRAW_MULTIPLIER);
        double effective = IaidoBalance.effectiveMultiplier(
                drawMultiplier, currentIaidoSpacing(attacker));
        if (drawMultiplier > 0.0D && effective < drawMultiplier) {
            event.setAmount((float) (event.getAmount() * effective / drawMultiplier));
        }
    }

    private static boolean isLockedIaidoTarget(
            LivingEntity attacker,
            LivingEntity target) {
        CompoundTag data = attacker.getPersistentData();
        return data.getLong(IAIDO_TARGET_UNTIL) >= attacker.level().getGameTime()
                && data.hasUUID(IAIDO_TARGET)
                && target.getUUID().equals(data.getUUID(IAIDO_TARGET));
    }

    private static void clearBrokenStance(CompoundTag targetData) {
        targetData.remove(BROKEN_STANCE_OWNER);
        targetData.remove(BROKEN_STANCE_READY);
        targetData.remove(BROKEN_STANCE_UNTIL);
    }

    private static boolean isDangakuArmorWindow(LivingEntity entity) {
        ItemStack blade = entity.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.DANGAKU) {
            return false;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return isDangakuSlash(combo)
                && ComboState.getElapsed(entity) <= DANGAKU_ARMOR_END_TICK;
    }

    private static boolean isHoldingIaido(LivingEntity entity) {
        ItemStack blade = entity.getMainHandItem();
        return blade.getItem() instanceof ModularSlashBladeItem
                && StyleResolver.resolve(blade) == BladeStyle.IAIDO;
    }

    private static boolean isIaidoDeflectWindow(LivingEntity entity) {
        if (!isHoldingIaido(entity)) return false;
        ItemStack blade = entity.getMainHandItem();
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return ModComboStates.isIaidoDraw(combo)
                && ComboState.getElapsed(entity) <= IaidoBalance.DEFLECT_END_TICK;
    }

    private static void updateIaidoReadiness(
            LivingEntity entity,
            CompoundTag data,
            long now) {
        ItemStack blade = entity.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            data.remove(IAIDO_READY_SINCE);
            return;
        }
        if (data.getLong(IAIDO_DISRUPTED_UNTIL) >= now) {
            data.remove(IAIDO_READY_SINCE);
            return;
        }
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        boolean settled = ComboStateRegistry.NONE.getId().equals(combo)
                || ModComboStates.isIaidoSheathe(combo);
        if (settled) {
            if (!data.contains(IAIDO_READY_SINCE, Tag.TAG_LONG)) {
                data.putLong(IAIDO_READY_SINCE, now);
                if (data.getBoolean(IAIDO_CHAIN_ACTIVE)
                        && entity.level() instanceof ServerLevel serverLevel) {
                    data.remove(IAIDO_CHAIN_ACTIVE);
                    serverLevel.playSound(
                            null,
                            entity.blockPosition(),
                            SoundEvents.IRON_TRAPDOOR_CLOSE,
                            SoundSource.PLAYERS,
                            0.48F,
                            1.72F);
                }
            } else if (now - data.getLong(IAIDO_READY_SINCE) == IaidoBalance.READY_TICKS
                    && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                        null,
                        entity.blockPosition(),
                        SoundEvents.ARMOR_EQUIP_IRON,
                        SoundSource.PLAYERS,
                        0.24F,
                        1.75F);
            }
        } else if (!ModComboStates.isIaidoDraw(combo)) {
            data.remove(IAIDO_READY_SINCE);
            if (ModComboStates.isIaidoAttack(combo)) {
                data.putBoolean(IAIDO_CHAIN_ACTIVE, true);
            }
        }
    }

    private static boolean consumeIaidoReadiness(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        long since = data.getLong(IAIDO_READY_SINCE);
        data.remove(IAIDO_READY_SINCE);
        return since > 0L
                && data.getLong(IAIDO_DISRUPTED_UNTIL) < user.level().getGameTime()
                && user.level().getGameTime() - since >= IaidoBalance.READY_TICKS;
    }

    private static IaidoDrawPower resolveIaidoDrawPower(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        long now = user.level().getGameTime();
        if (data.getLong(IAIDO_DRAW_POWER_UNTIL) >= now
                && data.contains(IAIDO_DRAW_MULTIPLIER, Tag.TAG_DOUBLE)) {
            double multiplier = data.getDouble(IAIDO_DRAW_MULTIPLIER);
            return new IaidoDrawPower(multiplier, iaidoTier(multiplier), false);
        }
        boolean prepared = consumeIaidoReadiness(user);
        boolean approached = IaidoTrickHandler.consumeApproachBonus(user);
        double multiplier = IaidoBalance.drawMultiplier(prepared, approached);
        data.putDouble(IAIDO_DRAW_MULTIPLIER, multiplier);
        data.putLong(IAIDO_DRAW_POWER_UNTIL, now + 3L);
        return new IaidoDrawPower(multiplier, prepared ? (approached ? 3 : 2) : 1, true);
    }

    private static double consumeIaidoDrawShare(LivingEntity user, boolean fresh) {
        CompoundTag data = user.getPersistentData();
        if (fresh) {
            data.putInt(IAIDO_DRAW_SLASH_COUNT, 0);
        }
        int slashIndex = Math.max(0, data.getInt(IAIDO_DRAW_SLASH_COUNT));
        data.putInt(IAIDO_DRAW_SLASH_COUNT, slashIndex + 1);
        return IaidoBalance.drawSlashShare(slashIndex);
    }

    private static int iaidoTier(double multiplier) {
        if (multiplier >= IaidoBalance.APPROACH_MULTIPLIER) return 3;
        if (multiplier >= IaidoBalance.PREPARED_MULTIPLIER) return 2;
        return 1;
    }

    private static void markIaidoSpacing(LivingEntity attacker, LivingEntity target) {
        IaidoBalance.Spacing spacing = IaidoBalance.spacing(horizontalDistance(attacker, target));
        CompoundTag data = attacker.getPersistentData();
        data.putInt(IAIDO_SPACING, spacing.ordinal());
        data.putLong(IAIDO_SPACING_UNTIL, attacker.level().getGameTime() + 3L);
        if (spacing != IaidoBalance.Spacing.OPTIMAL) {
            int tier = spacing == IaidoBalance.Spacing.CLOSE ? 2 : 1;
            data.putInt(IAIDO_FEEDBACK_TIER, Math.min(data.getInt(IAIDO_FEEDBACK_TIER), tier));
        }
    }

    private static IaidoBalance.Spacing currentIaidoSpacing(LivingEntity attacker) {
        CompoundTag data = attacker.getPersistentData();
        if (data.getLong(IAIDO_SPACING_UNTIL) < attacker.level().getGameTime()) {
            return IaidoBalance.Spacing.PRESSED;
        }
        int ordinal = data.getInt(IAIDO_SPACING);
        IaidoBalance.Spacing[] values = IaidoBalance.Spacing.values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    private static double horizontalDistance(LivingEntity first, LivingEntity second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return Math.sqrt(x * x + z * z);
    }

    private static void disruptIaido(LivingEntity defender) {
        CompoundTag data = defender.getPersistentData();
        long now = defender.level().getGameTime();
        data.remove(IAIDO_READY_SINCE);
        data.remove(IAIDO_PERFECT_UNTIL);
        data.remove(IAIDO_DRAW_MULTIPLIER);
        data.remove(IAIDO_DRAW_POWER_UNTIL);
        data.remove(IAIDO_DRAW_SLASH_COUNT);
        data.putLong(IAIDO_DISRUPTED_UNTIL, now + IaidoBalance.DISRUPTED_TICKS);
    }

    private static void markIaidoDeflect(LivingEntity defender) {
        CompoundTag data = defender.getPersistentData();
        long now = defender.level().getGameTime();
        data.putLong(IAIDO_DEFLECT_UNTIL, now + 1L);
        if (defender.level() instanceof ServerLevel level) {
            level.playSound(null, defender.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.42F, 1.75F);
            level.playSound(null, defender.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 0.65F, 1.35F);
            level.sendParticles(ParticleTypes.CRIT,
                    defender.getX(), defender.getY() + defender.getBbHeight() * 0.58D,
                    defender.getZ(), 10, 0.35D, 0.38D, 0.35D, 0.12D);
        }
    }

    private static boolean hasJustDeflected(LivingEntity defender) {
        return defender.getPersistentData().getLong(IAIDO_DEFLECT_UNTIL)
                >= defender.level().getGameTime();
    }

    private static void markPerfectIaido(LivingEntity user) {
        user.getPersistentData().putLong(
                IAIDO_PERFECT_UNTIL, user.level().getGameTime() + 2L);
    }

    private static boolean isPerfectIaidoVisual(LivingEntity user) {
        return user.getPersistentData().getLong(IAIDO_PERFECT_UNTIL)
                >= user.level().getGameTime();
    }

    private static void markIaidoFeedback(LivingEntity user, int tier) {
        CompoundTag data = user.getPersistentData();
        data.putInt(IAIDO_FEEDBACK_TIER, tier);
        data.putLong(IAIDO_FEEDBACK_UNTIL, user.level().getGameTime() + 3L);
        data.putBoolean(IAIDO_CHAIN_ACTIVE, true);
    }

    private static int consumeIaidoFeedback(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        int tier = data.getLong(IAIDO_FEEDBACK_UNTIL) >= user.level().getGameTime()
                ? data.getInt(IAIDO_FEEDBACK_TIER)
                : 1;
        clearIaidoFeedback(data);
        return Math.max(1, Math.min(3, tier));
    }

    private static void clearIaidoFeedback(CompoundTag data) {
        data.remove(IAIDO_FEEDBACK_TIER);
        data.remove(IAIDO_FEEDBACK_UNTIL);
    }

    private static void playIaidoDrawFeedback(LivingEntity user, int tier) {
        if (!(user.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.playSound(
                null,
                user.blockPosition(),
                SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS,
                tier >= 2 ? 0.32F : 0.22F,
                tier >= 3 ? 1.92F : 1.78F);
        serverLevel.playSound(
                null,
                user.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS,
                tier >= 2 ? 0.58F : 0.38F,
                tier >= 3 ? 1.54F : 1.68F);
    }

    private static void playIaidoImpactFeedback(
            LivingEntity user,
            LivingEntity target,
            ItemStack blade,
            int tier) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double centerY = target.getY() + target.getBbHeight() * 0.56D;
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.TRIDENT_HIT,
                SoundSource.PLAYERS,
                tier >= 2 ? 0.82F : 0.52F,
                tier >= 3 ? 1.38F : 1.55F);
        serverLevel.playSound(
                null,
                target.blockPosition(),
                SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS,
                tier >= 2 ? 0.68F : 0.38F,
                tier >= 3 ? 0.82F : 1.06F);

        serverLevel.sendParticles(
                tier >= 2 ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                target.getX(), centerY, target.getZ(),
                tier >= 3 ? 8 : tier >= 2 ? 5 : 3,
                target.getBbWidth() * 0.28D,
                target.getBbHeight() * 0.18D,
                target.getBbWidth() * 0.28D,
                tier >= 2 ? 0.055D : 0.035D);

        if (tier >= 2) {
            spawnIaidoCutLine(serverLevel, user, target, blade, tier);
            if (user instanceof ServerPlayer serverPlayer) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new IaidoImpactPacket(tier));
            }
        }
    }

    private static void spawnIaidoCutLine(
            ServerLevel level,
            LivingEntity user,
            LivingEntity target,
            ItemStack blade,
            int tier) {
        int color = MaterialSlashEffectResolver.resolve(blade).color();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(
                        Math.min(1.0F, red * 0.45F + 0.55F),
                        Math.min(1.0F, green * 0.45F + 0.55F),
                        Math.min(1.0F, blue * 0.45F + 0.55F)),
                tier >= 3 ? 0.82F : 0.68F);

        Vec3 look = user.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.18D, look.x).normalize();
        double halfLength = tier >= 3 ? 2.15D : 1.65D;
        int points = tier >= 3 ? 19 : 15;
        Vec3 center = new Vec3(
                target.getX(),
                target.getY() + target.getBbHeight() * 0.58D,
                target.getZ());
        for (int i = 0; i < points; i++) {
            double offset = -halfLength + (halfLength * 2.0D * i / (points - 1));
            Vec3 point = center.add(right.scale(offset));
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static boolean lockIaidoTarget(
            LivingEntity attacker,
            LivingEntity target) {
        CompoundTag attackerData = attacker.getPersistentData();
        long now = attacker.level().getGameTime();
        long until = attackerData.getLong(IAIDO_TARGET_UNTIL);

        if (until >= now && attackerData.hasUUID(IAIDO_TARGET)) {
            return target.getUUID().equals(attackerData.getUUID(IAIDO_TARGET));
        }

        attackerData.putUUID(IAIDO_TARGET, target.getUUID());
        attackerData.putLong(IAIDO_TARGET_UNTIL, now + 5L);
        return true;
    }

    static void primeIaidoTarget(
            LivingEntity attacker,
            LivingEntity target,
            long until) {
        CompoundTag data = attacker.getPersistentData();
        data.putUUID(IAIDO_TARGET, target.getUUID());
        data.putLong(IAIDO_TARGET_UNTIL, until);
    }

    private static boolean isInsideFrontArc(
            LivingEntity attacker,
            LivingEntity target,
            double range,
            double minimumDot) {
        Vec3 offset = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(attacker.position()
                        .add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D));
        Vec3 horizontalOffset = new Vec3(offset.x, 0.0D, offset.z);
        if (horizontalOffset.lengthSqr() > range * range) {
            return false;
        }
        if (horizontalOffset.lengthSqr() < 1.0E-6D) {
            return true;
        }

        Vec3 look = attacker.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        return horizontalLook.normalize().dot(horizontalOffset.normalize()) >= minimumDot;
    }

    private static boolean isIaidoSlash(ResourceLocation combo) {
        return ModComboStates.isIaidoDraw(combo);
    }

    private static boolean isDangakuSlash(ResourceLocation combo) {
        return ModComboStates.DANGAKU_CLEAVE.getId().equals(combo);
    }

    private static boolean isDangakuSweep(ResourceLocation combo) {
        return ModComboStates.DANGAKU_SWEEP.getId().equals(combo);
    }

    private record IaidoDrawPower(double multiplier, int tier, boolean fresh) {
    }

    private static void spawnMaterialAccent(
            ServerLevel level,
            EntitySlashEffect slashEffect,
            MaterialSlashEffectResolver.SlashVisual visual,
            BladeStyle style,
            ResourceLocation combo) {
        if (visual.particle() == null) {
            return;
        }

        if (style != BladeStyle.IAIDO || !isIaidoSlash(combo)) {
            return;
        }

        int count = 5;
        double spread = 0.18D;
        level.sendParticles(
                visual.particle(),
                slashEffect.getX(),
                slashEffect.getY() + 0.85D,
                slashEffect.getZ(),
                count,
                spread,
                spread * 0.65D,
                spread,
                0.025D);
    }

    private StyleCombatHandler() {
    }
}
