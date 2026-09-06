package dev.bladetetra.challenge;

import dev.bladetetra.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;

import java.util.List;
import java.util.Optional;

final class ChallengeRewards {
    static ItemStack boundaryScroll() {
        ItemStack stack = new ItemStack(ScrollItem.instance);
        new ScrollData(
                "blade_tetra.boundary_forging",
                Optional.of("blade_tetra.boundary_forging"),
                true,
                0xE8D7BA,
                0x8E1833,
                List.of(9, 12, 13, 10),
                List.of(new ResourceLocation("tetra", "slashblade/boundary_forging")),
                List.of()).write(stack);
        return stack;
    }

    static ItemStack remnant() {
        return new ItemStack(ModItems.SWORD_GHOST_REMNANT.get());
    }

    static ItemStack mask() {
        return new ItemStack(ModItems.BROKEN_ONI_MASK.get());
    }

    private ChallengeRewards() {
    }
}
