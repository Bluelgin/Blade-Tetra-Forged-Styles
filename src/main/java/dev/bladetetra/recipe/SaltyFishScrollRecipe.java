package dev.bladetetra.recipe;

import dev.bladetetra.forging.ForgingScrolls;
import dev.bladetetra.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

public final class SaltyFishScrollRecipe extends CustomRecipe {
    private static final Ingredient PAPER = Ingredient.of(Items.PAPER);
    private static final Ingredient BONE = Ingredient.of(Items.BONE);
    private static final List<Ingredient> PATTERN = List.of(
            PAPER, Ingredient.of(Items.COD, Items.SALMON), PAPER,
            BONE, Ingredient.of(Items.INK_SAC), BONE,
            PAPER, Ingredient.of(Items.STRING), PAPER);

    public SaltyFishScrollRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        if (grid.getWidth() != 3 || grid.getHeight() != 3) return false;
        for (int slot = 0; slot < PATTERN.size(); slot++) {
            if (!PATTERN.get(slot).test(grid.getItem(slot))) return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        return getResultItem(access);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return ForgingScrolls.create(ForgingScrolls.Kind.SALTY_FISH);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(PATTERN);
        return ingredients;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width == 3 && height == 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SALTY_FISH_SCROLL.get();
    }
}
