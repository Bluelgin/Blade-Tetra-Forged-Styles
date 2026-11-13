package dev.bladetetra.mixin;

import dev.bladetetra.forging.HabakiMaterialStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.data.MaterialVariantData;
import se.mickelus.tetra.module.data.VariantData;

/** Apply the material curve as Tetra builds each shared variant. */
@Mixin(value = MaterialVariantData.class, remap = false)
public abstract class HabakiMaterialVariantMixin {
    @Inject(method = "combine", at = @At("RETURN"))
    private void bladeTetra$applyHabakiStats(MaterialData material, CallbackInfoReturnable<VariantData> cir) {
        HabakiMaterialStats.apply((MaterialVariantData) (Object) this, material, cir.getReturnValue());
    }
}
