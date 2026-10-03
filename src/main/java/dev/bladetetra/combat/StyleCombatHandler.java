package dev.bladetetra.combat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.visual.MaterialSlashEffectResolver;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Thin Forge-event facade for style combat.
 *
 * <p>Style-specific state machines live in {@link IaidoStyleCombat} and
 * {@link DangakuStyleCombat}. This class owns only event filtering and routing,
 * so adding another style cannot turn the global subscriber back into a god
 * class.</p>
 */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StyleCombatHandler {
    @SubscribeEvent
    public static void onSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getBlade().getItem() instanceof ModularSlashBladeItem)) {
            return;
        }

        BladeStyle style = StyleResolver.resolve(event.getBlade());
        ResourceLocation combo = event.getSlashBladeState().getComboSeq();
        switch (style) {
            case IAIDO -> IaidoStyleCombat.onSlash(event, combo);
            case DANGAKU -> DangakuStyleCombat.onSlash(event, combo);
            case RENGEKI -> {
                // Wakizashi already trades per-hit damage for speed and pressure.
            }
            default -> {
            }
        }
    }

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

        boolean accepted = switch (style) {
            case IAIDO -> IaidoStyleCombat.acceptsTarget(
                    attacker, event.getEntity(), combo);
            case DANGAKU -> DangakuStyleCombat.acceptsTarget(
                    attacker, event.getEntity(), combo);
            default -> true;
        };
        if (!accepted) {
            event.setCanceled(true);
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
        switch (style) {
            case IAIDO -> IaidoStyleCombat.onBladeHit(event, combo);
            case DANGAKU -> DangakuStyleCombat.onBladeHit(event, combo);
            default -> {
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        IaidoStyleCombat.onLivingHurt(event);
        DangakuStyleCombat.applyArmor(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity defender = event.getEntity();
        if (DangakuStyleCombat.isArmorWindow(defender)
                || IaidoStyleCombat.hasJustDeflected(defender)) {
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
            color = IaidoStyleCombat.applySlashPresentation(
                    slashEffect, user, combo, visual, color,
                    (ServerLevel) event.getLevel());
        } else if (style == BladeStyle.RENGEKI) {
            slashEffect.setBaseSize(0.82F);
        } else if (style == BladeStyle.DANGAKU) {
            color = DangakuStyleCombat.applySlashPresentation(
                    slashEffect, combo, color);
        }
        slashEffect.setColor(color);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        IaidoStyleCombat.onLivingTick(event.getEntity());
    }

    static void primeIaidoTarget(
            LivingEntity attacker,
            LivingEntity target,
            long until) {
        IaidoStyleCombat.primeTarget(attacker, target, until);
    }

    private StyleCombatHandler() {
    }
}
