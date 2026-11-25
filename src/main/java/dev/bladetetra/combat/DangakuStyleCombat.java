package dev.bladetetra.combat;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.easteregg.AkatsukiBloodBlade;
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

import static dev.bladetetra.combat.DangakuCombatValues.*;

/**
 * Dangaku-only combat rules.
 *
 * <p>The Forge subscriber stays in {@link StyleCombatHandler}; this class owns
 * Dangaku damage tuning, target geometry, armor timing and slash presentation.</p>
 */
final class DangakuStyleCombat {
    static void onSlash(SlashBladeEvent.DoSlashEvent event, ResourceLocation combo) {
        if (DangakuSpinCombos.SPIN.getId().equals(combo)) {
            event.setDamage(event.getDamage() * DangakuSpinRules.damageScale(event.isCritical()));
            event.setKnockback(KnockBacks.cancel);
        } else if (isCircle(combo)) {
            event.setDamage(event.getDamage() * DangakuCircleSlashRules.damageScale(event.isCritical()));
            event.setKnockback(KnockBacks.cancel);
        } else if (isSlash(combo)) {
            event.setDamage(event.getDamage() * HEAVY_DAMAGE);
            event.setKnockback(KnockBacks.smash);
        } else if (isSweep(combo)) {
            event.setDamage(event.getDamage() * SWEEP_DAMAGE);
        }
    }

    static boolean acceptsTarget(
            LivingEntity attacker,
            LivingEntity target,
            ResourceLocation combo) {
        // Native Circle Slash owns reach; the old front-only gate must not cut off its rear half.
        if (isCircle(combo) || DangakuSpinCombos.SPIN.getId().equals(combo)) return true;
        if (isSlash(combo)) {
            return StyleTargeting.isInsideFrontArc(
                    attacker, target, CLEAVE_RANGE + AkatsukiBloodBlade.extraReach(attacker), CLEAVE_MIN_DOT);
        }
        if (isSweep(combo)) {
            return StyleTargeting.isInsideFrontArc(
                    attacker, target, SWEEP_RANGE + AkatsukiBloodBlade.extraReach(attacker), SWEEP_MIN_DOT);
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
            event.setAmount(event.getAmount() * ARMOR_DAMAGE);
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
        if (DangakuSpinCombos.SPIN.getId().equals(combo)) {
            slashEffect.setBaseSize(SWEEP_SLASH_SIZE);
            return color;
        }
        if (BranchingStyleCombos.phase(combo) == null) return color;
        slashEffect.setBaseSize(isSlash(combo) ? HEAVY_SLASH_SIZE : SWEEP_SLASH_SIZE);
        return isSlash(combo)
                ? MaterialSlashEffectResolver.blendTowardBlack(color, 0.10F)
                : color;
    }

    static boolean isSlash(ResourceLocation combo) {
        return BranchingStyleCombos.phase(combo) == StyleBranchRules.Phase.D_HEAVY
                || BranchingStyleCombos.phase(combo) == StyleBranchRules.Phase.D_AIR_HEAVY;
    }

    static boolean isSweep(ResourceLocation combo) {
        var phase = BranchingStyleCombos.phase(combo);
        return phase == StyleBranchRules.Phase.D_SWEEP || phase == StyleBranchRules.Phase.D_RETURN
                || phase == StyleBranchRules.Phase.D_LAND
                || phase == StyleBranchRules.Phase.D_AIR_FIRST || phase == StyleBranchRules.Phase.D_AIR_SECOND;
    }

    static boolean isCircle(ResourceLocation combo) {
        return DangakuCircleSlashRules.matches(BranchingStyleCombos.phase(combo));
    }

    private DangakuStyleCombat() {
    }
}
