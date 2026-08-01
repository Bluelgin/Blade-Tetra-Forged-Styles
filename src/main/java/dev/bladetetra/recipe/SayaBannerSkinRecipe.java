package dev.bladetetra.recipe;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.SayaPatternItem;
import dev.bladetetra.registry.ModRecipes;
import dev.bladetetra.visual.SayaBannerSkin;
import dev.bladetetra.visual.SayaPresetSkin;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class SayaBannerSkinRecipe extends CustomRecipe {
    public SayaBannerSkinRecipe(
            ResourceLocation id,
            CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        Inputs inputs = findInputs(container);
        return inputs != null
                && (inputs.decoration().getItem() instanceof BannerItem
                        || inputs.decoration().getItem() instanceof SayaPatternItem
                        || inputs.decoration().is(Items.SHEARS)
                        && hasSayaSkin(inputs.blade()));
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
        if (inputs.decoration().getItem() instanceof BannerItem) {
            return SayaBannerSkin.apply(output, inputs.decoration())
                    ? output
                    : ItemStack.EMPTY;
        }
        if (inputs.decoration().getItem() instanceof SayaPatternItem patternItem) {
            return SayaPresetSkin.apply(output, patternItem.preset())
                    ? output
                    : ItemStack.EMPTY;
        }
        if (inputs.decoration().is(Items.SHEARS)
                && clearSayaSkin(output)) {
            return output;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(
            CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(
                container.getContainerSize(),
                ItemStack.EMPTY);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(Items.SHEARS)) {
                continue;
            }

            ItemStack shears = stack.copyWithCount(1);
            shears.setDamageValue(shears.getDamageValue() + 1);
            if (shears.getDamageValue() < shears.getMaxDamage()) {
                remaining.set(slot, shears);
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SAYA_BANNER_SKIN.get();
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

            if ((stack.getItem() instanceof BannerItem
                    || stack.getItem() instanceof SayaPatternItem
                    || stack.is(Items.SHEARS))
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

    private static boolean hasSayaSkin(ItemStack blade) {
        return SayaBannerSkin.fromStack(blade).present()
                || SayaPresetSkin.fromStack(blade).present();
    }

    private static boolean clearSayaSkin(ItemStack blade) {
        boolean clearedBanner = SayaBannerSkin.clear(blade);
        boolean clearedPreset = SayaPresetSkin.clear(blade);
        return clearedBanner || clearedPreset;
    }

    private record Inputs(ItemStack blade, ItemStack decoration) {
    }
}
