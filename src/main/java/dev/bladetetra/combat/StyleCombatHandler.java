package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

/**
 * Applies style-only combat rules. Every handler exits immediately for regular
 * Resharped blades, so the addon never changes their balance or combo behavior.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StyleCombatHandler {
    private static final double IAIDO_RANGE = 4.25D;
    private static final double IAIDO_MIN_DOT = Math.cos(Math.toRadians(28.0D));
    private static final double DANGAKU_CLEAVE_RANGE = 6.25D;
    private static final double DANGAKU_CLEAVE_MIN_DOT =
            Math.cos(Math.toRadians(45.0D));
    private static final double DANGAKU_SWEEP_RANGE = 5.25D;
    private static final double DANGAKU_SWEEP_MIN_DOT =
            Math.cos(Math.toRadians(110.0D));
    private static final int DANGAKU_ARMOR_END_TICK = 11;
    private static final int IAIDO_GUARD_END_TICK = 6;
    private static final int IAIDO_READY_TICKS = 6;

    private static final String IAIDO_TARGET = "blade_tetra_iaido_target";
    private static final String IAIDO_TARGET_UNTIL = "blade_tetra_iaido_target_until";
    private static final String IAIDO_READY_SINCE = "blade_tetra_iaido_ready_since";
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
                    boolean prepared = consumeIaidoReadiness(event.getUser());
                    event.setDamage(event.getDamage() * (prepared ? 2.00D : 1.60D));
                    event.setCritical(true);
                } else if (ModComboStates.isIaidoFinish(combo)) {
                    event.setDamage(event.getDamage() * 1.40D);
                    event.setCritical(true);
                } else if (ModComboStates.isIaidoAttack(combo)) {
                    event.setDamage(event.getDamage() * 1.15D);
                }
            }
            case RENGEKI -> {
                if (combo != null && combo.getPath().startsWith("combo_b")) {
                    event.setDamage(event.getDamage() * 0.90D);
                }
            }
            case DANGAKU -> {
                if (isDangakuSlash(combo)) {
                    event.setDamage(event.getDamage() * 1.35D);
                    event.setKnockback(KnockBacks.smash);
                } else if (isDangakuSweep(combo)) {
                    event.setDamage(event.getDamage() * 1.08D);
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
                || !(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(event.getBlade()) != BladeStyle.DANGAKU
                || !isDangakuSlash(event.getSlashBladeState().getComboSeq())) {
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

        LivingEntity defender = event.getEntity();
        if (event.getSource().getEntity() != null
                && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                && isIaidoGuardWindow(defender)) {
            event.setAmount(event.getAmount() * 0.75F);
        }
        if (event.getSource().getEntity() != null
                && !event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                && isDangakuArmorWindow(defender)) {
            event.setAmount(event.getAmount() * 0.65F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (isDangakuArmorWindow(event.getEntity())) {
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
            slashEffect.setBaseSize(isIaidoSlash(combo) ? 0.72F : 0.86F);
            if (isIaidoSlash(combo)) {
                color = MaterialSlashEffectResolver.blendTowardWhite(color, 0.24F);
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
        long now = entity.level().getGameTime();
        if (data.contains(IAIDO_TARGET_UNTIL, Tag.TAG_LONG)
                && data.getLong(IAIDO_TARGET_UNTIL) < now) {
            data.remove(IAIDO_TARGET);
            data.remove(IAIDO_TARGET_UNTIL);
        }
        if (data.contains(BROKEN_STANCE_UNTIL, Tag.TAG_LONG)
                && data.getLong(BROKEN_STANCE_UNTIL) < now) {
            clearBrokenStance(data);
        }
        updateIaidoReadiness(entity, data, now);
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
            event.setAmount(event.getAmount() * 1.15F);
            clearBrokenStance(targetData);
        }
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

    private static boolean isIaidoGuardWindow(LivingEntity entity) {
        ItemStack blade = entity.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            return false;
        }
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return ModComboStates.isIaidoDraw(combo)
                && ComboState.getElapsed(entity) <= IAIDO_GUARD_END_TICK;
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
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        boolean settled = ComboStateRegistry.NONE.getId().equals(combo)
                || ModComboStates.isIaidoSheathe(combo);
        if (settled) {
            if (!data.contains(IAIDO_READY_SINCE, Tag.TAG_LONG)) {
                data.putLong(IAIDO_READY_SINCE, now);
            }
        } else if (!ModComboStates.isIaidoDraw(combo)) {
            data.remove(IAIDO_READY_SINCE);
        }
    }

    private static boolean consumeIaidoReadiness(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        long since = data.getLong(IAIDO_READY_SINCE);
        data.remove(IAIDO_READY_SINCE);
        return since > 0L
                && user.level().getGameTime() - since >= IAIDO_READY_TICKS;
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
