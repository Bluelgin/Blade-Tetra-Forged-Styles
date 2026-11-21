package dev.bladetetra.mixin;

import dev.bladetetra.combat.CombatBalanceRuntime;
import dev.bladetetra.combat.ProgrammaticFusionHandler;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Observe real charge/release and automatic combo continuations; never change native timings. */
@Mixin(value = SlashBladeState.class, remap = false)
public abstract class CombatBalanceBladeStateMixin implements ISlashBladeState,
        ProgrammaticFusionHandler.MotionRevision {
    @Unique private long bladeTetra$motionRevision;

    @Override public long bladeTetra$motionRevision() {
        return bladeTetra$motionRevision;
    }

    @Inject(method = "setComboSeq", at = @At("RETURN"))
    private void bladeTetra$recordMotion(ResourceLocation next, CallbackInfo callback) {
        bladeTetra$motionRevision++;
    }

    @Override public ResourceLocation doChargeAction(LivingEntity user, int elapsed) {
        ResourceLocation result = CombatBalanceRuntime.slashArt(user,
                () -> ProgrammaticFusionHandler.charge(this, user, elapsed,
                        () -> ISlashBladeState.super.doChargeAction(user, elapsed)));
        if (!result.equals(mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE.getId()))
            dev.bladetetra.customstyle.CustomComboRouter.interrupt(user);
        return result;
    }
    @Override public void updateComboSeq(LivingEntity user, ResourceLocation next) {
        CombatBalanceRuntime.prepareCombo(this, user, next);
        try (var motion=dev.bladetetra.easteregg.AkatsukiBloodBlade.observe(user)) {
            ProgrammaticFusionHandler.motion(this, user, next,
                    () -> ISlashBladeState.super.updateComboSeq(user, next));
            motion.committed(this);
        }
    }
    @Override public SlashArts getSlashArts() {
        SlashArts source = ProgrammaticFusionHandler.contextualArt(this);
        return source != null ? source : ISlashBladeState.super.getSlashArts();
    }

    @Override public Map.Entry<Integer, ResourceLocation> resolvCurrentComboStateTicks(LivingEntity user) {
        return ProgrammaticFusionHandler.resolveTimeout(this, user,
                () -> ISlashBladeState.super.resolvCurrentComboStateTicks(user));
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
