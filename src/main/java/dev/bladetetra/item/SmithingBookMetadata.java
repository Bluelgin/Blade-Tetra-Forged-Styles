package dev.bladetetra.item;

import net.minecraft.nbt.CompoundTag;

/** Language-neutral written-book metadata; visible author text is localized by the item. */
final class SmithingBookMetadata {
    static final String AUTHOR_TOOLTIP = "tooltip.blade_tetra.smithing_book.author";

    static void normalize(CompoundTag tag, String title) {
        tag.putString("title", title);
        tag.putString("author", "Unknown bladesmith");
    }

    private SmithingBookMetadata() {}
}
