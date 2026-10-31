package dev.bladetetra.forging;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.compat.LegacyImprintCraftingOutcome;
import dev.bladetetra.item.NamedBladeIngotItem;
import dev.bladetetra.recipe.NamedBladeIngotRecipe;
import dev.bladetetra.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import se.mickelus.tetra.data.DataManager;
import se.mickelus.tetra.module.schematic.ConfigSchematic;

import java.util.Map;

/** Opt-in, single-world Tetra integration smoke: does not run in player worlds. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class NamedIngotSmokeTest {
    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.namedIngotSmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            LegacyImprintKind source = NamedLegacyCatalog.values().stream().findFirst().orElseThrow();
            verifyExtraction(player);
            ItemStack ingot = NamedBladeIngotItem.create(source);
            ItemStack stack = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            String oldGrip = stack.getOrCreateTag().getString("slashblade/tsuka");
            for (String part : new String[]{"blade", "saya", "tsuba"}) {
                String slot = "slashblade/" + part;
                String path = "slashblade/legacy_ingot_" + part;
                var definition = DataManager.instance.schematicData.getData(
                        new ResourceLocation("tetra", path));
                if (definition == null) throw new IllegalStateException("Missing " + path);
                var schematic = new ConfigSchematic(definition);
                ItemStack[] materials = {ingot.copy()};
                if (!schematic.isMaterialsValid(stack, slot, materials)) {
                    throw new IllegalStateException("Ingot rejected at " + slot);
                }
                stack = schematic.applyUpgrade(stack, materials, false, slot, player);
                new LegacyImprintCraftingOutcome().apply(
                        new ResourceLocation[0], stack, slot, true, player,
                        materials, Map.of(), player.level(), schematic,
                        player.blockPosition(), player.level().getBlockState(player.blockPosition()),
                        true, new ItemStack[]{ItemStack.EMPTY});
                if (!source.id().equals(NamedLegacyImprintStorage.sourceId(stack, part))) {
                    throw new IllegalStateException("Wrong ingot source at " + slot);
                }
            }
            if (!source.equals(NamedLegacyParts.fromStack(stack).completeSet())) {
                throw new IllegalStateException("Completed saya + composite hilt lost its source");
            }
            if (!oldGrip.equals(stack.getOrCreateTag().getString("slashblade/tsuka"))) {
                throw new IllegalStateException("Ingot changed original Tetra grip");
            }
            LogUtils.getLogger().info("NAMED_INGOT_SMOKE_PASS: three Tetra upgrades with {}", source.id());
        } catch (Exception e) {
            LogUtils.getLogger().error("NAMED_INGOT_SMOKE_FAIL", e);
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void verifyExtraction(Player player) {
        AbstractContainerMenu dummyMenu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }
            @Override public boolean stillValid(Player player) { return true; }
            @Override public void slotsChanged(net.minecraft.world.Container container) {}
        };
        TransientCraftingContainer grid = new TransientCraftingContainer(dummyMenu, 3, 3);
        ItemStack wooden = new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("slashblade", "slashblade_wood")));
        grid.setItem(4, wooden);
        for (int i = 0; i < 9; i++) if (i != 4) grid.setItem(i,
                new ItemStack(ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation("slashblade", "proudsoul_tiny"))));
        NamedBladeIngotRecipe recipe = new NamedBladeIngotRecipe(
                new ResourceLocation(BladeTetra.MOD_ID, "named_blade_ingot"),
                CraftingBookCategory.EQUIPMENT);
        if (!recipe.matches(grid, player.level())) {
            throw new IllegalStateException("3x3 wooden puppet extraction unavailable");
        }
        ItemStack result = recipe.assemble(grid, player.level().registryAccess());
        if (!LegacyImprintKind.WOODEN_PUPPET.id().equals(
                NamedBladeIngotItem.sourceId(result))) {
            throw new IllegalStateException("Extraction produced wrong source");
        }
        int before = wooden.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getDamage()).orElse(-1);
        ItemStack returned = recipe.getRemainingItems(grid).get(4);
        int after = returned.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getDamage()).orElse(-1);
        if (returned.isEmpty() || before < 0 || after <= before) {
            throw new IllegalStateException("Source blade not safely returned with wear");
        }
        grid.setItem(0, new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("slashblade", "proudsoul"))));
        if (recipe.matches(grid, player.level())) {
            throw new IllegalStateException("Wrong Proud Soul variant accepted");
        }
    }

    private NamedIngotSmokeTest() {}
}
