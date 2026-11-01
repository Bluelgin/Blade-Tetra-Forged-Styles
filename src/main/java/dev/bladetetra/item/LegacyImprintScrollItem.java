package dev.bladetetra.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Blank consumable used to study a named SlashBlade on an adjacent blade stand. */
public final class LegacyImprintScrollItem extends Item {
    public LegacyImprintScrollItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.legacy_imprint_scroll.retired")
                .withStyle(ChatFormatting.GRAY));
    }
}
