package dev.bladetetra.challenge;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Native iron katana; no Tetra material effects or awakened player abilities on the boss. */
final class MikageEquipment {
    private static final ResourceLocation IRON = new ResourceLocation("slashblade", "rodai_iron");
    static ItemStack ironKatana(Level level) {
        var definition = level.registryAccess().registry(SlashBladeDefinition.REGISTRY_KEY)
                .map(registry -> registry.get(IRON)).orElse(null);
        ItemStack blade = definition != null ? definition.getBlade() : SlashBladeItems.SLASHBLADE.get().getDefaultInstance();
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            if (definition == null) state.setTexture(new ResourceLocation("slashblade", "model/rodai_iron.png"));
            state.setColorCode(0xD51F3F);
            state.setEffectColorInverse(false);
            state.setTargetEntityId(-1);
        });
        return blade;
    }
    private MikageEquipment() { }
}
