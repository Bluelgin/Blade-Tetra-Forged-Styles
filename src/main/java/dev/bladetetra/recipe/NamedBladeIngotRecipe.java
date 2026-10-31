package dev.bladetetra.recipe;

import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.LegacyResearchability;
import dev.bladetetra.item.NamedBladeIngotItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModRecipes;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

/**
 * Shaped 3x3 extraction: eight broken Proud Souls surround a valid named blade.
 * A damaged copy of the source is returned, rather than consuming the original.
 */
public final class NamedBladeIngotRecipe extends CustomRecipe {
    private static final String SOUL = "slashblade:proudsoul_tiny";

    public NamedBladeIngotRecipe(net.minecraft.resources.ResourceLocation id,
            CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return findBlade(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        ItemStack original = findBlade(grid);
        if (original == null) return ItemStack.EMPTY;
        LegacyImprintKind kind = LegacyResearchability.identify(original);
        return kind == null ? ItemStack.EMPTY : NamedBladeIngotItem.create(kind);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> remaining =
                NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        ItemStack blade = findBlade(grid);
        if (blade == null) return remaining;
        int center = (grid.getHeight() / 2) * grid.getWidth() + grid.getWidth() / 2;
        ItemStack saved = blade.copy();
        saved.setCount(1);
        saved.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
            int cost = cost(state.getMaxDamage());
            state.setDamage(state.getDamage() + cost);
            saved.getOrCreateTag().put("bladeState", state.serializeNBT());
        });
        remaining.set(center, saved);
        return remaining;
    }

    public static int cost(int maximum) {
        return Math.max(1, (int)Math.ceil(Math.max(2, maximum) * 0.10D));
    }

    private static ItemStack findBlade(CraftingContainer grid) {
        if (grid.getWidth() != 3 || grid.getHeight() != 3) return null;
        ItemStack center = grid.getItem(4);
        if (!(center.getItem() instanceof ItemSlashBlade)) return null;
        if (LegacyResearchability.identify(center) == null) return null;
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty() || !SOUL.equals(
                    String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())))) {
                return null;
            }
        }
        return center.getCapability(ItemSlashBlade.BLADESTATE)
                .resolve()
                .filter(state -> state.getMaxDamage() > 1
                        && state.getDamage() + cost(state.getMaxDamage()) < state.getMaxDamage())
                .map(state -> center)
                .orElse(null);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return new ItemStack(ModItems.NAMED_BLADE_INGOT.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.NAMED_BLADE_INGOT.get();
    }
}
