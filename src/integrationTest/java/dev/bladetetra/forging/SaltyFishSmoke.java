package dev.bladetetra.forging;

import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;
import se.mickelus.tetra.blocks.workbench.BasicWorkbenchBlock;
import se.mickelus.tetra.blocks.workbench.WorkbenchTile;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.ItemUpgradeRegistry;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.CraftingContext;

import java.util.Arrays;
import java.util.List;

public final class SaltyFishSmoke {
    public record Samples(ItemStack scroll, ItemStack shortBlade, ItemStack standardBlade,
            ItemStack longBlade, ItemStack mixedBlade) {}

    public static Samples verify(ServerLevel level, Player player, BlockPos pos) throws Exception {
        ItemStack scroll = verifyRecipe(level, player);
        List<ResourceLocation> unlocks = ScrollData.read(scroll).schematics;
        check(unlocks.size() == 6, "Scroll must unlock six schematics");

        level.setBlockAndUpdate(pos, BasicWorkbenchBlock.instance.defaultBlockState());
        WorkbenchTile bench = (WorkbenchTile) level.getBlockEntity(pos);
        check(bench != null, "Native workbench missing");
        place(player, pos, scroll.copy());
        check(Arrays.asList(bench.getUnlockedSchematics()).containsAll(unlocks),
                "Opened scroll did not unlock all six recipes");

        ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        for (var length : SaltyFishParts.Length.values()) {
            stack = craft(level, player, pos, bench, stack, length, "blade");
            stack = craft(level, player, pos, bench, stack, length, "saya");
        }
        stack = craft(level, player, pos, bench, stack, SaltyFishParts.Length.SHORT, "saya");
        ItemStack restored = ItemStack.of(stack.save(new CompoundTag()));
        check(SaltyFishParts.blade(restored) == SaltyFishParts.Length.LONG
                && SaltyFishParts.saya(restored) == SaltyFishParts.Length.SHORT, "Mixed save round trip");
        check(Arrays.asList(bench.getUnlockedSchematics()).containsAll(unlocks), "Craft consumed the scroll");
        level.removeBlock(pos.above(), false);
        check(Arrays.stream(bench.getUnlockedSchematics()).noneMatch(unlocks::contains),
                "Removing scroll failed to relock recipes");
        place(player, pos, scroll.copy());

        ItemStack catchStack = verifyFishing(level, scroll);
        var guard = SaltyFishParts.salvagedGuard(catchStack);
        check(guard != null, "No compatible guard from baseline SlashBlade sources");
        var other = NamedLegacyCatalog.values().stream()
                .filter(source -> !source.model().equals(guard.model())
                        && !source.equals(LegacyImprintKind.WOODEN_PUPPET))
                .sorted(java.util.Comparator.comparing(LegacyImprintKind::id)).findFirst().orElseThrow();
        var shortLength = SaltyFishParts.Length.SHORT;
        var standardLength = SaltyFishParts.Length.STANDARD;
        var longLength = SaltyFishParts.Length.LONG;
        return new Samples(scroll,
                SaltyFishParts.create(shortLength, shortLength, guard),
                SaltyFishParts.create(standardLength, standardLength, guard),
                SaltyFishParts.create(longLength, longLength, other),
                SaltyFishParts.create(longLength, shortLength, other));
    }

