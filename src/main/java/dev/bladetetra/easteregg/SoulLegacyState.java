package dev.bladetetra.easteregg;

import net.minecraft.world.item.ItemStack;

/**
 * Resolves the one named soul legacy currently hosted by a blade.
 *
 * <p>Unlock flags remain independent and permanent. The installed inscription
 * decides which unlocked legacy is awake, so rebuilding a blade never erases
 * its history and two named souls can never act at the same time.</p>
 */
public final class SoulLegacyState {
    public enum Legacy {
        NONE,
        AKATSUKI,
        KYOUKA,
        SENBONZAKURA
    }

    public static Legacy active(ItemStack stack) {
        if (AkatsukiAwakening.isActive(stack)) {
            return Legacy.AKATSUKI;
        }
        if (KyoukaAwakening.isActive(stack)) {
            return Legacy.KYOUKA;
        }
        if (SenbonzakuraAwakening.isActive(stack)) {
            return Legacy.SENBONZAKURA;
        }
        return Legacy.NONE;
    }

    private SoulLegacyState() {
    }
}
