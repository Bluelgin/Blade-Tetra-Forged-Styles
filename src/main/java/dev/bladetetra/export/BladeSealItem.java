package dev.bladetetra.export;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/** Creative tool. Export approval always comes from the logical server. */
public final class BladeSealItem extends Item {
    public BladeSealItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            return BladeSealSessions.open(serverPlayer, hand)
                    ? InteractionResultHolder.consume(tool) : InteractionResultHolder.fail(tool);
        }
        return InteractionResultHolder.success(tool);
    }
}
