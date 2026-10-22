package dev.bladetetra.item;

import dev.bladetetra.customstyle.CustomStyleScrolls;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.customstyle.StyleRecordingSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Right-click after wielding a SlashBlade to capture its last committed move.
 * Sneak-right-click completes the bounded draft and gives a native Tetra scroll.
 */
public final class StyleRecorderItem extends Item {
    public StyleRecorderItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(tool, level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            CustomStyleSpec spec = StyleRecordingSession.finish(serverPlayer);
            if (spec == null) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.blade_tetra.custom_style.empty").withStyle(ChatFormatting.RED), true);
            } else {
                ItemStack scroll = CustomStyleScrolls.create(spec);
                if (!player.getInventory().add(scroll)) player.drop(scroll, false);
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.blade_tetra.custom_style.finished", spec.actions().size())
                        .withStyle(ChatFormatting.GOLD), false);
            }
        } else {
            StyleRecordingSession.CaptureResult result = StyleRecordingSession.capture(serverPlayer);
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.blade_tetra.custom_style." + result.name().toLowerCase(java.util.Locale.ROOT),
                    StyleRecordingSession.count(serverPlayer), CustomStyleSpec.MAX_ACTIONS), true);
        }
        return InteractionResultHolder.sidedSuccess(tool, level.isClientSide);
    }
}
