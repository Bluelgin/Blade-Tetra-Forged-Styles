package dev.bladetetra.item;

import dev.bladetetra.lore.SmithingLore;
import dev.bladetetra.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.List;

public final class SmithingClueItem extends Item {
    private static final String CLUE_TAG = "SmithingClue";

    public SmithingClueItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static ItemStack create(String clue) {
        ItemStack stack = new ItemStack(ModItems.SMITHING_CLUE.get());
        stack.getOrCreateTag().putString(CLUE_TAG, clue);
        stack.getOrCreateTag().putInt("CustomModelData", modelIndex(clue));
        return stack;
    }

    public static String clue(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        String value = tag == null ? "" : tag.getString(CLUE_TAG);
        return SmithingLore.isValid(value) ? value : SmithingLore.SHOSHIN;
    }

    private static int modelIndex(String clue) {
        int index = SmithingLore.CLUES.indexOf(clue);
        return index < 0 ? 1 : index + 1;
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        int expected = modelIndex(clue(stack));
        if (stack.getOrCreateTag().getInt("CustomModelData") != expected) {
            stack.getOrCreateTag().putInt("CustomModelData", expected);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.blade_tetra.smithing_clue." + clue(stack));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blade_tetra.smithing_clue")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.blade_tetra.smithing_clue.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        String clue = clue(stack);
        if (!SmithingLore.discover(player, clue)) {
            player.displayClientMessage(Component.translatable(
                    "message.blade_tetra.smithing_clue.known"), true);
            return InteractionResultHolder.consume(stack);
        }

        player.displayClientMessage(Component.translatable(
                "message.blade_tetra.smithing_clue.recorded",
                Component.translatable("lore.blade_tetra." + clue + ".title")), false);
        player.playNotifySound(
                SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS,
                0.8F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        if (player instanceof ServerPlayer serverPlayer
                && !containsJournal(serverPlayer)) {
            ItemStack journal = ModItems.SMITHING_JOURNAL.get().getDefaultInstance();
            SmithingJournalItem.refresh(journal, serverPlayer);
            if (!serverPlayer.getInventory().add(journal)) {
                serverPlayer.drop(journal, false);
            }
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.blade_tetra.smithing_journal.received"), false);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static boolean containsJournal(Player player) {
        return player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.SMITHING_JOURNAL.get()));
    }
}
