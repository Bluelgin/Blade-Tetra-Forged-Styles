package dev.bladetetra.compat.effects;

import dev.bladetetra.item.ModularSlashBladeItem;
import se.mickelus.tetra.items.modular.ModularItem;

/** Extends an audited provider's original item gate only to our module-backed blade. */
public final class ProviderEffectEligibility {
    public static boolean accepts(Object item) {
        return item instanceof ModularItem || item instanceof ModularSlashBladeItem;
    }

    private ProviderEffectEligibility() {}
}
