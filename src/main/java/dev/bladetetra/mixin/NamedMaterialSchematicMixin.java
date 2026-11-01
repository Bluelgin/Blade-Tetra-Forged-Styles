package dev.bladetetra.mixin;

import dev.bladetetra.forging.NamedBladeMaterial;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;

@Mixin(value = ConfigSchematic.class, remap = false)
public abstract class NamedMaterialSchematicMixin {
    @Inject(method = "applyOutcome", at = @At("TAIL"))
    private void bladeTetra$replaceIdentity(OutcomeDefinition outcome, ItemStack upgraded,
            boolean consume, String slot, Player player, CallbackInfo ci) {
        if (outcome.moduleKey != null) NamedBladeMaterial.clearReplacedSource(upgraded, slot);
    }

    @Inject(method = "acceptsMaterial", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$validateSource(ItemStack target, String slot, int index,
            ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (ingredient.getItem() instanceof NamedBladeIngotItem
                && (NamedBladeIngotItem.source(ingredient) == null
                    || !NamedBladeMaterial.supportedSlot(slot))) cir.setReturnValue(false);
    }

    @Inject(method = "applyUpgrade", at = @At("RETURN"))
    private void bladeTetra$captureSource(ItemStack target, ItemStack[] materials,
            boolean consume, String slot, Player player, CallbackInfoReturnable<ItemStack> cir) {
        // applyUpgrade is also the real workbench preview path, before crafting effects.
        NamedBladeMaterial.capture(cir.getReturnValue(), slot, materials);
    }
}
