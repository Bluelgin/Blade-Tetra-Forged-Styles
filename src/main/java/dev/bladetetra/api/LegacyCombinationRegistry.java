package dev.bladetetra.api;

import dev.bladetetra.forging.FoxLegacyParts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Public registry reserved for future named-part combination mechanics. */
public final class LegacyCombinationRegistry {
    private static final List<LegacyCombination> VALUES = new ArrayList<>();

    public static synchronized void register(LegacyCombination combination) {
        if (VALUES.stream().anyMatch(value -> value.id().equals(combination.id()))) {
            throw new IllegalArgumentException("Duplicate legacy combination " + combination.id());
        }
        VALUES.add(combination);
        VALUES.sort(Comparator.comparingInt(LegacyCombination::priority).reversed());
    }

    public static synchronized Optional<LegacyCombination> resolve(ItemStack stack) {
        FoxLegacyParts parts = FoxLegacyParts.fromStack(stack);
        return VALUES.stream().filter(value -> value.matches(stack, parts)).findFirst();
    }

    public static synchronized Optional<LegacyCombination> get(ResourceLocation id) {
        return VALUES.stream().filter(value -> value.id().equals(id)).findFirst();
    }

    public static synchronized List<LegacyCombination> values() {
        return List.copyOf(VALUES);
    }

    private LegacyCombinationRegistry() {
    }
}
