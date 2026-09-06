package dev.bladetetra.compat;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.item.SmithingClueItem;
import dev.bladetetra.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;

/** Optional JEI integration for NBT-backed Blade Tetra item variants. */
@JeiPlugin
public final class BladeTetraJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            BladeTetra.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(
                ModItems.SMITHING_CLUE.get(),
                (stack, context) -> SmithingClueItem.clue(stack));
    }
}
