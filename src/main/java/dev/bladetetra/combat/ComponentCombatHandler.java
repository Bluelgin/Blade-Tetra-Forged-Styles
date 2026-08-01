package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Combat behavior supplied by non-blade components. All effects are scoped to
 * the modular slashblade and derive directly from installed Tetra modules.
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ComponentCombatHandler {
    private static final String SPIRIT_SAYA_UNTIL = "blade_tetra_spirit_saya_until";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSlash(SlashBladeEvent.DoSlashEvent event) {
        ItemStack blade = event.getBlade();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.SAYA_SLOT,
                ModularSlashBladeItem.QUICKDRAW_SAYA_MODULE)
                && ModComboStates.IAIDO_DRAW.getId().equals(
                        event.getSlashBladeState().getComboSeq())) {
            event.setDamage(event.getDamage() * 1.15D);
        }

        long spiritUntil = event.getUser().getPersistentData().getLong(SPIRIT_SAYA_UNTIL);
        if (spiritUntil > 0L
                && event.getUser().level().getGameTime() > spiritUntil) {
            event.getUser().getPersistentData().remove(SPIRIT_SAYA_UNTIL);
            spiritUntil = 0L;
        }
        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.SAYA_SLOT,
                ModularSlashBladeItem.SPIRIT_SAYA_MODULE)
                && event.getUser().level().getGameTime() <= spiritUntil) {
            event.setDamage(event.getDamage() * 1.15D);
        }

        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.KASHIRA_SLOT,
                ModularSlashBladeItem.HEAVY_KASHIRA_MODULE)
                && event.isCritical()
                && event.getKnockback() == KnockBacks.cancel) {
            event.setKnockback(KnockBacks.toss);
        }
    }

    @SubscribeEvent
    public static void onPerformSlashArt(SlashBladeEvent.PerformSlashArtEvent event) {
        LivingEntity user = event.getEntityLiving();
        ItemStack blade = user.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.HABAKI_SLOT,
                ModularSlashBladeItem.PRECISION_HABAKI_MODULE)
                && event.getType() == SlashArts.ArtsType.Success) {
            int extendedJustEnd = event.getSlashBladeState().getFullChargeTicks(user)
                    + SlashArts.getJustReceptionSpan(user)
                    + 2;
            if (event.getElapsed() < extendedJustEnd) {
                event.setComboState(
                        event.getSlashBladeState().getSlashArts().getComboStateJust(user));
            }
        }

        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.SAYA_SLOT,
                ModularSlashBladeItem.SPIRIT_SAYA_MODULE)
                && event.getComboState() != null
                && !ComboStateRegistry.NONE.getId().equals(event.getComboState())) {
            user.getPersistentData().putLong(
                    SPIRIT_SAYA_UNTIL,
                    user.level().getGameTime() + 40L);
        }
    }

    @SubscribeEvent
    public static void onBladeHit(SlashBladeEvent.HitEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)
                || event.getUser().level().isClientSide()) {
            return;
        }

        if (ComponentEffectResolver.hasModule(
                event.getBlade(),
                ModularSlashBladeItem.TSUKA_SLOT,
                ModularSlashBladeItem.STABLE_TSUKA_MODULE)) {
            event.getUser().getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> rank.addRankPoint(
                            event.getUser(),
                            Math.max(1L, rank.getUnitCapacity() / 50L)));
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity defender = event.getEntity();
        ItemStack blade = defender.getMainHandItem();
        if (!(blade.getItem() instanceof ModularSlashBladeItem)
                || defender.level().isClientSide()
                || !defender.isCrouching()
                || event.getSource().getEntity() == null
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        if (ComponentEffectResolver.hasModule(
                blade,
                ModularSlashBladeItem.TSUBA_SLOT,
                ModularSlashBladeItem.GUARD_TSUBA_MODULE)) {
            event.setAmount(event.getAmount() * 0.75F);
            blade.hurtAndBreak(1, defender, ItemSlashBlade.getOnBroken(blade));
        }
    }

    private ComponentCombatHandler() {
    }
}
