package dev.bladetetra.compat;

import dev.bladetetra.forging.NamedLegacyImprintStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import se.mickelus.tetra.craftingeffect.condition.CraftingEffectCondition;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

import java.util.Map;

/** Limits source ownership updates to the three named-ingot-compatible module slots. */
public final class LegacyImprintCraftingCondition implements CraftingEffectCondition {
    @Override
    public boolean test(ResourceLocation[] unlocks, ItemStack upgradedStack, String slot,
            boolean isReplacing, Player player, ItemStack[] materials,
            Map<ToolAction, Integer> tools, UpgradeSchematic schematic,
            Level world, BlockPos pos, BlockState blockState) {
        String part = NamedLegacyImprintStorage.partForSlot(slot);
        if (part == null) return false;
        // Saya and the existing composite hilt retain their historical ownership hook.
        if (!"blade".equals(part)) return true;
        // Never install an additional identity effect on unrelated blade crafting
        // (notably Custom Style), which already owns its own effect.
        if (upgradedStack == null || upgradedStack.isEmpty()) return false;
        CompoundTag tag = upgradedStack.getTag();
        if (tag == null) return false;
        String module = tag.getString(slot);
        boolean imprinted = !module.isEmpty() && NamedLegacyImprintStorage.genericVariant(part)
                .equals(tag.getString(module + "_material"));
        boolean oldSourceNeedsCleanup = tag.contains(NamedLegacyImprintStorage.ROOT, Tag.TAG_COMPOUND)
                && tag.getCompound(NamedLegacyImprintStorage.ROOT)
                        .contains(part, Tag.TAG_STRING);
        // Also run when the blade imprint was replaced by an ordinary blade
        // so stale source ownership is removed.
        return imprinted || oldSourceNeedsCleanup
                || dev.bladetetra.forging.NamedBladeMaterial.namedVariant(tag.getString(module + "_material"));
    }
}
