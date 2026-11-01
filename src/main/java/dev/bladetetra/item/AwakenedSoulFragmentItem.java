package dev.bladetetra.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** A separate, non-consumable tooltip guide for each awakened soul. */
public final class AwakenedSoulFragmentItem extends Item {
    private final String soul;

    public AwakenedSoulFragmentItem(String soul) {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
        this.soul = soul;
    }

    public String soul() { return soul; }
}
