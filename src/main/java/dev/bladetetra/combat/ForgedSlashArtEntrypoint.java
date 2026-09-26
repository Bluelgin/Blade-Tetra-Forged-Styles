package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Public registry-facing entrypoint for forged Slash Art release paths that do
 * not emit PerformSlashArtEvent.
 */
public final class ForgedSlashArtEntrypoint {
    public static ResourceLocation superCombo(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return ComboStateRegistry.NONE.getId();
        }
        ItemStack blade = player.getMainHandItem();
        var state = blade.getCapability(ItemSlashBlade.BLADESTATE).orElse(null);
        if (state == null) {
            return ComboStateRegistry.NONE.getId();
        }
        return ForgedSlashArtHandler.beginCast(
                player, blade, state, SlashArts.ArtsType.Super);
    }

    private ForgedSlashArtEntrypoint() {
    }
}
