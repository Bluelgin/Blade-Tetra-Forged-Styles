package dev.bladetetra.registry;

import dev.bladetetra.BladeTetra;
import dev.bladetetra.recipe.ModularSlashBladeRecipe;
import dev.bladetetra.recipe.IronKatanaRecipe;
import dev.bladetetra.recipe.SayaBannerSkinRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BladeTetra.MOD_ID);

    public static final RegistryObject<RecipeSerializer<ModularSlashBladeRecipe>>
            MODULAR_SLASHBLADE_CONVERSION =
            SERIALIZERS.register(
                    "modular_slashblade_conversion",
                    () -> new SimpleCraftingRecipeSerializer<>(ModularSlashBladeRecipe::new));

    public static final RegistryObject<RecipeSerializer<IronKatanaRecipe>>
            IRON_KATANA =
            SERIALIZERS.register(
                    "iron_katana",
                    () -> new SimpleCraftingRecipeSerializer<>(IronKatanaRecipe::new));

    public static final RegistryObject<RecipeSerializer<SayaBannerSkinRecipe>>
            SAYA_BANNER_SKIN =
            SERIALIZERS.register(
                    "saya_banner_skin",
                    () -> new SimpleCraftingRecipeSerializer<>(SayaBannerSkinRecipe::new));

    private ModRecipes() {
    }
}
