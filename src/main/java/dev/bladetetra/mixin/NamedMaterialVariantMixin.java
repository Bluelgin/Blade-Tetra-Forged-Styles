package dev.bladetetra.mixin;

import dev.bladetetra.forging.NamedBladeMaterial;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.VariantData;

/** Resolve before Tetra aggregates stats, so workbench and gameplay share one result. */
@Mixin(value = ItemModule.class, remap = false)
public abstract class NamedMaterialVariantMixin {
    @Shadow public abstract String getKey();
    @Shadow public abstract String getSlot();

    @Inject(method = "getVariantData(Lnet/minecraft/world/item/ItemStack;)Lse/mickelus/tetra/module/data/VariantData;",
            at = @At("RETURN"), cancellable = true)
    private void bladeTetra$sourceStats(ItemStack stack, CallbackInfoReturnable<VariantData> cir) {
        cir.setReturnValue(NamedBladeMaterial.resolve(stack, getKey(), getSlot(), cir.getReturnValue()));
    }
}
