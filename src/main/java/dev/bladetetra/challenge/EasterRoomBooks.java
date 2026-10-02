package dev.bladetetra.challenge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Localizes only unchanged bundled books, never an edited or substituted player book. */
final class EasterRoomBooks {
    record Book(String id, String title, List<String> originalPages) {}
    static final List<Book> BOOKS = List.of(
            new Book("thanks", "Thanks", List.of("Echoes from the Community\n\nveleges\nSuggested an orthodox blade construction designed for traditional, combo-focused combat.", "Aasynn\nInspired the addition of tsubaless blade customization.", "user_3bri5rzwusjsqfi7\nTheir feedback helped make the hidden legacy guide clearer and more complete.", "Some ideas begin with the smith.\nOthers arrive as echoes from those who wield the blade.")),
            new Book("why", "Why This Exists", List.of("This mod began with one unreasonable question:\n\n“What if a SlashBlade could truly be forged through Tetra?”\n\n", "The answer involved many models, even more bugs, and far more work than expected.\n\nStill, we made it.")),
            new Book("reserved", "Reserved", List.of("Something was supposed to be placed here.\n\nWe have not decided what it is yet.\n\nUntil then, please pretend this is a deeply meaningful secret.")),
            new Book("potato", "Potato_Blade", List.of("It started as a joke.\n\nThen it got a recipe, a model, and somehow, its own story.\n\nNow nobody dares to remove it.")),
            new Book("visitor", "To the visitor", List.of("You overcame the final challenge, found a door that did not need to exist, and followed it all the way here.\n\nThere is no stronger enemy and no hidden endgame weapon.", "There is only one thing we wanted to say:\n\nThank you for playing.")),
            new Book("seat", "The Empty Seat", List.of("Akatsuki was my last student.\n\nShe learned quickly, and always left too quickly.\n\nThis seat was kept for her.\n\nI doubt she will return to use it, but I never put it away.")));

    static boolean localize(CompoundTag tag) {
        if (tag.getCompound("display").contains("Name")) return false;
        for (Book book : BOOKS) {
            if (!book.title().equals(tag.getString("title"))
                    || !"_Cazs_".equals(tag.getString("author"))) continue;
            ListTag oldPages = tag.getList("pages", 8);
            if (oldPages.size() != book.originalPages().size()) continue;
            boolean matches = true;
            for (int i = 0; i < oldPages.size(); i++) {
                try {
                    Component text = Component.Serializer.fromJson(oldPages.getString(i));
                    if (text == null || !book.originalPages().get(i).equals(text.getString())) {
                        matches = false;
                        break;
                    }
                } catch (RuntimeException invalidPage) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            ListTag pages = new ListTag();
            String prefix = "lore.blade_tetra.easter_room." + book.id();
            for (int i = 0; i < oldPages.size(); i++) {
                pages.add(StringTag.valueOf(Component.Serializer.toJson(
                        Component.translatable(prefix + ".page." + (i + 1)))));
            }
            tag.put("pages", pages);
            // Filtered pages must not keep overriding the translated originals.
            tag.remove("filtered_pages");
            tag.remove("filtered_title");
            CompoundTag display = tag.getCompound("display");
            display.putString("Name", Component.Serializer.toJson(
                    Component.translatable(prefix + ".title")));
            tag.put("display", display);
            tag.putBoolean("resolved", true);
            return true;
        }
        return false;
    }

    private EasterRoomBooks() {}
}
