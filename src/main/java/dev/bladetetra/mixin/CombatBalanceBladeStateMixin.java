package dev.bladetetra.mixin;

import dev.bladetetra.combat.CombatBalanceRuntime;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Observe real charge/release and automatic combo continuations; never change native timings. */
@Mixin(value = SlashBladeState.class, remap = false)
public abstract class CombatBalanceBladeStateMixin implements ISlashBladeState {
    @Override public ResourceLocation doChargeAction(LivingEntity user, int elapsed) {
        ResourceLocation result = CombatBalanceRuntime.slashArt(user, () -> ISlashBladeState.super.doChargeAction(user, elapsed));
        if (!result.equals(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId()))
            dev.bladetetra.customstyle.CustomComboRouter.interrupt(user);
        return result;
    }
    @Override public void updateComboSeq(LivingEntity user, ResourceLocation next) {
        CombatBalanceRuntime.prepareCombo(this, user, next);
        ISlashBladeState.super.updateComboSeq(user, next);
    }
    @Override public ResourceLocation progressCombo(LivingEntity user, boolean virtual) {
        if (!virtual) CombatBalanceRuntime.ordinaryCombo(user);
        ResourceLocation before = getComboSeq();
        ResourceLocation result = dev.bladetetra.customstyle.CustomComboRouter.progress(this, user, virtual,
                () -> ISlashBladeState.super.progressCombo(user, virtual));
        if (Boolean.getBoolean("blade_tetra.customStyleDebug")) com.mojang.logging.LogUtils.getLogger().info(
                "CUSTOM_PROGRESS side={} tick={} virtual={} before={} result={} actual={} entered={}",
                user.level().isClientSide() ? "client" : "server", user.level().getGameTime(), virtual,
                before, result, getComboSeq(), getLastActionTime());
        return result;
    }
}
