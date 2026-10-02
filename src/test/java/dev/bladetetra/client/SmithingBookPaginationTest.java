package dev.bladetetra.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class SmithingBookPaginationTest {
    private final StringSplitter splitter = new StringSplitter((codePoint, style) ->
            codePoint > 255 ? 10F : 6F);

    @Test
    void allBilingualBookTextsFitContinuationPages() throws Exception {
        for (String language : List.of("zh_cn", "en_us")) {
            var path = java.nio.file.Path.of("src/main/resources/assets/blade_tetra/lang/"
                    + language + ".json");
            var entries = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(path)).getAsJsonObject();
            for (var entry : entries.entrySet()) {
                if (!entry.getKey().startsWith("lore.blade_tetra.")) continue;
                FormattedText source = FormattedText.of(entry.getValue().getAsString());
                var pages = SmithingBookPagination.paginate(List.of(source), splitter);
                List<String> actual = new ArrayList<>();
                for (var page : pages) {
                    var lines = splitter.splitLines(page, 114, Style.EMPTY);
                    assertTrue(lines.size() <= 14, language + ": " + entry.getKey());
                    lines.forEach(line -> actual.add(line.getString()));
                }
                var expected = splitter.splitLines(source, 114, Style.EMPTY)
                        .stream().map(FormattedText::getString).toList();
                assertEquals(expected, actual, language + ": " + entry.getKey());
            }
        }
    }

    @Test
    void splitsLongEnglishAndChineseWithoutLosingLines() {
        for (String text : List.of("The travelling bladesmith records this journey. ".repeat(40),
                "行旅刀匠留下的锻刀手记，铁火可塑其形，经历方铸其名。".repeat(40))) {
            FormattedText source = FormattedText.of(text);
            var expected = splitter.splitLines(source, 114, Style.EMPTY);
            var pages = SmithingBookPagination.paginate(List.of(source), splitter);
            assertTrue(pages.size() > 1);
            List<String> actual = new ArrayList<>();
            for (FormattedText page : pages) {
                var lines = splitter.splitLines(page, 114, Style.EMPTY);
                assertTrue(lines.size() <= 14);
                lines.forEach(line -> actual.add(line.getString()));
            }
            assertEquals(expected.stream().map(FormattedText::getString).toList(), actual);
        }
    }

    @Test
    void preservesParagraphsAndLogicalPageBoundaries() {
        var pages = SmithingBookPagination.paginate(List.of(
                FormattedText.of("first\n\nparagraph"), FormattedText.EMPTY,
                FormattedText.of("last")), splitter);
        assertEquals(3, pages.size());
        assertEquals("first\n\nparagraph", pages.get(0).getString());
        assertEquals("", pages.get(1).getString());
        assertEquals("last", pages.get(2).getString());
    }

    @Test
    void preservesStyledText() {
        Component source = Component.literal("gold heading")
                .setStyle(Style.EMPTY.withBold(true).withColor(0xffcc00));
        var pages = SmithingBookPagination.paginate(List.of(source), splitter);
        pages.get(0).visit((style, text) -> {
            if (!text.isEmpty()) {
                assertTrue(style.isBold());
                assertEquals(0xffcc00, style.getColor().getValue());
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }
}
