package dev.bladetetra.compat;

import dev.bladetetra.forging.NamedLegacyImprintStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.craftingeffect.condition.CraftingEffectCondition;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

import java.util.Map;

/** Limits the generic imprint identity effect to Blade Tetra's two fitting slots. */
public final class LegacyImprintCraftingCondition implements CraftingEffectCondition {
    @Override
    public boolean test(ResourceLocation[] unlocks, ItemStack upgradedStack, String slot,
            boolean isReplacing, Player player, ItemStack[] materials,
            Map<ToolAction, Integer> tools, UpgradeSchematic schematic,
            Level world, BlockPos pos, BlockState blockState) {
        return NamedLegacyImprintStorage.partForSlot(slot) != null;
    }
}
