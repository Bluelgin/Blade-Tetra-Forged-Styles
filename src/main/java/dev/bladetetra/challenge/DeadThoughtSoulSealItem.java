package dev.bladetetra.challenge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dedicated reward hook for Dead Thought's second acquisition route. It is not
 * a generic SA/SE voucher and intentionally carries only the Dead Thought path.
 */
public final class DeadThoughtSoulSealItem extends Item {
    public DeadThoughtSoulSealItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("无生残印");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("死念 · 第二获取路线的神域凭证")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.literal("不是通用剑技或特殊效果兑换物。")
                .withStyle(ChatFormatting.GRAY));
    }
}
