package dev.bladetetra.recipe;

import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.registry.ModRecipes;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.Set;

public final class ModularSlashBladeRecipe extends CustomRecipe {
    private static final ResourceLocation TETRA_SWORD =
            Objects.requireNonNull(ResourceLocation.tryParse("tetra:modular_sword"));
    private static final Set<String> TETRA_METADATA = Set.of(
            "id",
            "repairCount",
            "cooledStrength",
            "honing_progress",
            "honing_available",
            "honing_count");

    public ModularSlashBladeRecipe(
            ResourceLocation id,
            CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findInputs(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        Inputs inputs = findInputs(container);
        if (inputs == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag serializedBlade = inputs.slashBlade.save(new CompoundTag());
        serializedBlade.putString(
                "id",
                ForgeRegistries.ITEMS.getKey(ModItems.MODULAR_SLASHBLADE.get()).toString());

        CompoundTag outputTag = serializedBlade.getCompound("tag");
        CompoundTag tetraTag = inputs.tetraSword.getTag();
        if (tetraTag != null) {
            for (String key : tetraTag.getAllKeys()) {
                if (key.startsWith("sword/") || TETRA_METADATA.contains(key)) {
                    if (tetraTag.get(key) != null) {
                        outputTag.put(key, tetraTag.get(key).copy());
                    }
                }
            }
        }
        serializedBlade.put("tag", outputTag);

        ItemStack output = ItemStack.of(serializedBlade);
        output.setCount(1);
        if (!(output.getItem() instanceof ModularSlashBladeItem modularBlade)) {
            return ItemStack.EMPTY;
        }
        modularBlade.migrateLegacyModules(output);
        modularBlade.syncDerivedBladeState(output);
        output.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state ->
                output.getOrCreateTag().put("bladeState", state.serializeNBT()));
        return output;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MODULAR_SLASHBLADE_CONVERSION.get();
    }

    private static Inputs findInputs(CraftingContainer container) {
        ItemStack slashBlade = ItemStack.EMPTY;
        ItemStack tetraSword = ItemStack.EMPTY;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof ItemSlashBlade
                    && !(stack.getItem() instanceof ModularSlashBladeItem)
                    && slashBlade.isEmpty()) {
                slashBlade = stack;
                continue;
            }

            if (TETRA_SWORD.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    && tetraSword.isEmpty()) {
                tetraSword = stack;
                continue;
            }

            return null;
        }

        return slashBlade.isEmpty() || tetraSword.isEmpty()
                ? null
                : new Inputs(slashBlade, tetraSword);
    }

    private record Inputs(ItemStack slashBlade, ItemStack tetraSword) {
    }
}
