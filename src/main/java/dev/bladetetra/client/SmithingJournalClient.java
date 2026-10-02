package dev.bladetetra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.FormattedText;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only bridge for opening a custom item with the vanilla book reader. */
@OnlyIn(Dist.CLIENT)
public final class SmithingJournalClient {
    public static void open(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        BookViewScreen.BookAccess source = new BookViewScreen.WrittenBookAccess(stack);
        List<FormattedText> logicalPages = new ArrayList<>();
        for (int i = 0; i < source.getPageCount(); i++) {
            logicalPages.add(source.getPage(i));
        }
        List<FormattedText> pages = SmithingBookPagination.paginate(
                logicalPages, minecraft.font.getSplitter());
        minecraft.setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() { return pages.size(); }

            @Override
            public FormattedText getPageRaw(int index) { return pages.get(index); }
        }));
    }

    private SmithingJournalClient() {
    }
}
