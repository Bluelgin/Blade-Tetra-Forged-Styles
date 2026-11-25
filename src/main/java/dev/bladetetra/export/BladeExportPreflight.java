package dev.bladetetra.export;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side, read-only diagnosis of the exact blade being sealed.
 *
 * <p>Packaging remains authoritative: client resource packs, provider bytes and export
 * permissions are checked again during the final bake. This preview never promises
 * that a passing server-side inspection guarantees a successful JAR.</p>
 */
final class BladeExportPreflight {
    record Report(List<String> preserved, List<String> blockers) {
        Report {
            preserved = List.copyOf(preserved);
            blockers = List.copyOf(blockers);
        }

        boolean ready() {
            return blockers.isEmpty();
        }

        String summary() {
            String value = ready() ? String.join(", ", preserved) : String.join("; ", blockers);
            // Bounded network/UI summary, not a replacement for the full diagnostic log.
            return value.length() <= 180 ? value : value.substring(0, 177) + "...";
        }
    }

    static Report inspect(BladeExportSnapshot snapshot, ItemStack blade) {
        var preserved = new ArrayList<String>();
        var blockers = new ArrayList<String>();

        preserved.add("SlashBlade base state");
        preserved.add("style=" + snapshot.style());
        if (!snapshot.attributes().isEmpty()) preserved.add(snapshot.attributes().size() + " attributes");
        if (!snapshot.enchantments().isEmpty()) preserved.add(snapshot.enchantments().size() + " enchantments");
        if (!snapshot.specialEffects().isEmpty()) preserved.add(snapshot.specialEffects().size() + " special effects");

        var effects = snapshot.componentEffects();
        if (effects.drawBonus() > 0) preserved.add("quickdraw saya");
        if (effects.spiritBonus() > 0) preserved.add("spirit saya");
        if (effects.saltyFish()) preserved.add("Salty Fish hit feedback");
        if (effects.heavyPommel()) preserved.add("heavy pommel");
        if (effects.rankDivisor() > 0) preserved.add("rank grip");
        if (effects.guardReduction() > 0) preserved.add("guard tsuba");
        if (effects.precisionHabaki()) preserved.add("precision habaki (half rank decay)");
        if (effects.reinforcedHabaki()) preserved.add("reinforced habaki (+50% ordinary horizontal knockback)");

        try {
            StandaloneBladeExport.checkCombat(snapshot);
        } catch (IllegalArgumentException | IllegalStateException unsupported) {
            blockers.add("combat: " + unsupported.getMessage());
        }
        try {
            BladeExportArt.check(snapshot, blade);
        } catch (IllegalArgumentException | IllegalStateException unsupported) {
            blockers.add("appearance: " + unsupported.getMessage());
        }
        return new Report(preserved, blockers);
    }

    private BladeExportPreflight() {}
}
