package dev.bladetetra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only bridge for opening a custom item with the vanilla book reader. */
@OnlyIn(Dist.CLIENT)
public final class SmithingJournalClient {
    public static void open(ItemStack stack) {
        Minecraft.getInstance().setScreen(new BookViewScreen(
                new BookViewScreen.WrittenBookAccess(stack)));
    }

    private SmithingJournalClient() {
    }
}
