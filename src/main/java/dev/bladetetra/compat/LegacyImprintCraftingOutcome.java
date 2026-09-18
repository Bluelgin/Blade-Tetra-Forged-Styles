package dev.bladetetra.compat;

import dev.bladetetra.forging.NamedLegacyImprintStorage;
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

/** Writes/removes Blade Tetra's soft source id after Tetra modifies a fitting slot. */
public final class LegacyImprintCraftingOutcome implements CraftingEffectOutcome {
    @Override
    public boolean apply(ResourceLocation[] unlockedEffects, ItemStack upgradedStack, String slot,
            boolean isReplacing, Player player, ItemStack[] preMaterials,
            Map<ToolAction, Integer> tools, Level world, UpgradeSchematic schematic,
            BlockPos pos, BlockState blockState, boolean consumeResources,
            ItemStack[] postMaterials, float severity) {
        String key = schematic == null ? null : schematic.getKey();
        return NamedLegacyImprintStorage.applyCraftResult(upgradedStack, slot, key);
    }
}
