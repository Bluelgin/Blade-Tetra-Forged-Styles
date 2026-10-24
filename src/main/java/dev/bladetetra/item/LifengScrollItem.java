package dev.bladetetra.item;

import dev.bladetetra.aura.SwordAuraMastery;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Consumed to learn a player passive; intentionally not a Tetra scroll. */
public final class LifengScrollItem extends Item {
    public LifengScrollItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack scroll = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(scroll);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(scroll);
        if (SwordAuraMastery.knows(serverPlayer)) {
            player.displayClientMessage(Component.translatable("message.blade_tetra.lifeng.already"), true);
            return InteractionResultHolder.fail(scroll);
        }
        if (SwordAuraMastery.learn(serverPlayer)) {
            if (!player.getAbilities().instabuild) scroll.shrink(1);
            player.displayClientMessage(Component.translatable("message.blade_tetra.lifeng.learned"), false);
            return InteractionResultHolder.success(scroll);
        }
        return InteractionResultHolder.fail(scroll);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.lifeng_scroll")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.lifeng_scroll.verse")
                .withStyle(ChatFormatting.GRAY));
    }
}
