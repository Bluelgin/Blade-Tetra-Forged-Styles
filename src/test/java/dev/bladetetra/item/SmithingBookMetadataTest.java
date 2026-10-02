package dev.bladetetra.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmithingBookMetadataTest {
    @Test
    void upgradesOldAuthorWithoutChangingPagesOrProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putString("author", "佚名刀匠");
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("existing page"));
        tag.put("pages", pages);
        tag.putInt("BladeTetraCompletedClues", 23);
        tag.putInt("BladeTetraKnownClues", 5);
        tag.putBoolean("BladeTetraNbtSage", true);
        SmithingBookMetadata.normalize(tag, "Smithing Journal");
        assertEquals("Unknown bladesmith", tag.getString("author"));
        assertSame(pages, tag.get("pages"));
        assertEquals(23, tag.getInt("BladeTetraCompletedClues"));
        assertEquals(5, tag.getInt("BladeTetraKnownClues"));
        assertTrue(tag.getBoolean("BladeTetraNbtSage"));
        CompoundTag normalized = tag.copy();
        SmithingBookMetadata.normalize(tag, "Smithing Journal");
        assertEquals(normalized, tag);
    }
}
