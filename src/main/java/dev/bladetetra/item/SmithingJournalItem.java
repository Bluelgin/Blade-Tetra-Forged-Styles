package dev.bladetetra.item;

import dev.bladetetra.client.SmithingJournalClient;
import dev.bladetetra.lore.SmithingLore;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

public final class SmithingJournalItem extends WrittenBookItem {
    public SmithingJournalItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        makeBaseBook(stack);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.blade_tetra.smithing_journal");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () -> () -> SmithingJournalClient.open(stack));
        } else {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        if (!level.isClientSide && entity instanceof Player player) {
            refresh(stack, player);
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        int count = tag == null ? 0 : tag.getInt("BladeTetraKnownClues");
        int completed = tag == null ? 0
                : Integer.bitCount(tag.getInt("BladeTetraCompletedClues"));
        boolean nbtSage = tag != null && tag.getBoolean("BladeTetraNbtSage");
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.smithing_journal", count, SmithingLore.CLUES.size())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                        "tooltip.blade_tetra.smithing_journal.completed",
                        completed, SmithingLore.CLUES.size())
                .withStyle(ChatFormatting.DARK_GREEN));
        if (completed >= 3 || nbtSage) {
            tooltip.add(Component.translatable(nbtSage
                            ? "tooltip.blade_tetra.smithing_journal.nbt_sage.completed"
                            : "tooltip.blade_tetra.smithing_journal.nbt_sage.revealed")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.blade_tetra.smithing_journal.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void refresh(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        int count = SmithingLore.discoveredCount(player);
        int completionMask = player instanceof ServerPlayer serverPlayer
                ? SmithingLore.completionMask(serverPlayer) : 0;
        boolean nbtSage = player instanceof ServerPlayer serverPlayer
                && SmithingLore.nbtSageCompleted(serverPlayer);
        int newlyCompleted = player instanceof ServerPlayer serverPlayer
                ? SmithingLore.consumeCompletionNotices(serverPlayer, completionMask) : 0;
        if (newlyCompleted != 0 && player instanceof ServerPlayer serverPlayer) {
            celebrateCompletions(serverPlayer, newlyCompleted);
        }
        if (tag.getInt("BladeTetraKnownClues") == count
                && tag.getInt("BladeTetraCompletedClues") == completionMask
                && tag.getBoolean("BladeTetraNbtSage") == nbtSage
                && tag.contains("pages", 9)) {
            return;
        }

        ListTag pages = new ListTag();
        pages.add(page(Component.translatable("lore.blade_tetra.journal.cover", count,
                SmithingLore.CLUES.size())));
        for (int i = 0; i < SmithingLore.CLUES.size(); i++) {
            String clue = SmithingLore.CLUES.get(i);
            if (SmithingLore.knows(player, clue)) {
                boolean completed = (completionMask & (1 << i)) != 0;
                pages.add(page(completed
                        ? completedPage(clue)
                        : Component.translatable("lore.blade_tetra." + clue + ".page")));
            }
        }
        if (count == 0) {
            pages.add(page(Component.translatable("lore.blade_tetra.journal.empty")));
        }
        if (nbtSage) {
            pages.add(page(completedPage(SmithingLore.NBT_SAGE)));
        } else if (Integer.bitCount(completionMask) >= 3) {
            pages.add(page(Component.translatable(
                    "lore.blade_tetra.nbt_sage.page")));
        }

        tag.putString("title", "Smithing Journal");
        tag.putString("author", "佚名刀匠");
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        tag.put("pages", pages);
        tag.putInt("BladeTetraKnownClues", count);
        tag.putInt("BladeTetraCompletedClues", completionMask);
        tag.putBoolean("BladeTetraNbtSage", nbtSage);
    }

    private static void makeBaseBook(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", "Smithing Journal");
        tag.putString("author", "佚名刀匠");
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        pages.add(page(Component.translatable("lore.blade_tetra.journal.empty")));
        tag.put("pages", pages);
        tag.putInt("BladeTetraKnownClues", 0);
        tag.putInt("BladeTetraCompletedClues", 0);
        tag.putBoolean("BladeTetraNbtSage", false);
    }

    private static Component completedPage(String clue) {
        return Component.empty()
                .append(Component.translatable("lore.blade_tetra." + clue + ".title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.translatable("lore.blade_tetra.journal.inherited")
                        .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD))
                .append(Component.literal("\n\n"))
                .append(Component.translatable(
                        "lore.blade_tetra." + clue + ".completed"));
    }

    private static void celebrateCompletions(
            ServerPlayer player,
            int newlyCompleted) {
        player.playNotifySound(
                SoundEvents.ANVIL_USE,
                SoundSource.PLAYERS,
                0.65F,
                1.15F);
        ServerLevel level = player.serverLevel();
        level.sendParticles(
                ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.1D, player.getZ(),
                10, 0.35D, 0.45D, 0.35D, 0.025D);
        for (int i = 0; i < SmithingLore.CLUES.size(); i++) {
            if ((newlyCompleted & (1 << i)) == 0) continue;
            player.displayClientMessage(Component.translatable(
                            "message.blade_tetra.smithing_journal.completed",
                            Component.translatable("lore.blade_tetra."
                                    + SmithingLore.CLUES.get(i) + ".title"))
                    .withStyle(ChatFormatting.GOLD), false);
        }
    }

    private static StringTag page(Component component) {
        return StringTag.valueOf(Component.Serializer.toJson(component));
    }
}
