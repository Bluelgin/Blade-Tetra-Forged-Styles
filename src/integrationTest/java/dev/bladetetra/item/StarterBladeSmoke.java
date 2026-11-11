package dev.bladetetra.item;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.registry.ModItems;
import dev.bladetetra.visual.MaterialAppearance;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.RemoveSchematic;

import java.util.List;

import static dev.bladetetra.item.ModularSlashBladeItem.*;

public final class StarterBladeSmoke {
    public static void verify(Player player) throws Exception {
        Level level = player.level();
        var wooden = craft(level, "wooden_orthodox", Items.OAK_PLANKS, 2);
        var iron = craft(level, "iron_katana", Items.IRON_INGOT, 3);
        var blade = ModItems.MODULAR_SLASHBLADE.get();
        check(StyleResolver.resolve(wooden) == BladeStyle.STANDARD, "wooden style");
        check(StyleResolver.resolve(iron) == BladeStyle.IAIDO, "iron style");
        check(!wooden.getTag().getBoolean("blade_tetra_shoshin_iron_katana_origin")
                && iron.getTag().getBoolean("blade_tetra_shoshin_iron_katana_origin"), "recipe origins");
        variant(blade, wooden, BLADE_SLOT, "orthodox_blade/oak");
        variant(blade, wooden, TSUKA_SLOT, "wrapped_tsuka/stick");
        variant(blade, wooden, SAYA_SLOT, "basic_saya/oak");
        variant(blade, wooden, TSUBA_SLOT, "simple_tsuba/oak");
        variant(blade, wooden, KASHIRA_SLOT, "simple_kashira/oak");
        check(blade.getModuleFromSlot(wooden, HABAKI_SLOT) == null, "wooden habaki absent");
        variant(blade, iron, BLADE_SLOT, "katana_blade/iron");
        variant(blade, iron, TSUBA_SLOT, "simple_tsuba/iron");
        variant(blade, iron, HABAKI_SLOT, "basic_habaki/iron");
        variant(blade, iron, KASHIRA_SLOT, "simple_kashira/iron");
        check(wooden.getMaxDamage() > 1 && wooden.getMaxDamage() < iron.getMaxDamage(), "wooden durability");

        var remove = new RemoveSchematic();
        for (String required : List.of(BLADE_SLOT, TSUKA_SLOT, SAYA_SLOT)) {
            check(!remove.isApplicableForSlot(required, iron), "required slot is protected " + required);
        }
        for (String optional : List.of(TSUBA_SLOT, HABAKI_SLOT, KASHIRA_SLOT)) {
            check(remove.isApplicableForSlot(optional, iron), "remove offered " + optional);
            iron.getMaxDamage(); // Populate Tetra's cache before copying the blade.
            var preview = remove.applyUpgrade(iron, new ItemStack[0], false, optional, player);
            check(!blade.getIdentifier(preview).equals(blade.getIdentifier(iron)), "preview cache refreshed");
            check(blade.getModuleFromSlot(preview, optional) == null
                    && blade.getModuleFromSlot(iron, optional) != null, "preview independent");
            iron = remove.applyUpgrade(iron, new ItemStack[0], true, optional, player);
            blade.assemble(iron, level, 0);
            iron = ItemStack.of(iron.save(new CompoundTag()));
            check(!iron.getTag().contains(optional), "empty slot persisted " + optional);
            check(!blade.migrateLegacyModules(iron), "removal migration is idempotent");
        }
        check(MaterialAppearance.fromStack(iron).tsubaProfile() == MaterialAppearance.TsubaProfile.NONE,
                "empty guard has no geometry");
        var noGuard = schematic("remove_tsuba").applyUpgrade(wooden, new ItemStack[0], false, TSUBA_SLOT, player);
        variant(blade, noGuard, TSUBA_SLOT, "tsubaless/none");
        var upgrade = schematic("orthodox_blade");
        ItemStack[] materials = { new ItemStack(Items.IRON_INGOT, 64) };
        check(upgrade.isMaterialsValid(wooden, BLADE_SLOT, materials), "iron upgrade materials");
        var upgraded = upgrade.applyUpgrade(wooden, materials, true, BLADE_SLOT, player);
        variant(blade, upgraded, BLADE_SLOT, "orthodox_blade/iron");
        check(StyleResolver.resolve(upgraded) == BladeStyle.STANDARD, "changing material preserves style");

        ItemStack alpha = new ItemStack(blade);
        var legacy = alpha.getOrCreateTag();
        legacy.putString("sword/blade", "sword/basic_blade");
        legacy.putString("sword/basic_blade_material", "basic_blade/diamond");
        legacy.putInt("sword/blade/settle_progress", 42);
        legacy.putString("fixture:unknown", "retained");
        check(blade.migrateLegacyModules(alpha), "Alpha converted");
        variant(blade, alpha, BLADE_SLOT, "katana_blade/diamond");
        check(legacy.getInt(BLADE_SLOT + "/settle_progress") == 42, "Alpha progression preserved");
        check("retained".equals(legacy.getString("fixture:unknown"))
                && !legacy.contains(TSUBA_SLOT) && !legacy.contains(HABAKI_SLOT) && !legacy.contains(KASHIRA_SLOT),
                "Alpha optional and unknown data preserved");
        check(!blade.migrateLegacyModules(alpha), "Alpha migration is idempotent");
    }

    public static ConfigSchematic schematic(String path) throws Exception {
        return new ConfigSchematic(DataManager.instance.schematicData.getData(
                ResourceLocation.fromNamespaceAndPath("tetra", "slashblade/" + path)));
    }

    public static ItemStack craft(Level level, String path, Item material, int count) {
        var menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void slotsChanged(net.minecraft.world.Container container) {}
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        var puppet = new ItemStack(ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("slashblade", "slashblade_wood")));
        puppet.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new).setKillCount(17);
        puppet.getOrCreateTag().putString("fixture:puppet", "retained");
        grid.setItem(4, puppet);
        for (int i = 0; i < count; i++) grid.setItem(i, new ItemStack(material));
        CraftingRecipe recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
        check(recipe.getId().equals(ResourceLocation.fromNamespaceAndPath("blade_tetra", path)), "exact recipe");
        check(!recipe.isSpecial() && recipe.getIngredients().size() == count + 1, "recipe book and JEI inputs");
        var result = recipe.assemble(grid, level.registryAccess());
        check(result.getCount() == 1 && recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),
                "single output and no duplicate puppet");
        check("retained".equals(result.getTag().getString("fixture:puppet"))
                && result.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new)
                        .getKillCount() == 17, "source state retained");
        return result;
    }

    private static void variant(ModularSlashBladeItem blade, ItemStack stack, String slot, String expected) {
        var module = blade.getModuleFromSlot(stack, slot);
        check(module != null && expected.equals(module.getVariantData(stack).key), slot + " registered " + expected);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("Starter blade regression: " + label);
    }

    private StarterBladeSmoke() {}
}
