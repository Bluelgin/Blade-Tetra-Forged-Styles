package dev.bladetetra.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** A bounded crafting grid of exact item IDs. Missing registry entries are checked before packaging. */
public record BladeExportRecipe(List<ResourceLocation> occupiedItems, int occupiedSlots) {
    public BladeExportRecipe {
        occupiedItems = List.copyOf(occupiedItems);
        if (occupiedSlots < 1 || occupiedSlots > 0x1ff
                || Integer.bitCount(occupiedSlots) != occupiedItems.size()) {
            throw new IllegalArgumentException("Expected one to nine occupied cells in a 3×3 crafting grid");
        }
        for (ResourceLocation item : occupiedItems) {
            if (!ForgeRegistries.ITEMS.containsKey(item)
                    || ForgeRegistries.ITEMS.getValue(item) == Items.AIR) {
                throw new IllegalArgumentException("Unavailable crafting ingredient: " + item);
            }
        }
    }

    Set<String> namespaces() {
        Set<String> result = new TreeSet<>();
        occupiedItems.forEach(item -> result.add(item.getNamespace()));
        return result;
    }

    JsonObject encode(ResourceLocation blade) {
        Map<ResourceLocation, Character> symbols = new LinkedHashMap<>();
        JsonArray pattern = new JsonArray();
        int ingredientIndex = 0;
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder(3);
            for (int column = 0; column < 3; column++) {
                if ((occupiedSlots & (1 << (row * 3 + column))) == 0) {
                    line.append(' ');
                } else {
                    ResourceLocation item = occupiedItems.get(ingredientIndex++);
                    line.append(symbols.computeIfAbsent(item, ignored -> (char) ('A' + symbols.size())));
                }
            }
            pattern.add(line.toString());
        }

        JsonObject key = new JsonObject();
        symbols.forEach((item, symbol) -> {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", item.toString());
            key.add(symbol.toString(), ingredient);
        });
        JsonObject result = new JsonObject();
        result.addProperty("item", "slashblade:slashblade");
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", "slashblade:shaped_blade");
        recipe.addProperty("blade", Objects.requireNonNull(blade).toString());
        recipe.addProperty("category", "equipment");
        recipe.add("key", key);
        recipe.add("pattern", pattern);
        recipe.add("result", result);
        return recipe;
    }
}