    private static ItemStack verifyRecipe(ServerLevel level, Player player) {
        var menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player p) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        var recipe = level.getRecipeManager().byKey(new ResourceLocation("blade_tetra", "salty_fish_scroll"))
                .orElseThrow();
        for (var fish : List.of(Items.COD, Items.SALMON)) {
            var ingredients = List.of(Items.PAPER, fish, Items.PAPER, Items.BONE, Items.INK_SAC,
                    Items.BONE, Items.PAPER, Items.STRING, Items.PAPER);
            for (int i = 0; i < ingredients.size(); i++) grid.setItem(i, new ItemStack(ingredients.get(i)));
            var matched = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level).orElseThrow();
            check(matched == recipe, "Fish recipe missing or shadowed");
            var result = matched.assemble(grid, level.registryAccess());
            check(result.is(ScrollItem.instance) && ItemStack.isSameItemSameTags(result,
                    ForgingScrolls.create(ForgingScrolls.Kind.SALTY_FISH)), "Recipe result is not native scroll data");
        }
        grid.setItem(1, new ItemStack(Items.COOKED_COD));
        check(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .filter(match -> match == recipe).isEmpty(), "Cooked fish accepted");
        return ForgingScrolls.create(ForgingScrolls.Kind.SALTY_FISH);
    }

    private static void place(Player player, BlockPos pos, ItemStack scroll) {
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        check(ScrollItem.instance.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))).consumesAction(),
                "Native scroll placement failed");
    }

    private static ItemStack craft(ServerLevel level, Player player, BlockPos pos, WorkbenchTile bench,
            ItemStack stack, SaltyFishParts.Length length, String part) throws Exception {
        String slot = "slashblade/" + part;
        String module = length.module(part);
        var schematic = new ConfigSchematic(DataManager.instance.schematicData.getData(
                new ResourceLocation("tetra", module)));
        var context = new CraftingContext(level, pos, bench.getBlockState(), player, stack, slot,
                bench.getUnlockedSchematics());
        check(schematic.matchesRequirements(context), "Native requirement rejected " + module);
        ItemStack input = new ItemStack(part.equals("blade") ? Items.BONE : Items.SALMON, length.ordinal() + 1);
        check(schematic.getRequiredQuantity(stack, 0, input) == input.getCount(), "Wrong resource cost");
        var materials = new ItemStack[]{input};
        check(schematic.isMaterialsValid(stack, slot, materials), "Invalid material for " + module);
        ItemStack upgraded = schematic.applyUpgrade(stack, materials, true, slot, player);
        check(input.isEmpty() && module.equals(upgraded.getOrCreateTag().getString(slot)), "Upgrade failed " + module);
        var variant = ItemUpgradeRegistry.instance.getModule(module).getVariantData(upgraded);
        check(variant != null && variant.key.startsWith(length.part(part) + "/"), "Missing native module variant");
        return upgraded;
    }

    private static ItemStack verifyFishing(ServerLevel level, ItemStack scroll) {
        var table = level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft", "gameplay/fishing"));
        var params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .create(LootContextParamSets.FISHING);
        var scrollChance = GameplayConfig.SALTY_FISH_SCROLL_CHANCE;
        var bladeChance = GameplayConfig.SALTY_FISH_BLADE_CHANCE;
        double previousScroll = scrollChance.get(), previousBlade = bladeChance.get();
        try {
            scrollChance.set(0.0);
            bladeChance.set(0.0);
            var baseline = table.getRandomItems(params, 151L);
            scrollChance.set(1.0);
            bladeChance.set(1.0);
            var bonus = table.getRandomItems(params, 151L);
            check(bonus.size() == baseline.size() + 2
                    && bonus.stream().anyMatch(item -> ItemStack.isSameItemSameTags(item, scroll)),
                    "Fishing should add exactly one identical scroll and one blade");
            ItemStack caught = bonus.stream().filter(item -> item.is(ModItems.MODULAR_SLASHBLADE.get()))
                    .findFirst().orElseThrow();
            var reloaded = ItemStack.of(caught.save(new CompoundTag()));
            check(SaltyFishParts.blade(reloaded) != null && SaltyFishParts.saya(reloaded) != null
                    && SaltyFishParts.salvagedGuard(reloaded) != null
                    && reloaded.getDamageValue() > 0 && reloaded.getDamageValue() < reloaded.getMaxDamage() - 1,
                    "Fishing blade lost parts, guard or wear");
            check(!NamedLegacyParts.fromStack(reloaded).present()
                    && NamedLegacyParts.bladeFromStack(reloaded) == null, "Fishing bypassed named inheritance");
            check(SaltyFishParts.salvagedGuard(
                    SaltyFishParts.create(SaltyFishParts.Length.SHORT, SaltyFishParts.Length.LONG, null)) == null,
                    "No-source construction failed");
            return reloaded;
        } finally {
            scrollChance.set(previousScroll);
            bladeChance.set(previousBlade);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private SaltyFishSmoke() {}
}
