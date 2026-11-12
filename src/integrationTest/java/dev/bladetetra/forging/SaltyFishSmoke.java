package dev.bladetetra.forging;

import dev.bladetetra.combat.BladeStyle;
import dev.bladetetra.combat.ModComboStates;
import dev.bladetetra.combat.StyleResolver;
import dev.bladetetra.config.GameplayConfig;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;
import se.mickelus.tetra.blocks.workbench.BasicWorkbenchBlock;
import se.mickelus.tetra.blocks.workbench.WorkbenchTile;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemUpgradeRegistry;
import se.mickelus.tetra.module.SchematicRegistry;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;
import se.mickelus.tetra.module.schematic.CraftingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.bladetetra.item.ModularSlashBladeItem.BLADE_SLOT;
import static dev.bladetetra.item.ModularSlashBladeItem.SAYA_SLOT;

public final class SaltyFishSmoke {
    public record Samples(ItemStack scroll, ItemStack shortBlade, ItemStack standardBlade,
            ItemStack longBlade, ItemStack mixedBlade, ItemStack caughtBlade) {}

    private static final Set<String> ORDINARY_BLADES = Set.of(
            "orthodox_blade", "katana_blade", "wakizashi_blade", "nodachi_blade");
    private static final Set<String> FISH_BLADES = Set.of(
            "fish_blade_standard", "fish_blade_iaido", "fish_blade_short", "fish_blade_long");

    public static Samples verify(ServerLevel level, Player player, BlockPos pos) throws Exception {
        ItemStack scroll = verifyRecipe(level);
        List<ResourceLocation> unlocks = ScrollData.read(scroll).schematics;
        check(unlocks.size() == 5, "Scroll must list five schematics");

        level.setBlockAndUpdate(pos, BasicWorkbenchBlock.instance.defaultBlockState());
        WorkbenchTile bench = (WorkbenchTile) level.getBlockEntity(pos);
        check(bench != null, "Native workbench missing");
        var item = ModItems.MODULAR_SLASHBLADE.get();
        var ordinary = item.createDefaultStack();
        verifyChoices(player, bench, ordinary, false);

        place(player, pos, scroll.copy());
        verifyChoices(player, bench, ordinary, true);
        check(Arrays.asList(bench.getUnlockedSchematics()).containsAll(unlocks),
                "Opened scroll did not list all five recipes");
        var withFishSaya = craft(player, bench, ordinary, SAYA_SLOT, SaltyFishParts.FISH_SAYA,
                new ItemStack(Items.SALMON, 2));
        check(SaltyFishParts.blade(withFishSaya) == null
                && SaltyFishParts.saya(withFishSaya) == SaltyFishParts.Length.STANDARD, "Ordinary iron blade and fish sheath");
        var fishWithOrdinarySaya = craft(player, bench, ordinary, BLADE_SLOT, SaltyFishParts.IAIDO_BLADE,
                new ItemStack(Items.BONE, 2));
        check(SaltyFishParts.blade(fishWithOrdinarySaya) == SaltyFishParts.Length.STANDARD
                && SaltyFishParts.saya(fishWithOrdinarySaya) == null, "Iaido fishbone and ordinary sheath");

        var changed = withFishSaya;
        for (var blade : SaltyFishParts.Blade.values()) {
            var expectedStyle = BladeStyle.valueOf(blade.name());
            var expectedLength = switch (blade) {
                case STANDARD, IAIDO -> SaltyFishParts.Length.STANDARD;
                case RENGEKI -> SaltyFishParts.Length.SHORT;
                case DANGAKU -> SaltyFishParts.Length.LONG;
            };
            changed = craft(player, bench, changed, BLADE_SLOT, blade.module,
                    new ItemStack(Items.BONE, switch (blade) {
                        case STANDARD, IAIDO -> 2;
                        case RENGEKI -> 1;
                        case DANGAKU -> 3;
                    }));
            check(StyleResolver.resolve(changed) == expectedStyle
                    && changed.getCapability(ItemSlashBlade.BLADESTATE).orElseThrow(IllegalStateException::new)
                            .getComboRoot().equals(ModComboStates.getRoot(expectedStyle)), "Wrong combat root for " + blade);
            check(SaltyFishParts.blade(changed) == expectedLength
                    && SaltyFishParts.saya(changed) == expectedLength
                    && SaltyFishParts.FISH_SAYA.equals(changed.getTag().getString(SAYA_SLOT)), "Wrong sheath length for " + blade);
        }

        check(Arrays.asList(bench.getUnlockedSchematics()).containsAll(unlocks), "Craft consumed the scroll");
        level.removeBlock(pos.above(), false);
        verifyChoices(player, bench, ordinary, false);
        var plainShort = craft(player, bench, withFishSaya, BLADE_SLOT, "slashblade/wakizashi_blade", new ItemStack(Items.IRON_INGOT));
        var plainLong = craft(player, bench, plainShort, BLADE_SLOT, "slashblade/nodachi_blade", new ItemStack(Items.IRON_INGOT, 3));
        check(SaltyFishParts.saya(plainShort) == SaltyFishParts.Length.SHORT
                && SaltyFishParts.saya(plainLong) == SaltyFishParts.Length.LONG, "Ordinary blade length did not drive sheath");

        // The V1.0 scroll used the same standard blade key, alongside five other keys.
        var oldData = ScrollData.read(scroll);
        oldData.schematics = List.of("fish_blade_short", "fish_blade_standard",
                "fish_blade_long", "fish_saya_short", "fish_saya_standard", "fish_saya_long").stream()
                .map(path -> new ResourceLocation(schematicKey(path))).toList();
        var oldScroll = new ItemStack(ScrollItem.instance);
        oldData.write(oldScroll);
        place(player, pos, oldScroll);
        verifyChoices(player, bench, ordinary, true);
        level.removeBlock(pos.above(), false);
        place(player, pos, scroll.copy());

        ItemStack catchStack = verifyFishing(level, scroll);
        var guard = SaltyFishParts.salvagedGuard(catchStack);
        check(guard != null, "No compatible guard from baseline SlashBlade sources");
        var other = NamedLegacyCatalog.get(guard.id().equals("fox_black") ? "slashblade/yamato" : "fox_black");
        check(other != null, "Second baseline guard missing");
        var mixed = SaltyFishParts.create(SaltyFishParts.Blade.DANGAKU, other);
        for (var length : SaltyFishParts.Length.values()) {
            String module = "slashblade/" + length.part("saya");
            IModularItem.putModuleInSlot(mixed, SAYA_SLOT, module, length.part("saya") + "/salted_fish");
            IModularItem.updateIdentifier(mixed);
            var restored = ItemStack.of(mixed.save(new CompoundTag()));
            check(SaltyFishParts.saya(restored) == length && module.equals(restored.getTag().getString(SAYA_SLOT))
                    && ItemUpgradeRegistry.instance.getModule(module).getVariantData(restored).key
                            .equals(length.part("saya") + "/salted_fish"), "Legacy sheath failed to load " + length);
        }
        IModularItem.putModuleInSlot(mixed, SAYA_SLOT, "slashblade/fish_saya_short", "fish_saya_short/salted_fish");
        IModularItem.updateIdentifier(mixed);
        return new Samples(scroll,
                SaltyFishParts.create(SaltyFishParts.Blade.RENGEKI, guard),
                SaltyFishParts.create(SaltyFishParts.Blade.STANDARD, guard),
                SaltyFishParts.create(SaltyFishParts.Blade.DANGAKU, other), mixed, catchStack);
    }

