package dev.bladetetra.compat.attachments;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.schematic.SchematicDefinition;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Adds only the blade-specific one-coating limit; native requirements still run first. */
final class BladeCoatingPolicy {
    static boolean canApply(ItemStack stack, SchematicDefinition recipe) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem item)) return true;
        if (!(item.getModuleFromSlot(stack, ModularSlashBladeItem.BLADE_SLOT)
                instanceof ItemModuleMajor blade)) return false;
        Set<String> requested = Arrays.stream(recipe.outcomes)
                .filter(outcome -> outcome.improvements != null)
                .flatMap(outcome -> outcome.improvements.keySet().stream())
                .filter(SwordAttachmentSchematics::isCoatingKey).collect(Collectors.toSet());
        return Arrays.stream(blade.getImprovements(stack))
                .map(improvement -> improvement.key)
                .filter(SwordAttachmentSchematics::isCoatingKey)
                .allMatch(key -> requested.contains(key)
                        // Native MMT improvement groups replace the previous material coating.
                        || (key.startsWith(MaterialFullerCoating.IMPROVEMENT_PREFIX)
                        && requested.stream().anyMatch(value -> value.startsWith(MaterialFullerCoating.IMPROVEMENT_PREFIX))));
    }

    private BladeCoatingPolicy() {}
}
