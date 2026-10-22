package dev.bladetetra.customstyle;

import dev.bladetetra.item.ModularSlashBladeItem;
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

/** Never apply custom style NBT while crafting unrelated sword modules. */
public final class CustomStyleCraftingCondition implements CraftingEffectCondition {
    @Override
    public boolean test(ResourceLocation[] unlocks, ItemStack upgradedStack,
            String slot, boolean isReplacing, Player player, ItemStack[] materials,
            Map<ToolAction, Integer> tools, UpgradeSchematic schematic,
            Level world, BlockPos pos, BlockState blockState) {
        return ModularSlashBladeItem.BLADE_SLOT.equals(slot)
                && schematic != null
                && schematic.getKey().startsWith("slashblade/custom_style_")
                && CustomStyleScrollLookup.unique(world, pos) != null;
    }
}
