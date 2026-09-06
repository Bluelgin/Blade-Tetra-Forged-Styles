package dev.bladetetra.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BrokenOniMaskItem extends Item implements Equipable {
    public BrokenOniMaskItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    static boolean enablesReminiscence(Player player) {
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        return player.isShiftKeyDown() && mask.getItem() instanceof BrokenOniMaskItem
                && !mask.getOrCreateTag().getBoolean("blade_tetra_mikage_echo");
    }

    static boolean enablesVisitor(Player player) {
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        return player.isShiftKeyDown() && mask.getItem() instanceof BrokenOniMaskItem
                && mask.getOrCreateTag().getBoolean("blade_tetra_mikage_echo");
    }

    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return armorType == EquipmentSlot.HEAD;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrCreateTag().getBoolean("blade_tetra_mikage_echo");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.mask.wearable")
                .withStyle(ChatFormatting.GRAY));
        if (stack.getOrCreateTag().getBoolean("blade_tetra_mikage_echo")) {
            tooltip.add(Component.translatable("tooltip.blade_tetra.mask.echo")
                    .withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Component.translatable("tooltip.blade_tetra.mask.visitor")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.blade_tetra.mask.reminiscence")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }
}
