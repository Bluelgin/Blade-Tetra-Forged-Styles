package dev.bladetetra.forging;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.ItemModuleMajor;

/** Stable keys and Tetra 6.10-compatible accessors for ordinary forging work. */
public final class ForgingImprovements {
    public static final String KOBUSE = "blade_tetra/construction/kobuse";
    public static final String SANMAI = "blade_tetra/construction/sanmai";
    public static final String SHIHOZUME = "blade_tetra/construction/shihozume";
    public static final String NORMALIZED_CONSTRUCTION =
            "blade_tetra/construction/normalized";

    public static final String HAMAGURI = "blade_tetra/edge/hamaguri";
    public static final String HIRA = "blade_tetra/edge/hira";
    public static final String USUBA = "blade_tetra/edge/usuba";
    public static final String NORMALIZED_EDGE = "blade_tetra/edge/normalized";

    public static final String NAKAGO_FIT = "blade_tetra/assembly/nakago_fit";
    public static final String RIGID_ASSEMBLY = "blade_tetra/assembly/rigid";
    public static final String NORMALIZED_ASSEMBLY =
            "blade_tetra/assembly/normalized";

    public static boolean has(ItemStack stack, String slot, String improvement) {
        return level(stack, slot, improvement) >= 0;
    }

    public static int level(ItemStack stack, String slot, String improvement) {
        if (!(stack.getItem() instanceof IModularItem modularItem)) {
            return -1;
        }
        ItemModule module = modularItem.getModuleFromSlot(stack, slot);
        if (module instanceof ItemModuleMajor majorModule) {
            return majorModule.getImprovementLevel(stack, improvement);
        }
        return -1;
    }

    public static boolean apply(ItemStack stack, String slot, String improvement) {
        if (!(stack.getItem() instanceof IModularItem modularItem)) {
            return false;
        }
        ItemModule module = modularItem.getModuleFromSlot(stack, slot);
        if (!(module instanceof ItemModuleMajor majorModule)
                || !majorModule.acceptsImprovementLevel(improvement, 1)) {
            return false;
        }
        majorModule.addImprovement(stack, improvement, 1);
        IModularItem.updateIdentifier(stack);
        if (stack.getItem() instanceof ModularSlashBladeItem bladeItem) {
            bladeItem.syncDerivedBladeState(stack);
        }
        return has(stack, slot, improvement);
    }

    private ForgingImprovements() {
    }
}
