package dev.bladetetra.challenge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EasterRoomBooksTest {
    private static CompoundTag original(EasterRoomBooks.Book book) {
        CompoundTag tag = new CompoundTag();
        tag.putString("title", book.title());
        tag.putString("author", "_Cazs_");
        ListTag pages = new ListTag();
        book.originalPages().forEach(text -> pages.add(StringTag.valueOf(
                Component.Serializer.toJson(Component.literal(text)))));
        tag.put("pages", pages);
        return tag;
    }

    @Test
    void allSixBooksBecomeTranslatableAndUpgradeOnlyOnce() {
        assertEquals(6, EasterRoomBooks.BOOKS.size());
        for (var book : EasterRoomBooks.BOOKS) {
            CompoundTag tag = original(book);
            assertTrue(EasterRoomBooks.localize(tag));
            assertEquals(book.originalPages().size(), tag.getList("pages", 8).size());
            assertTrue(tag.getList("pages", 8).getString(0).contains(
                    "lore.blade_tetra.easter_room." + book.id() + ".page.1"));
            assertTrue(tag.getCompound("display").getString("Name").contains("translate"));
            assertEquals("_Cazs_", tag.getString("author"));
            CompoundTag localized = tag.copy();
            assertFalse(EasterRoomBooks.localize(tag));
            assertEquals(localized, tag);
        }
    }

    @Test
    void editedBooksAndCustomNamesAreNotOverwritten() {
        CompoundTag tag = original(EasterRoomBooks.BOOKS.get(0));
        tag.getList("pages", 8).set(0, StringTag.valueOf("{\"text\":\"my own writing\"}"));
        CompoundTag before = tag.copy();
        assertFalse(EasterRoomBooks.localize(tag));
        assertEquals(before, tag);
        tag = original(EasterRoomBooks.BOOKS.get(0));
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"renamed\"}");
        tag.put("display", display);
        before = tag.copy();
        assertFalse(EasterRoomBooks.localize(tag));
        assertEquals(before, tag);
    }

    @Test
    void recognizesActualBundledRoomBooks() throws Exception {
        var path = java.nio.file.Path.of(
                "src/main/resources/data/blade_tetra/challenge/easter_room.bta.gz");
        try (var input = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(
                java.nio.file.Files.newInputStream(path)))) {
            assertEquals(0x42544133, input.readInt());
            int paletteSize = input.readUnsignedShort();
            int blocks = input.readInt();
            for (int i = 0; i < paletteSize; i++) input.skipNBytes(input.readUnsignedShort());
            input.skipNBytes((long) blocks * 8);
            int blockEntities = input.readInt();
            int localized = 0;
            for (int i = 0; i < blockEntities; i++) {
                input.skipNBytes(6);
                String snbt = new String(input.readNBytes(input.readInt()),
                        java.nio.charset.StandardCharsets.UTF_8);
                var tag = net.minecraft.nbt.TagParser.parseTag(snbt);
                if (tag.getCompound("Book").getString("id").equals("minecraft:written_book")
                        && EasterRoomBooks.localize(tag.getCompound("Book").getCompound("tag"))) {
                    localized++;
                }
            }
            assertEquals(6, localized);
        }
    }

    @Test
    void bothLanguagesContainEveryTitleAndPage() throws Exception {
        for (String language : java.util.List.of("zh_cn", "en_us")) {
            var entries = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/assets/blade_tetra/lang/"
                            + language + ".json"))).getAsJsonObject();
            for (var book : EasterRoomBooks.BOOKS) {
                String prefix = "lore.blade_tetra.easter_room." + book.id();
                assertTrue(entries.has(prefix + ".title"));
                for (int i = 1; i <= book.originalPages().size(); i++) {
                    assertTrue(entries.has(prefix + ".page." + i));
                }
            }
        }
    }
}
