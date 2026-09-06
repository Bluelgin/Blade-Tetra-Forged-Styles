package dev.bladetetra.recipe;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModRecipes;
import dev.bladetetra.visual.TsukaWrapColor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class TsukaWrapColorRecipe extends CustomRecipe {
    public TsukaWrapColorRecipe(
            ResourceLocation id,
            CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        Inputs inputs = findInputs(container);
        if (inputs == null) {
            return false;
        }
        DyeColor color = TsukaWrapColor.fromCarpet(inputs.decoration());
        return color != null
                || inputs.decoration().is(Items.STRING)
                && !TsukaWrapColor.DEFAULT.equals(
                        TsukaWrapColor.resolve(inputs.blade()));
    }

    @Override
    public ItemStack assemble(
            CraftingContainer container,
            RegistryAccess registryAccess) {
        Inputs inputs = findInputs(container);
        if (inputs == null) {
            return ItemStack.EMPTY;
        }

        ItemStack output = inputs.blade().copyWithCount(1);
        DyeColor color = TsukaWrapColor.fromCarpet(inputs.decoration());
        if (color != null) {
            TsukaWrapColor.apply(output, color);
            return output;
        }
        return inputs.decoration().is(Items.STRING)
                && TsukaWrapColor.clear(output)
                ? output
                : ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TSUKA_WRAP_COLOR.get();
    }

    private static Inputs findInputs(CraftingContainer container) {
        ItemStack blade = ItemStack.EMPTY;
        ItemStack decoration = ItemStack.EMPTY;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ModularSlashBladeItem
                    && blade.isEmpty()) {
                blade = stack;
                continue;
            }
            if ((TsukaWrapColor.fromCarpet(stack) != null
                    || stack.is(Items.STRING))
                    && decoration.isEmpty()) {
                decoration = stack;
                continue;
            }
            return null;
        }

        return blade.isEmpty() || decoration.isEmpty()
                ? null
                : new Inputs(blade, decoration);
    }

    private record Inputs(ItemStack blade, ItemStack decoration) {
    }
}
