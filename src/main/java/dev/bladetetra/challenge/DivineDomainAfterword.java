package dev.bladetetra.challenge;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** First-clear keepsake: closes this chapter without gating its repeatable challenge. */
final class DivineDomainAfterword {
    private static final String RECEIVED = "blade_tetra_divine_afterword_received";
    static void grant(ServerPlayer player) {
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(RECEIVED)) return;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        var tag = book.getOrCreateTag();
        tag.putString("title", "Beyond the Domain");
        tag.putString("author", "_Cazs_");
        tag.putBoolean("resolved", true);
        ListTag pages = new ListTag();
        for (int i = 1; i <= 2; i++) pages.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.translatable("lore.blade_tetra.divine.afterword.page." + i))));
        tag.put("pages", pages);
        book.setHoverName(Component.translatable("lore.blade_tetra.divine.afterword.title"));
        if (!player.getInventory().add(book)) player.drop(book, false);
        persisted.putBoolean(RECEIVED, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        player.sendSystemMessage(Component.translatable("message.blade_tetra.divine.afterword"));
    }
    private DivineDomainAfterword() {}
}
