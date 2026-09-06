package dev.bladetetra.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BoundaryGateCharmItem extends Item {
    public BoundaryGateCharmItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 48;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ChallengeManager.GateMode mode = BrokenOniMaskItem.enablesVisitor(player)
                    ? ChallengeManager.GateMode.VISITOR
                    : BrokenOniMaskItem.enablesReminiscence(player)
                            ? ChallengeManager.GateMode.REMINISCENCE
                            : ChallengeManager.GateMode.NORMAL;
            ChallengeManager.openGate(player, mode);
            player.getCooldowns().addCooldown(this, 100);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.boundary_gate_charm")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.boundary_gate_charm.echo")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
