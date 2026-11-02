package dev.bladetetra.client;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Development-only input for the existing screenshot suite; never packaged in the mod. */
@Mixin(value = Screen.class, remap = false)
abstract class ReviewInputMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true, remap = false)
    private static void reviewShift(CallbackInfoReturnable<Boolean> result) {
        if (SoulGuideReviewClient.enabled()) result.setReturnValue(SoulGuideReviewClient.expanded());
    }
}
