package dev.btsealed.runtime;

import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.LogicalSide;

final class PortableComponentEffects {
    private static final String SPIRIT_UNTIL = "bt_export_spirit_saya_until";

    record Effects(double drawBonus, double spiritBonus, int spiritTicks, boolean heavyPommel,
                   int rankDivisor, float guardReduction, int guardDurability) {
        static final Effects NONE = new Effects(0, 0, 0, false, 0, 0, 0);
    }

    static void slash(SlashBladeEvent.DoSlashEvent event) {
        var effects = effects(event.getUser(), event.getBlade());
        if (effects == Effects.NONE) return;
        if (effects.drawBonus() > 0 && PortableCombos.isIaidoDraw(event.getSlashBladeState().getComboSeq())) {
            event.setDamage(event.getDamage() * (1 + effects.drawBonus()));
        }
        long until = event.getUser().getPersistentData().getLong(SPIRIT_UNTIL);
        if (effects.spiritBonus() > 0 && until > 0 && event.getUser().level().getGameTime() <= until) {
            event.setDamage(event.getDamage() * (1 + effects.spiritBonus()));
        }
        if (effects.heavyPommel() && event.isCritical() && event.getKnockback() == KnockBacks.cancel) {
            event.setKnockback(KnockBacks.toss);
        }
    }

    static void arts(SlashBladeEvent.PerformSlashArtEvent event) {
        var user = event.getEntityLiving();
        int ticks = effects(user, user.getMainHandItem()).spiritTicks();
        if (ticks > 0 && event.getComboState() != null
                && !ComboStateRegistry.NONE.getId().equals(event.getComboState())) {
            user.getPersistentData().putLong(SPIRIT_UNTIL, user.level().getGameTime() + ticks);
        }
    }

    static void hit(SlashBladeEvent.HitEvent event) {
        var user = event.getUser();
        if (user.level().isClientSide()) return;
        int divisor = effects(user, event.getBlade()).rankDivisor();
        if (divisor != 0) {
            user.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT).ifPresent(rank ->
                    rank.addRankPoint(user, Math.max(1L, rank.getUnitCapacity() / divisor)));
        }
    }

    static void guard(LivingHurtEvent event) {
        var user = event.getEntity();
        if (user.level().isClientSide() || !user.isCrouching()
                || event.getSource().getEntity() == null || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        var blade = user.getMainHandItem();
        var effects = effects(user, blade);
        if (effects.guardReduction() > 0) {
            event.setAmount(event.getAmount() * (1 - effects.guardReduction()));
            blade.hurtAndBreak(effects.guardDurability(), user, ItemSlashBlade.getOnBroken(blade));
        }
    }

    private static Effects effects(LivingEntity user, ItemStack blade) {
        var profile = PortableProfiles.forBlade(blade, user.level().isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER);
        return profile == null ? Effects.NONE : profile.effects();
    }

    private PortableComponentEffects() {}
}
