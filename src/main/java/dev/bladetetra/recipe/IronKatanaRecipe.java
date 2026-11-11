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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/** Reforges a wooden puppet while retaining its native SlashBlade state. */
public final class IronKatanaRecipe extends CustomRecipe {
    private static final ResourceLocation WOODEN_PUPPET =
            ResourceLocation.fromNamespaceAndPath("slashblade", "slashblade_wood");

    private final boolean wooden;
    private final Item material;
    private final int materialCount;

    public IronKatanaRecipe(ResourceLocation id, CraftingBookCategory category) {
        this(id, category, false);
    }

    public IronKatanaRecipe(ResourceLocation id, CraftingBookCategory category, boolean wooden) {
        super(id, category);
        this.wooden = wooden;
        this.material = wooden ? Items.OAK_PLANKS : Items.IRON_INGOT;
        this.materialCount = wooden ? 2 : 3;
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

        modularBlade.initializeStarterStack(output, wooden);
        if (!wooden) {
            BladeLegacyEasterEggs.markIronKatanaOrigin(output);
        }
        output.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state ->
                output.getOrCreateTag().put(
                        "bladeState",
                        state.serializeNBT()));
        return output;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        var item = ModItems.MODULAR_SLASHBLADE.get();
        return wooden ? item.createWoodenStarterStack() : item.createDefaultStack();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(Ingredient.of(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(WOODEN_PUPPET))));
        for (int i = 0; i < materialCount; i++) {
            ingredients.add(Ingredient.of(material));
        }
        return ingredients;
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= materialCount + 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return wooden ? ModRecipes.WOODEN_ORTHODOX.get() : ModRecipes.IRON_KATANA.get();
    }

    private ItemStack findWoodenPuppet(CraftingContainer container) {
        ItemStack woodenPuppet = ItemStack.EMPTY;
        int materials = 0;

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
            if (stack.is(material)) {
                materials++;
                continue;
            }
            return null;
        }

        return !woodenPuppet.isEmpty() && materials == materialCount
                ? woodenPuppet
                : null;
    }
}
