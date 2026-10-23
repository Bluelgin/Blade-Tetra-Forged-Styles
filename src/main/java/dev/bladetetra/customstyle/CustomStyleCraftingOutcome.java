package dev.bladetetra.customstyle;

import dev.bladetetra.item.ModularSlashBladeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.craftingeffect.outcome.CraftingEffectOutcome;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

import java.util.Map;

/**
 * Runs after Tetra installs the chosen custom blade module. Snapshots the
 * unfolded scroll into the result; the scroll itself is never consumed.
 * Keep both Tetra 6.10 and 6.12+ ABI entry points.
 */
public final class CustomStyleCraftingOutcome implements CraftingEffectOutcome {
    public boolean apply(ResourceLocation[] unlockedEffects, ItemStack upgradedStack,
            String slot, boolean isReplacing, Player player, ItemStack[] preMaterials,
            Map<ToolAction, Integer> tools, Level world, UpgradeSchematic schematic,
            BlockPos pos, BlockState blockState, boolean consumeResources,
            ItemStack[] postMaterials) {
        return install(upgradedStack, slot, world, pos, schematic);
    }

    public boolean apply(ResourceLocation[] unlockedEffects, ItemStack upgradedStack,
            String slot, boolean isReplacing, Player player, ItemStack[] preMaterials,
            Map<ToolAction, Integer> tools, Level world, UpgradeSchematic schematic,
            BlockPos pos, BlockState blockState, boolean consumeResources,
            ItemStack[] postMaterials, float severity) {
        return install(upgradedStack, slot, world, pos, schematic);
    }

    private static boolean install(ItemStack stack, String slot, Level world,
            BlockPos pos, UpgradeSchematic schematic) {
        if (!(stack.getItem() instanceof ModularSlashBladeItem)
                || !ModularSlashBladeItem.BLADE_SLOT.equals(slot)
                || schematic == null || !schematic.getKey().startsWith("slashblade/custom_style_")) {
            return false;
        }
        CustomStyleSpec spec = CustomStyleScrollLookup.unique(world, pos);
        if (spec == null) return false;
        stack.getOrCreateTag().put(CustomStyleSpec.STACK_KEY, spec.encode());
        return true;
    }
}
