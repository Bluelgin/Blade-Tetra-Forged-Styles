package dev.bladetetra.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/** Display-only continuation pages, using the vanilla reader's width and 14-line limit. */
public final class SmithingBookPagination {
    private static final int WIDTH = 114;
    private static final int LINES_PER_PAGE = 14;

    public static List<FormattedText> paginate(List<FormattedText> pages, StringSplitter splitter) {
        List<FormattedText> result = new ArrayList<>();
        for (FormattedText page : pages) {
            List<FormattedText> lines = splitter.splitLines(page, WIDTH, Style.EMPTY);
            if (lines.isEmpty()) {
                result.add(FormattedText.EMPTY);
            }
            for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
                List<FormattedText> content = new ArrayList<>();
                int end = Math.min(start + LINES_PER_PAGE, lines.size());
                for (int i = start; i < end; i++) {
                    if (i > start) content.add(FormattedText.of("\n"));
                    content.add(lines.get(i));
                }
                result.add(FormattedText.composite(content));
            }
        }
        return List.copyOf(result);
    }

    private SmithingBookPagination() {}
}
