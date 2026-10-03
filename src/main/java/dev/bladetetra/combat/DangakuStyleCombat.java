package dev.bladetetra.combat;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Dangaku-only combat rules.
 *
 * <p>The Forge subscriber stays in {@link StyleCombatHandler}; this class owns
 * Dangaku damage tuning, target geometry, armor timing and slash presentation.</p>
 */
final class DangakuStyleCombat {
    private static final double CLEAVE_RANGE = 6.25D;
    private static final double CLEAVE_MIN_DOT = Math.cos(Math.toRadians(45.0D));
    private static final double SWEEP_RANGE = 5.25D;
    private static final double SWEEP_MIN_DOT = Math.cos(Math.toRadians(110.0D));
    private static final int ARMOR_END_TICK = 11;

    static void onSlash(SlashBladeEvent.DoSlashEvent event, ResourceLocation combo) {
        if (isSlash(combo)) {
            event.setDamage(event.getDamage() * 1.12D);
            event.setKnockback(KnockBacks.smash);
        } else if (isSweep(combo)) {
            event.setDamage(event.getDamage() * 0.92D);
        }
    }

    static boolean acceptsTarget(
            LivingEntity attacker,
            LivingEntity target,
            ResourceLocation combo) {
        if (isSlash(combo)) {
            return StyleTargeting.isInsideFrontArc(
                    attacker, target, CLEAVE_RANGE, CLEAVE_MIN_DOT);
        }
        if (isSweep(combo)) {
            return StyleTargeting.isInsideFrontArc(
                    attacker, target, SWEEP_RANGE, SWEEP_MIN_DOT);
        }
        return true;
    }

    static void onBladeHit(SlashBladeEvent.HitEvent event, ResourceLocation combo) {
        if (!isSlash(combo)) {
            return;
        }
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

    static void applyArmor(LivingHurtEvent event) {
        if (event.getSource().getEntity() != null
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
                && isArmorWindow(event.getEntity())) {
            event.setAmount(event.getAmount() * 0.65F);
        }
    }

    static boolean isArmorWindow(LivingEntity entity) {
        ItemStack blade = entity.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || StyleResolver.resolve(blade) != BladeStyle.DANGAKU) {
            return false;
        }

        ResourceLocation combo = blade.getCapability(ModularSlashBladeItem.BLADESTATE)
                .map(state -> state.getComboSeq())
                .orElse(ComboStateRegistry.NONE.getId());
        return isSlash(combo) && ComboState.getElapsed(entity) <= ARMOR_END_TICK;
    }

    static int applySlashPresentation(EntitySlashEffect slashEffect,
            ResourceLocation combo, int color) {
        slashEffect.setBaseSize(isSlash(combo) ? 1.55F : 1.18F);
        return isSlash(combo)
                ? MaterialSlashEffectResolver.blendTowardBlack(color, 0.10F)
                : color;
    }

    static boolean isSlash(ResourceLocation combo) {
        return ModComboStates.DANGAKU_CLEAVE.getId().equals(combo);
    }

    static boolean isSweep(ResourceLocation combo) {
        return ModComboStates.DANGAKU_SWEEP.getId().equals(combo);
    }

    private DangakuStyleCombat() {
    }
}
