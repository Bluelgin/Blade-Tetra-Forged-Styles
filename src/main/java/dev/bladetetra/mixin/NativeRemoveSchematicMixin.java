package dev.bladetetra.mixin;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.forging.NamedBackplate;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.schematic.RemoveSchematic;

@Mixin(value = RemoveSchematic.class, remap = false)
abstract class NativeRemoveSchematicMixin {
    @Redirect(method = "applyUpgrade", at = @At(value = "INVOKE",
            target = "Lse/mickelus/tetra/module/ItemModule;removeModule(Lnet/minecraft/world/item/ItemStack;)[Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack[] bladeTetra$refreshRemovedSlot(ItemModule module, ItemStack stack) {
        ItemStack[] removed = module.removeModule(stack);
        if (stack.getItem() instanceof ModularSlashBladeItem blade) {
            if (NamedBackplate.MODULE.equals(module.getKey())) NamedBackplate.clearSource(stack);
            // The schematic reads durability again before the workbench assembles the result.
            IModularItem.updateIdentifier(stack);
            blade.migrateLegacyModules(stack);
        }
        return removed;
    }
}
