package dev.bladetetra.mixin;

import dev.bladetetra.kyouka.AwakenedRankConstraintService;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRank;
import mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConcentrationRank.class, remap = false)
public abstract class AwakenedRankConstraintMixin implements IConcentrationRank {
    @Override public void addRankPoint(LivingEntity user, long point) {
        if (!AwakenedRankConstraintService.reject(this, user, point))
            IConcentrationRank.super.addRankPoint(user, point);
    }
    @Override public void addRankPoint(net.minecraft.world.damagesource.DamageSource source) {
        // The native DamageSource overload resolves timeout combos BEFORE calling the long overload.
        // Locked hits must return here, so secondary damage cannot advance the owner's combo.
        if (!AwakenedRankConstraintService.locked(this)) IConcentrationRank.super.addRankPoint(source);
    }
    @Inject(method = "setRawRankPoint", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$rejectRawGain(long point, CallbackInfo ci) {
        if (AwakenedRankConstraintService.locked(this) && point > getRawRankPoint()) ci.cancel();
    }
}
