package dev.bladetetra.mixin;

import dev.bladetetra.forging.ForgingImprovements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.schematic.MaterialOutcomeDefinition;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;

import java.util.HashMap;

@Mixin(value = MaterialOutcomeDefinition.class, remap = false)
abstract class ForgingMaterialOutcomeMixin {
    @Inject(method = "combine", at = @At("RETURN"))
    private void bladeTetra$keepStructureId(MaterialData material, CallbackInfoReturnable<OutcomeDefinition> cir) {
        MaterialOutcomeDefinition template = (MaterialOutcomeDefinition) (Object) this;
        if (template.moduleKey != null || template.improvements == null) return;
        for (String key : ForgingImprovements.CONSTRUCTIONS) {
            Integer level = template.improvements.get(key);
            if (level == null) continue;
            OutcomeDefinition resolved = cir.getReturnValue();
            var improvements = new HashMap<>(resolved.improvements);
            // Tetra appends the material to improvements specified by a material outcome.
            // All metals use the same structural improvement and its existing saved ID.
            improvements.remove(key + material.key);
            improvements.put(key, level);
            resolved.improvements = improvements;
            return;
        }
    }
}