    private static void verifyChoices(Player player, WorkbenchTile bench, ItemStack stack, boolean fish) {
        var bladeChoices = Arrays.stream(schematics(player, bench, stack, BLADE_SLOT))
                .map(UpgradeSchematic::getKey).collect(Collectors.toSet());
        check((fish ? FISH_BLADES : ORDINARY_BLADES).stream().map(SaltyFishSmoke::schematicKey)
                        .allMatch(bladeChoices::contains)
                && (fish ? ORDINARY_BLADES : FISH_BLADES).stream().map(SaltyFishSmoke::schematicKey)
                        .noneMatch(bladeChoices::contains), "Wrong blade choices: " + bladeChoices);
        var fishSayaCount = Arrays.stream(schematics(player, bench, stack, SAYA_SLOT))
                .map(UpgradeSchematic::getKey).filter(key -> key.startsWith(schematicKey("fish_saya"))).count();
        check(fishSayaCount == (fish ? 1 : 0), "Wrong number of fish sheath choices: " + fishSayaCount);
    }

    private static UpgradeSchematic[] schematics(Player player, WorkbenchTile bench, ItemStack stack, String slot) {
        return SchematicRegistry.getSchematics(new CraftingContext(bench.getLevel(), bench.getBlockPos(),
                bench.getBlockState(), player, stack, slot, bench.getUnlockedSchematics()));
    }

    private static String schematicKey(String path) {
        return "tetra:slashblade/" + path;
    }

    private static ItemStack verifyRecipe(ServerLevel level) {
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

    private static ItemStack craft(Player player, WorkbenchTile bench, ItemStack stack, String slot,
            String module, ItemStack input) throws Exception {
        var schematic = Arrays.stream(schematics(player, bench, stack, slot))
                .filter(option -> option.getKey().equals("tetra:" + module)).findFirst().orElseThrow();
        check(schematic.getRequiredQuantity(stack, 0, input) == input.getCount(), "Wrong resource cost for " + module);
        var materials = new ItemStack[]{input};
        check(schematic.isMaterialsValid(stack, slot, materials), "Invalid material for " + module);
        ItemStack upgraded = schematic.applyUpgrade(stack, materials, true, slot, player);
        check(input.isEmpty() && module.equals(upgraded.getOrCreateTag().getString(slot)), "Upgrade failed " + module);
        var variant = ItemUpgradeRegistry.instance.getModule(module).getVariantData(upgraded);
        check(variant != null && variant.key.startsWith(module.substring("slashblade/".length()) + "/"),
                "Missing native module variant");
        ModItems.MODULAR_SLASHBLADE.get().assemble(upgraded, player.level(), 0);
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
                    SaltyFishParts.create(SaltyFishParts.Blade.RENGEKI, null)) == null,
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
