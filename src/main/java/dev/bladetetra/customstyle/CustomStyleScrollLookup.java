package dev.bladetetra.customstyle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollTile;

/**
 * Intricate Tetra scrolls unlock ONLY when directly above the workbench.
 * Read the actual placed ScrollTile, not the recorder's inventory NBT.
 */
public final class CustomStyleScrollLookup {
    public static CustomStyleSpec unique(Level level, BlockPos workbench) {
        if (level == null || workbench == null
                || !(level.getBlockEntity(workbench.above()) instanceof ScrollTile tile)
                || !tile.isIntricate()) {
            return null;
        }
        CustomStyleSpec found = null;
        for (ScrollData data : tile.getScrolls()) {
            if (!"blade_tetra.custom_style".equals(data.key)) continue;
            // Refuse ambiguous or invalid scrolls, even during client preview.
            CustomStyleSpec candidate = CustomStyleScrolls.read(data);
            if (!ComboCompatibilityResolver.playable(candidate) || found != null) return null;
            found = candidate;
        }
        return found;
    }

    private CustomStyleScrollLookup() {}
}
