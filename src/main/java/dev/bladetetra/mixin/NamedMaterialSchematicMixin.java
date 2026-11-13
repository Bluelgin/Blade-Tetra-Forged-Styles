package dev.bladetetra.mixin;

import dev.bladetetra.forging.NamedBladeMaterial;
import dev.bladetetra.forging.NamedBackplate;
import dev.bladetetra.forging.NamedMaterialStats;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Mixin(value = ConfigSchematic.class, remap = false)
public abstract class NamedMaterialSchematicMixin {
    @Unique private static final ToolAction BLADETETRA_HAMMER = ToolAction.get("hammer_dig");

    @Shadow
    private Optional<OutcomeDefinition> getOutcomeFromMaterial(ItemStack material, int index) {
        throw new AssertionError();
    }

    @Inject(method = "getRequiredToolLevels", at = @At("RETURN"), cancellable = true)
    private void bladeTetra$namedToolLevels(ItemStack target, ItemStack[] materials,
            CallbackInfoReturnable<Map<ToolAction, Integer>> cir) {
        var original = cir.getReturnValue();
        int current = original.getOrDefault(BLADETETRA_HAMMER, 0);
        int required = bladeTetra$hammerLevel(materials, current);
        if (required > current) {
            var levels = new HashMap<>(original);
            levels.put(BLADETETRA_HAMMER, required);
            cir.setReturnValue(levels);
        }
    }

    @Inject(method = "getRequiredToolLevel", at = @At("RETURN"), cancellable = true)
    private void bladeTetra$namedToolLevel(ItemStack target, ItemStack[] materials, ToolAction tool,
            CallbackInfoReturnable<Integer> cir) {
        if (BLADETETRA_HAMMER.equals(tool)) {
            int current = cir.getReturnValue();
            int required = bladeTetra$hammerLevel(materials, current);
            if (required > current) cir.setReturnValue(required);
        }
    }

    @Unique
    private int bladeTetra$hammerLevel(ItemStack[] materials, int current) {
        int required = current;
        for (int i = 0; i < materials.length && required < 4; i++) {
            var source = NamedBladeIngotItem.source(materials[i]);
            if (source == null) continue;
            var outcome = getOutcomeFromMaterial(materials[i], i);
            if (outcome.isPresent() && NamedBladeMaterial.namedVariant(outcome.get().moduleVariant)) {
                required = Math.max(required, NamedMaterialStats.requiredHammerLevel(source.baseAttack()));
            }
        }
        return required;
    }

    @Inject(method = "applyOutcome", at = @At("TAIL"))
    private void bladeTetra$replaceIdentity(OutcomeDefinition outcome, ItemStack upgraded,
            boolean consume, String slot, Player player, CallbackInfo ci) {
        if (outcome.moduleKey != null) NamedBladeMaterial.clearReplacedSource(upgraded, slot);
        if (ModularSlashBladeItem.BACKPLATE_SLOT.equals(slot)) NamedBackplate.clearSource(upgraded);
    }

    @Inject(method = "acceptsMaterial", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$validateSource(ItemStack target, String slot, int index,
            ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (ModularSlashBladeItem.BACKPLATE_SLOT.equals(slot)) {
            if (NamedBladeIngotItem.source(ingredient) == null) cir.setReturnValue(false);
        } else if (ingredient.getItem() instanceof NamedBladeIngotItem
                && (NamedBladeIngotItem.source(ingredient) == null
                    || !NamedBladeMaterial.supportedSlot(slot))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "applyUpgrade", at = @At("HEAD"), cancellable = true)
    private void bladeTetra$validateBackplateCraft(ItemStack target, ItemStack[] materials,
            boolean consume, String slot, Player player, CallbackInfoReturnable<ItemStack> cir) {
        // Tetra supplies all workbench material slots even for a one-slot schematic.
        if (ModularSlashBladeItem.BACKPLATE_SLOT.equals(slot)
                && (!(target.getItem() instanceof ModularSlashBladeItem) || materials.length == 0
                    || materials[0].isEmpty() || NamedBladeIngotItem.source(materials[0]) == null)) {
            cir.setReturnValue(target.copy());
        }
    }

    @Inject(method = "applyUpgrade", at = @At("RETURN"))
    private void bladeTetra$captureSource(ItemStack target, ItemStack[] materials,
            boolean consume, String slot, Player player, CallbackInfoReturnable<ItemStack> cir) {
        // applyUpgrade is also the real workbench preview path, before crafting effects.
        NamedBladeMaterial.capture(cir.getReturnValue(), slot, materials);
        if (ModularSlashBladeItem.BACKPLATE_SLOT.equals(slot) && materials.length > 0) {
            // The final consumed ingot still has its tag, but empty stacks report AIR.
            NamedBackplate.installSource(cir.getReturnValue(), NamedBladeIngotItem.storedSourceId(materials[0]));
        }
        dev.bladetetra.combat.ProgrammaticFusionAttunement.preserveUnchangedPair(target, cir.getReturnValue());
    }
}
