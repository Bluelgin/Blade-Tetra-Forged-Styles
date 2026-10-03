package dev.bladetetra.combat;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.network.IaidoImpactPacket;
import dev.bladetetra.network.ModNetwork;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Server-owned Iaido combat state and presentation.
 *
 * <p>This class intentionally contains the complete transient-NBT lifecycle for
 * one style. Keeping those keys and their cleanup together makes the state
 * machine reviewable without making the global Forge subscriber a god class.</p>
 */
final class IaidoStyleCombat {
    private static final double RANGE = 4.25D;
    private static final double MIN_DOT = Math.cos(Math.toRadians(35.0D));
    private static final float DEFLECT_DAMAGE_MULTIPLIER = 0.35F;

    private static final String TARGET = "blade_tetra_iaido_target";
    private static final String TARGET_UNTIL = "blade_tetra_iaido_target_until";
    private static final String READY_SINCE = "blade_tetra_iaido_ready_since";
    private static final String PERFECT_UNTIL = "blade_tetra_iaido_perfect_until";
    private static final String FEEDBACK_TIER = "blade_tetra_iaido_feedback_tier";
    private static final String FEEDBACK_UNTIL = "blade_tetra_iaido_feedback_until";
    private static final String CHAIN_ACTIVE = "blade_tetra_iaido_chain_active";
    private static final String DRAW_MULTIPLIER = "blade_tetra_iaido_draw_multiplier";
    private static final String DRAW_POWER_UNTIL = "blade_tetra_iaido_draw_power_until";
    private static final String DRAW_SLASH_COUNT = "blade_tetra_iaido_draw_slash_count";
    private static final String SPACING = "blade_tetra_iaido_spacing";
    private static final String SPACING_UNTIL = "blade_tetra_iaido_spacing_until";
    private static final String DISRUPTED_UNTIL = "blade_tetra_iaido_disrupted_until";
    private static final String DEFLECT_UNTIL = "blade_tetra_iaido_deflect_until";

    static void onSlash(SlashBladeEvent.DoSlashEvent event, ResourceLocation combo) {
        if (isSlash(combo)) {
            IaidoDrawPower draw = resolveDrawPower(event.getUser());
            int feedbackTier = draw.tier();
            double share = consumeDrawShare(event.getUser(), draw.fresh());
            event.setDamage(event.getDamage() * draw.multiplier() * share);
            event.setCritical(true);
            markFeedback(event.getUser(), feedbackTier);
            playDrawFeedback(event.getUser(), feedbackTier);
            if (draw.fresh() && feedbackTier >= 2) {
                markPerfect(event.getUser());
            }
        } else if (ModComboStates.isIaidoFinish(combo)) {
            event.setDamage(event.getDamage() * 1.45D);
            event.setCritical(true);
        } else if (ModComboStates.isIaidoAttack(combo)) {
            event.setDamage(event.getDamage() * 1.22D);
        }
    }

    static boolean acceptsTarget(
            LivingEntity attacker,
            LivingEntity target,
            ResourceLocation combo) {
        if (!ModComboStates.isIaidoAttack(combo)) {
            return true;
        }
        if (!StyleTargeting.isInsideFrontArc(attacker, target, RANGE, MIN_DOT)
                || !lockTarget(attacker, target)) {
            return false;
        }
        if (isSlash(combo)) {
            markSpacing(attacker, target);
        }
        return true;
    }

    static void onBladeHit(SlashBladeEvent.HitEvent event, ResourceLocation combo) {
        if (!isSlash(combo)) {
            return;
        }
        IaidoTrickHandler.markIaidoHit(event.getUser(), event.getTarget());
        int feedbackTier = consumeFeedback(event.getUser());
        playImpactFeedback(
                event.getUser(), event.getTarget(), event.getBlade(), feedbackTier);
    }

