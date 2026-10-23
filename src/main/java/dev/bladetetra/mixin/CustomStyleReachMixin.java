package dev.bladetetra.mixin;

import dev.bladetetra.customstyle.CustomBladeLength;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only the owner's native melee query; never the projectile-radius overload. */
@Mixin(value = TargetSelector.class, remap = false)
public abstract class CustomStyleReachMixin {
    @Inject(method = "getResolvedReach", at = @At("RETURN"), cancellable = true)
    private static void customLength(LivingEntity user, CallbackInfoReturnable<Double> callback) {
        callback.setReturnValue(CustomBladeLength.resolvedMeleeReach(user, callback.getReturnValue()));
    }
}
