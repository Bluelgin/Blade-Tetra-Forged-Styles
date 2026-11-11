package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.item.StarterBladeSmoke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.blocks.workbench.WorkbenchTile;
import se.mickelus.tetra.blocks.workbench.gui.WorkbenchScreen;
import se.mickelus.tetra.items.modular.impl.ModularDoubleHeadedItem;
import se.mickelus.tetra.module.schematic.RemoveSchematic;

import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static dev.bladetetra.item.ModularSlashBladeItem.*;

@Mod.EventBusSubscriber(modid = "blade_tetra", value = Dist.CLIENT)
public final class StarterBladeReview {
    private static final BlockPos BENCH = new BlockPos(0, -60, 0);
    private static final BlockPos TABLE = new BlockPos(1, -60, 0);
    private static final BlockPos CHEST = new BlockPos(2, -60, 0);
    private static boolean opened, reopening;
    private static String worldName;
    private static boolean jeiRecipesVisible;
    private static CompletableFuture<Void> task;
    private static int tick, age, captures;
    private static String screenshot;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("blade_tetra.starterBladeReview")) return;
        var mc = Minecraft.getInstance();
        if (++age > 3600) throw new IllegalStateException("Starter review timed out");
        if (!opened && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            opened = true;
            worldName = "starter-review-" + System.currentTimeMillis();
            mc.createWorldOpenFlows().createFreshLevel(worldName,
                    new LevelSettings(worldName, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(158L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (task != null) {
            if (!task.isDone()) return;
            task.join();
            task = null;
        }
        if (reopening) {
            if (mc.screen == null) {
                reopening = false;
                onServer(mc, StarterBladeReview::verifySavedSlots);
            }
            return;
        }
        if (tick == 31 && (mc.screen != null || !(mc.level.getBlockEntity(BENCH) instanceof WorkbenchTile))) return;
        switch (++tick) {
            case 1 -> {
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(3);
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.resizeDisplay();
                onServer(mc, StarterBladeReview::prepareWorkbench);
            }
            case 32 -> onServer(mc, player -> NetworkHooks.openScreen(player,
                    (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH), BENCH));
            case 58 -> captureWorkbench(mc, "01-wooden-orthodox");
            case 64 -> onServer(mc, player -> {
                try {
                    var bench = (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH);
                    var inventory = inventory(bench);
                    var schematic = StarterBladeSmoke.schematic("orthodox_blade");
                    inventory.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 64));
                    bench.setCurrentSchematic(schematic, BLADE_SLOT);
                    bench.craft(player);
                    var result = bench.getTargetItemStack();
                    if (!"orthodox_blade/iron".equals(result.getTag().getString(ORTHODOX_BLADE_MODULE + "_material"))) {
                        throw new IllegalStateException("Native workbench did not install iron");
                    }
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            case 87 -> captureWorkbench(mc, "02-upgraded-iron-blade");
            case 93 -> onServer(mc, player -> {
                var bench = (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH);
                bench.setCurrentSchematic(new RemoveSchematic(), TSUBA_SLOT);
                bench.craft(player);
                var result = bench.getTargetItemStack();
                verifyEmptyGuard(result);
                player.getInventory().setItem(7, result.copy());
                player.getEnderChestInventory().setItem(0, result.copy());
                var level = player.serverLevel();
                level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity) level.getBlockEntity(CHEST)).setItem(0, result.copy());
            });
            case 116 -> captureWorkbench(mc, "03-after-native-remove");
            case 122 -> onServer(mc, StarterBladeReview::openIronRecipe);
            case 145 -> {
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen)) {
                    throw new IllegalStateException("Native crafting screen unavailable");
                }
                screenshot = "04-original-iron-recipe";
            }
            case 163 -> {
                if (captures != 4) throw new IllegalStateException("Expected four screenshots, got " + captures);
                if (!jeiRecipesVisible) throw new IllegalStateException("JEI did not list both starter recipes");
                reopening = true;
                mc.player.closeContainer();
                mc.level.disconnect();
                mc.clearLevel(new TitleScreen());
                reopenWorld(mc);
            }
            case 164 -> {
                LogUtils.getLogger().info("BLADE_STARTER_REVIEW_PASS: native workbench, JEI recipes and saved inventory, chest and ender chest");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void reopenWorld(Minecraft mc) {
        try {
            // The disposable fixture uses custom dimensions. Continue through the native loader
            // without the interactive backup prompt.
            var load = WorldOpenFlows.class.getDeclaredMethod("doLoadLevel",
                    Screen.class, String.class, boolean.class, boolean.class);
            load.setAccessible(true);
            load.invoke(mc.createWorldOpenFlows(), mc.screen, worldName, false, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not reopen starter review world", failure);
        }
    }

    private static void verifySavedSlots(ServerPlayer player) {
        verifyEmptyGuard(player.getInventory().getItem(7));
        verifyEmptyGuard(player.getEnderChestInventory().getItem(0));
        var chest = (ChestBlockEntity) player.serverLevel().getBlockEntity(CHEST);
        verifyEmptyGuard(chest.getItem(0));
    }

    private static void verifyEmptyGuard(ItemStack result) {
        if (!result.hasTag() || result.getTag().contains(TSUBA_SLOT)
                || !"orthodox_blade/iron".equals(result.getTag().getString(ORTHODOX_BLADE_MODULE + "_material"))) {
            throw new IllegalStateException("Native workbench result lost its iron blade or empty guard");
        }
    }

    @JeiPlugin
    public static final class JeiCheck implements IModPlugin {
        @Override
        public ResourceLocation getPluginUid() {
            return ResourceLocation.fromNamespaceAndPath("blade_tetra", "starter_review");
        }

        @Override
        public void onRuntimeAvailable(IJeiRuntime runtime) {
            if (!Boolean.getBoolean("blade_tetra.starterBladeReview")) return;
            var starterIds = Set.of(ResourceLocation.fromNamespaceAndPath("blade_tetra", "wooden_orthodox"),
                    ResourceLocation.fromNamespaceAndPath("blade_tetra", "iron_katana"));
            var displayed = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                    .map(recipe -> recipe.getId()).filter(starterIds::contains).collect(Collectors.toSet());
            jeiRecipesVisible = displayed.equals(starterIds);
        }
    }

    private static void prepareWorkbench(ServerPlayer player) {
        var level = player.serverLevel();
        player.connection.teleport(.5, -60, 2, 180, 0);
        player.getInventory().setItem(0, ModularDoubleHeadedItem.setupHammerStack("iron", "spruce"));
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("tetra", "basic_workbench"));
        level.setBlockAndUpdate(BENCH, block.defaultBlockState());
        var bench = (WorkbenchTile) level.getBlockEntity(BENCH);
        inventory(bench).setStackInSlot(0, StarterBladeSmoke.craft(level, "wooden_orthodox", Items.OAK_PLANKS, 2));
    }

    private static IItemHandlerModifiable inventory(WorkbenchTile bench) {
        return (IItemHandlerModifiable) bench.getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(IllegalStateException::new);
    }

    private static void openIronRecipe(ServerPlayer player) {
        var level = player.serverLevel();
        level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
        player.openMenu(new SimpleMenuProvider((id, inventory, user) ->
                new CraftingMenu(id, inventory, ContainerLevelAccess.create(level, TABLE)),
                net.minecraft.network.chat.Component.translatable("container.crafting")));
        var menu = player.containerMenu;
        var puppet = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("slashblade", "slashblade_wood"));
        menu.getSlot(1).set(new ItemStack(puppet));
        for (int i = 2; i <= 4; i++) menu.getSlot(i).set(new ItemStack(Items.IRON_INGOT));
        if (menu.getSlot(0).getItem().isEmpty()) throw new IllegalStateException("Iron recipe did not resolve");
        menu.broadcastChanges();
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> operation) {
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        task = server.submit(() -> operation.accept(server.getPlayerList().getPlayer(playerId)));
    }

    private static void captureWorkbench(Minecraft mc, String name) {
        if (!(mc.screen instanceof WorkbenchScreen)) throw new IllegalStateException("Native workbench screen unavailable: " + mc.screen);
        screenshot = name;
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END || screenshot == null) return;
        var mc = Minecraft.getInstance();
        var directory = mc.gameDirectory.toPath().resolve("evidence");
        Files.createDirectories(directory);
        try (var frame = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            frame.writeToFile(directory.resolve(screenshot + ".png"));
        }
        screenshot = null;
        captures++;
    }

    private StarterBladeReview() {}
}
