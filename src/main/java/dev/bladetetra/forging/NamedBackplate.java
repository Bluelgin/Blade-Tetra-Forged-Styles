package dev.bladetetra.forging;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.NamedBladeIngotItem;
import net.minecraft.world.item.ItemStack;

public final class NamedBackplate {
    public static final String MODULE = "slashblade/named_backplate";
    private static final String SOURCE = "blade_tetra_backplate_source";

    public static String sourceId(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || !MODULE.equals(tag.getString(ModularSlashBladeItem.BACKPLATE_SLOT))) return null;
        String id = tag.getString(SOURCE);
        return id.isBlank() || id.length() > 256 ? null : id;
    }

    public static void installSource(ItemStack blade, String source) {
        if (source != null && blade.getItem() instanceof ModularSlashBladeItem
                && MODULE.equals(blade.getOrCreateTag().getString(ModularSlashBladeItem.BACKPLATE_SLOT))) {
            blade.getOrCreateTag().putString(SOURCE, source);
        }
    }

    public static void clearSource(ItemStack blade) {
        if (blade.getTag() != null) blade.getTag().remove(SOURCE);
    }

    public static LegacyImprintKind source(ItemStack stack) {
        return NamedBladeIngotItem.sourceFromId(sourceId(stack));
    }

    private NamedBackplate() {}
}
