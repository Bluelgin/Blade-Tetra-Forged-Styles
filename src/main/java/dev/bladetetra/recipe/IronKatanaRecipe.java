package dev.bladetetra.recipe;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModRecipes;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * The survival entry point for Blade Tetra.
 *
 * <p>A SlashBlade wooden puppet and three iron ingots are reforged into the
 * default iron katana construction. The source stack is serialized first so
 * its SlashBlade state, enchantments, kill/refine counts, owner, and broken
 * state survive the item conversion.</p>
 */
public final class IronKatanaRecipe extends CustomRecipe {
    private static final ResourceLocation WOODEN_PUPPET =
            Objects.requireNonNull(
                    ResourceLocation.tryParse("slashblade:slashblade_wood"));

    public IronKatanaRecipe(
            ResourceLocation id,
            CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findWoodenPuppet(container) != null;
    }

    @Override
    public ItemStack assemble(
            CraftingContainer container,
            RegistryAccess registryAccess) {
        ItemStack woodenPuppet = findWoodenPuppet(container);
        if (woodenPuppet == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag serializedBlade = woodenPuppet.save(new CompoundTag());
        serializedBlade.putString(
                "id",
                Objects.requireNonNull(
                        ForgeRegistries.ITEMS.getKey(
                                ModItems.MODULAR_SLASHBLADE.get()))
                        .toString());

        ItemStack output = ItemStack.of(serializedBlade);
        output.setCount(1);
        if (!(output.getItem() instanceof ModularSlashBladeItem modularBlade)) {
            return ItemStack.EMPTY;
        }

        modularBlade.migrateLegacyModules(output);
        modularBlade.syncDerivedBladeState(output);
        BladeLegacyEasterEggs.markIronKatanaOrigin(output);
        output.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state ->
                output.getOrCreateTag().put(
                        "bladeState",
                        state.serializeNBT()));
        return output;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(
                Ingredient.EMPTY,
                Ingredient.of(Objects.requireNonNull(
                        ForgeRegistries.ITEMS.getValue(WOODEN_PUPPET))),
                Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT),
                Ingredient.of(Items.IRON_INGOT));
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 4;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.IRON_KATANA.get();
    }

    private static ItemStack findWoodenPuppet(CraftingContainer container) {
        ItemStack woodenPuppet = ItemStack.EMPTY;
        int ironIngots = 0;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (WOODEN_PUPPET.equals(
                    ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    && woodenPuppet.isEmpty()) {
                woodenPuppet = stack;
                continue;
            }
            if (stack.is(Items.IRON_INGOT)) {
                ironIngots++;
                continue;
            }
            return null;
        }

        return !woodenPuppet.isEmpty() && ironIngots == 3
                ? woodenPuppet
                : null;
    }
}
