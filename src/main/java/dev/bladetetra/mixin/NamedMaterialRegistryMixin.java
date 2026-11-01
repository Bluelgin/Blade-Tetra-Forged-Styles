package dev.bladetetra.mixin;

import dev.bladetetra.forging.NamedBladeMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.module.ModuleRegistry;
import se.mickelus.tetra.module.data.MaterialVariantData;
import se.mickelus.tetra.module.data.VariantData;
import se.mickelus.tetra.module.data.ModuleData;
import java.util.stream.Stream;

/** Retain data-pack extraction rules before Tetra expands the shared material. */
@Mixin(value = ModuleRegistry.class, remap = false)
public abstract class NamedMaterialRegistryMixin {
    @ModifyVariable(method = "setupModule", at = @At("HEAD"), argsOnly = true)
    private ModuleData bladeTetra$preserveTemplates(ModuleData data) {
        // Registry expansion must not erase extraction rules from the data synced to clients.
        for (VariantData variant : data.variants) {
            if (variant instanceof MaterialVariantData material)
                for (var id : material.materials)
                    if (id.toString().equals("tetra:named_blade/")) return data.shallowCopy();
        }
        return data;
    }
    @Inject(method = "setupModules", at = @At("HEAD"))
    private void bladeTetra$reload(CallbackInfo ci) { NamedBladeMaterial.clearTemplates(); }

    @Inject(method = "expandMaterialVariant", at = @At("HEAD"))
    private void bladeTetra$rememberExtraction(MaterialVariantData template,
            CallbackInfoReturnable<Stream<VariantData>> cir) { NamedBladeMaterial.register(template); }

    @Inject(method = "expandMaterialVariant", at = @At("RETURN"), cancellable = true)
    private void bladeTetra$catalogStats(MaterialVariantData template,
            CallbackInfoReturnable<Stream<VariantData>> cir) {
        // Only registry expansion is neutralized. Real per-stack combination keeps
        // the original extraction template, module base stats and source formula.
        cir.setReturnValue(cir.getReturnValue().map(NamedBladeMaterial::catalogPreview));
    }
}