    static void onLivingHurt(LivingHurtEvent event) {
        applySpacing(event);
        applyPerfectBonus(event);

        LivingEntity defender = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != defender && isHoldingIaido(defender)) {
            if (!event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                    && isDeflectWindow(defender)) {
                event.setAmount(event.getAmount() * DEFLECT_DAMAGE_MULTIPLIER);
                markDeflect(defender);
            } else {
                disrupt(defender);
            }
        }
    }

    static void onLivingTick(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }

        CompoundTag data = entity.getPersistentData();
        boolean modularBlade = entity.getMainHandItem().getItem() instanceof ModularSlashBladeItem;
        boolean hasIaidoState = hasTickState(data);
        if (!modularBlade && !hasIaidoState) {
            return;
        }

        long now = entity.level().getGameTime();
        if (data.contains(TARGET_UNTIL, Tag.TAG_LONG)
                && data.getLong(TARGET_UNTIL) < now) {
            data.remove(TARGET);
            data.remove(TARGET_UNTIL);
        }
        if (data.contains(PERFECT_UNTIL, Tag.TAG_LONG)
                && data.getLong(PERFECT_UNTIL) < now) {
            data.remove(PERFECT_UNTIL);
        }
        if (data.contains(FEEDBACK_UNTIL, Tag.TAG_LONG)
                && data.getLong(FEEDBACK_UNTIL) < now) {
            clearFeedback(data);
        }
        if (data.contains(DRAW_POWER_UNTIL, Tag.TAG_LONG)
                && data.getLong(DRAW_POWER_UNTIL) < now) {
            data.remove(DRAW_MULTIPLIER);
            data.remove(DRAW_POWER_UNTIL);
            data.remove(DRAW_SLASH_COUNT);
        }
        if (data.contains(SPACING_UNTIL, Tag.TAG_LONG)
                && data.getLong(SPACING_UNTIL) < now) {
            data.remove(SPACING);
            data.remove(SPACING_UNTIL);
        }
        if (data.contains(DEFLECT_UNTIL, Tag.TAG_LONG)
                && data.getLong(DEFLECT_UNTIL) < now) {
            data.remove(DEFLECT_UNTIL);
        }
        if (data.contains(DISRUPTED_UNTIL, Tag.TAG_LONG)
                && data.getLong(DISRUPTED_UNTIL) < now) {
            data.remove(DISRUPTED_UNTIL);
        }
        updateReadiness(entity, data, now);
    }

    static int applySlashPresentation(
            EntitySlashEffect slashEffect,
            LivingEntity user,
            ItemStack blade,
            ResourceLocation combo,
            MaterialSlashEffectResolver.SlashVisual visual,
            int color,
            ServerLevel level) {
        boolean perfect = isSlash(combo) && isPerfectVisual(user);
        slashEffect.setBaseSize(isSlash(combo)
                ? (perfect ? 0.96F : 0.76F) : 0.86F);
        if (isSlash(combo)) {
            color = MaterialSlashEffectResolver.blendTowardWhite(
                    color, perfect ? 0.62F : 0.24F);
            spawnMaterialAccent(level, slashEffect, visual, combo);
        }
        return color;
    }

    static float calculatePerfectBonus(float targetMaxHealth) {
        return IaidoBalance.perfectBonus(targetMaxHealth);
    }

    static void primeTarget(LivingEntity attacker, LivingEntity target, long until) {
        CompoundTag data = attacker.getPersistentData();
        data.putUUID(TARGET, target.getUUID());
        data.putLong(TARGET_UNTIL, until);
    }

    static boolean hasJustDeflected(LivingEntity defender) {
        return defender.getPersistentData().getLong(DEFLECT_UNTIL)
                >= defender.level().getGameTime();
    }

    static boolean hasTickState(CompoundTag data) {
        return data.contains(TARGET_UNTIL, Tag.TAG_LONG)
                || data.contains(READY_SINCE, Tag.TAG_LONG)
                || data.contains(PERFECT_UNTIL, Tag.TAG_LONG)
                || data.contains(FEEDBACK_UNTIL, Tag.TAG_LONG)
                || data.contains(DRAW_POWER_UNTIL, Tag.TAG_LONG)
                || data.contains(SPACING_UNTIL, Tag.TAG_LONG)
                || data.contains(DEFLECT_UNTIL, Tag.TAG_LONG)
                || data.contains(DISRUPTED_UNTIL, Tag.TAG_LONG)
                || data.getBoolean(CHAIN_ACTIVE);
    }

    private static void applyPerfectBonus(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || attacker.level().isClientSide()) {
            return;
        }

        ItemStack blade = attacker.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO
                || !isPerfectVisual(attacker)) {
            return;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        if (!isSlash(combo)
                || !isLockedTarget(attacker, event.getEntity())
                || currentSpacing(attacker) != IaidoBalance.Spacing.OPTIMAL) {
            attacker.getPersistentData().remove(PERFECT_UNTIL);
            return;
        }

        event.setAmount(event.getAmount()
                + calculatePerfectBonus(event.getEntity().getMaxHealth()));
        attacker.getPersistentData().remove(PERFECT_UNTIL);
    }

    private static void applySpacing(LivingHurtEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)
                || event.getSource().getDirectEntity() != attacker
                || !isHoldingIaido(attacker)) {
            return;
        }
        CompoundTag data = attacker.getPersistentData();
        long now = attacker.level().getGameTime();
        if (data.getLong(DRAW_POWER_UNTIL) < now
                || !data.contains(DRAW_MULTIPLIER, Tag.TAG_DOUBLE)) {
            return;
        }
        if (!lockTarget(attacker, event.getEntity())) {
            event.setAmount(0.0F);
            return;
        }
        markSpacing(attacker, event.getEntity());
        double drawMultiplier = data.getDouble(DRAW_MULTIPLIER);
        double effective = IaidoBalance.effectiveMultiplier(
                drawMultiplier, currentSpacing(attacker));
        if (drawMultiplier > 0.0D && effective < drawMultiplier) {
            event.setAmount((float) (event.getAmount() * effective / drawMultiplier));
        }
    }

    private static boolean isLockedTarget(LivingEntity attacker, LivingEntity target) {
        CompoundTag data = attacker.getPersistentData();
        return data.getLong(TARGET_UNTIL) >= attacker.level().getGameTime()
                && data.hasUUID(TARGET)
                && target.getUUID().equals(data.getUUID(TARGET));
    }

    private static boolean isHoldingIaido(LivingEntity entity) {
        ItemStack blade = entity.getMainHandItem();
        return blade.getItem() instanceof ModularSlashBladeItem
                && StyleResolver.resolve(blade) == BladeStyle.IAIDO;
    }

    private static boolean isDeflectWindow(LivingEntity entity) {
        if (!isHoldingIaido(entity)) {
            return false;
        }
        ItemStack blade = entity.getMainHandItem();
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return ModComboStates.isIaidoDraw(combo)
                && ComboState.getElapsed(entity) <= IaidoBalance.DEFLECT_END_TICK;
    }

    private static void updateReadiness(LivingEntity entity, CompoundTag data, long now) {
        ItemStack blade = entity.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.IAIDO) {
            data.remove(READY_SINCE);
            return;
        }
        if (data.getLong(DISRUPTED_UNTIL) >= now) {
            data.remove(READY_SINCE);
            return;
        }
        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        boolean settled = ComboStateRegistry.NONE.getId().equals(combo)
                || ModComboStates.isIaidoSheathe(combo);
        if (settled) {
            if (!data.contains(READY_SINCE, Tag.TAG_LONG)) {
                data.putLong(READY_SINCE, now);
                if (data.getBoolean(CHAIN_ACTIVE)
                        && entity.level() instanceof ServerLevel serverLevel) {
                    data.remove(CHAIN_ACTIVE);
                    serverLevel.playSound(
                            null, entity.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                            SoundSource.PLAYERS, 0.48F, 1.72F);
                }
            } else if (now - data.getLong(READY_SINCE) == IaidoBalance.READY_TICKS
                    && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(
                        null, entity.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON,
                        SoundSource.PLAYERS, 0.24F, 1.75F);
            }
        } else if (!ModComboStates.isIaidoDraw(combo)) {
            data.remove(READY_SINCE);
            if (ModComboStates.isIaidoAttack(combo)) {
                data.putBoolean(CHAIN_ACTIVE, true);
            }
        }
    }

    private static boolean consumeReadiness(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        long since = data.getLong(READY_SINCE);
        data.remove(READY_SINCE);
        return since > 0L
                && data.getLong(DISRUPTED_UNTIL) < user.level().getGameTime()
                && user.level().getGameTime() - since >= IaidoBalance.READY_TICKS;
    }

    private static IaidoDrawPower resolveDrawPower(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        long now = user.level().getGameTime();
        if (data.getLong(DRAW_POWER_UNTIL) >= now
                && data.contains(DRAW_MULTIPLIER, Tag.TAG_DOUBLE)) {
            double multiplier = data.getDouble(DRAW_MULTIPLIER);
            return new IaidoDrawPower(multiplier, tier(multiplier), false);
        }
        boolean prepared = consumeReadiness(user);
        boolean approached = IaidoTrickHandler.consumeApproachBonus(user);
        double multiplier = IaidoBalance.drawMultiplier(prepared, approached);
        data.putDouble(DRAW_MULTIPLIER, multiplier);
        data.putLong(DRAW_POWER_UNTIL, now + 3L);
        return new IaidoDrawPower(multiplier, prepared ? (approached ? 3 : 2) : 1, true);
    }

    private static double consumeDrawShare(LivingEntity user, boolean fresh) {
        CompoundTag data = user.getPersistentData();
        if (fresh) {
            data.putInt(DRAW_SLASH_COUNT, 0);
        }
        int slashIndex = Math.max(0, data.getInt(DRAW_SLASH_COUNT));
        data.putInt(DRAW_SLASH_COUNT, slashIndex + 1);
        return IaidoBalance.drawSlashShare(slashIndex);
    }

    private static int tier(double multiplier) {
        if (multiplier >= IaidoBalance.APPROACH_MULTIPLIER) {
            return 3;
        }
        if (multiplier >= IaidoBalance.PREPARED_MULTIPLIER) {
            return 2;
        }
        return 1;
    }

    private static void markSpacing(LivingEntity attacker, LivingEntity target) {
        IaidoBalance.Spacing spacing = IaidoBalance.spacing(horizontalDistance(attacker, target));
        CompoundTag data = attacker.getPersistentData();
        data.putInt(SPACING, spacing.ordinal());
        data.putLong(SPACING_UNTIL, attacker.level().getGameTime() + 3L);
        if (spacing != IaidoBalance.Spacing.OPTIMAL) {
            int feedback = spacing == IaidoBalance.Spacing.CLOSE ? 2 : 1;
            data.putInt(FEEDBACK_TIER, Math.min(data.getInt(FEEDBACK_TIER), feedback));
        }
    }

    private static IaidoBalance.Spacing currentSpacing(LivingEntity attacker) {
        CompoundTag data = attacker.getPersistentData();
        if (data.getLong(SPACING_UNTIL) < attacker.level().getGameTime()) {
            return IaidoBalance.Spacing.PRESSED;
        }
        int ordinal = data.getInt(SPACING);
        IaidoBalance.Spacing[] values = IaidoBalance.Spacing.values();
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    private static double horizontalDistance(LivingEntity first, LivingEntity second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return Math.sqrt(x * x + z * z);
    }

    private static void disrupt(LivingEntity defender) {
        CompoundTag data = defender.getPersistentData();
        long now = defender.level().getGameTime();
        data.remove(READY_SINCE);
        data.remove(PERFECT_UNTIL);
        data.remove(DRAW_MULTIPLIER);
        data.remove(DRAW_POWER_UNTIL);
        data.remove(DRAW_SLASH_COUNT);
        data.putLong(DISRUPTED_UNTIL, now + IaidoBalance.DISRUPTED_TICKS);
    }

    private static void markDeflect(LivingEntity defender) {
        CompoundTag data = defender.getPersistentData();
        long now = defender.level().getGameTime();
        data.putLong(DEFLECT_UNTIL, now + 1L);
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

    private static void markPerfect(LivingEntity user) {
        user.getPersistentData().putLong(
                PERFECT_UNTIL, user.level().getGameTime() + 2L);
    }

    private static boolean isPerfectVisual(LivingEntity user) {
        return user.getPersistentData().getLong(PERFECT_UNTIL)
                >= user.level().getGameTime();
    }

    private static void markFeedback(LivingEntity user, int tier) {
        CompoundTag data = user.getPersistentData();
        data.putInt(FEEDBACK_TIER, tier);
        data.putLong(FEEDBACK_UNTIL, user.level().getGameTime() + 3L);
        data.putBoolean(CHAIN_ACTIVE, true);
    }

    private static int consumeFeedback(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        int tier = data.getLong(FEEDBACK_UNTIL) >= user.level().getGameTime()
                ? data.getInt(FEEDBACK_TIER)
                : 1;
        clearFeedback(data);
        return Math.max(1, Math.min(3, tier));
    }

    private static void clearFeedback(CompoundTag data) {
        data.remove(FEEDBACK_TIER);
        data.remove(FEEDBACK_UNTIL);
    }

    private static void playDrawFeedback(LivingEntity user, int tier) {
        if (!(user.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.playSound(
                null, user.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS, tier >= 2 ? 0.32F : 0.22F,
                tier >= 3 ? 1.92F : 1.78F);
        serverLevel.playSound(
                null, user.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, tier >= 2 ? 0.58F : 0.38F,
                tier >= 3 ? 1.54F : 1.68F);
    }

    private static void playImpactFeedback(
            LivingEntity user,
            LivingEntity target,
            ItemStack blade,
            int tier) {
        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double centerY = target.getY() + target.getBbHeight() * 0.56D;
        serverLevel.playSound(
                null, target.blockPosition(), SoundEvents.TRIDENT_HIT,
                SoundSource.PLAYERS, tier >= 2 ? 0.82F : 0.52F,
                tier >= 3 ? 1.38F : 1.55F);
        serverLevel.playSound(
                null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, tier >= 2 ? 0.68F : 0.38F,
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
            spawnCutLine(serverLevel, user, target, blade, tier);
            if (user instanceof ServerPlayer serverPlayer) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new IaidoImpactPacket(tier));
            }
        }
    }

    private static void spawnCutLine(
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
                target.getX(), target.getY() + target.getBbHeight() * 0.58D,
                target.getZ());
        for (int i = 0; i < points; i++) {
            double offset = -halfLength + (halfLength * 2.0D * i / (points - 1));
            Vec3 point = center.add(right.scale(offset));
            level.sendParticles(dust, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static boolean lockTarget(LivingEntity attacker, LivingEntity target) {
        CompoundTag attackerData = attacker.getPersistentData();
        long now = attacker.level().getGameTime();
        long until = attackerData.getLong(TARGET_UNTIL);

        if (until >= now && attackerData.hasUUID(TARGET)) {
            return target.getUUID().equals(attackerData.getUUID(TARGET));
        }

        attackerData.putUUID(TARGET, target.getUUID());
        attackerData.putLong(TARGET_UNTIL, now + 5L);
        return true;
    }

    private static boolean isSlash(ResourceLocation combo) {
        return ModComboStates.isIaidoDraw(combo);
    }

    private static void spawnMaterialAccent(
            ServerLevel level,
            EntitySlashEffect slashEffect,
            MaterialSlashEffectResolver.SlashVisual visual,
            ResourceLocation combo) {
        if (visual.particle() == null || !isSlash(combo)) {
            return;
        }
        double spread = 0.18D;
        level.sendParticles(
                visual.particle(),
                slashEffect.getX(),
                slashEffect.getY() + 0.85D,
                slashEffect.getZ(),
                5,
                spread,
                spread * 0.65D,
                spread,
                0.025D);
    }

    private record IaidoDrawPower(double multiplier, int tier, boolean fresh) {
    }

    private IaidoStyleCombat() {
    }
}
