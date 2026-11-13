package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.SaltyFishSmoke;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.item.SaltyFishParts;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
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
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.workbench.WorkbenchTile;
import se.mickelus.tetra.blocks.workbench.gui.WorkbenchScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Disposable integrated world and native framebuffer captures for this feature. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SaltyFishReview {
    private static final BlockPos BENCH = new BlockPos(11, -60, 0);
    private static CompletableFuture<SaltyFishSmoke.Samples> prepared;
    private static SaltyFishSmoke.Samples samples;
    private static boolean starting, rejoin;
    private static CompletableFuture<Boolean> reloaded;
    private static int age, tick;
    private static String screenshot;
    private static int captures;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("blade_tetra.saltyFishReview")) return;
        Minecraft mc = Minecraft.getInstance();
        if (++age > 4000) throw new IllegalStateException("Salty Fish review timed out");
        if (!starting && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            starting = true;
            openWorld(mc);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (rejoin) {
            verifyRejoin(mc);
            return;
        }
        if (prepared == null) {
            if (mc.screen != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(5);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.resizeDisplay();
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            prepared = server.submit(() -> prepare(server.getPlayerList().getPlayer(playerId)));
            return;
        }
        if (!prepared.isDone()) return;
        if (samples == null) {
            samples = prepared.join();
            for (var stack : new ItemStack[]{samples.standardBlade(), samples.longBlade()}) {
                var guard = SaltyFishParts.salvagedGuard(stack);
                if (guard == null || !guard.visualUsable()) {
                    throw new IllegalStateException("Review guard is missing compatible geometry: " + guard);
                }
                LogUtils.getLogger().info("SALTY_FISH_REVIEW_GUARD: {}", guard.id());
            }
        }
        switch (++tick) {
            case 1 -> camera(mc, 0.5, -59.2, 6.2, 47, 180, 0);
            case 32 -> screenshot = "01-three-lengths";
            case 40 -> camera(mc, 0.5, -59.2, 2.9, 35, 180, 0);
            case 62 -> screenshot = "02-standard-sheathed";
            case 70 -> camera(mc, 0.12, -59.15, 1.7, 35, 172, 1);
            case 92 -> screenshot = "03-standard-guard";
            case 100 -> camera(mc, 3.12, -59.15, 1.7, 35, 172, 1);
            case 122 -> screenshot = "04-second-guard";
            case 130 -> camera(mc, 7.45, -59.2, 3.0, 37, 180, 0);
            case 152 -> screenshot = "05-mixed-length";
            case 160 -> {
                mc.options.fov().set(65);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.standardBlade().copy());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                    player.connection.teleport(-6, -60, 2, 20, 20);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                });
            }
            case 185 -> drawBlade(mc, ComboStateRegistry.COMBO_A1.getId());
            case 188, 192, 195 -> screenshot = "06-standard-drawn-sweep-" + (tick - 185);
            case 215 -> drawBlade(mc, ComboStateRegistry.UPPERSLASH.getId());
            case 219, 223, 227 -> screenshot = "06-standard-drawn-upper-" + (tick - 215);
            case 295 -> screenshot = "07-standard-resheathed";
            case 305 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                camera(mc, 11.5, -59.0, 2.9, 42, 180, 22);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.scroll().copy());
                    player.inventoryMenu.broadcastChanges();
                });
            }
            case 332 -> screenshot = "08-scroll-unfolded-above-workbench";
            case 340 -> {
                mc.options.hideGui = false;
                onServer(mc, player -> NetworkHooks.openScreen(player,
                        (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH), BENCH));
            }
            case 378 -> selectSlot(mc, ModularSlashBladeItem.BLADE_SLOT);
            case 398 -> screenshot = "09-workbench-fishbone";
            case 408 -> selectSlot(mc, ModularSlashBladeItem.SAYA_SLOT);
            case 428 -> screenshot = "10-workbench-fish-saya";
            case 450 -> {
                mc.player.closeContainer();
                mc.options.hideGui = true;
                camera(mc, 0.5, -59.2, 2.9, 35, 180, 0);
                onServer(mc, player -> {
                    var stand = standardStand(player);
                    var blade = stand.getItem().copy();
                    blade.enchant(Enchantments.FIRE_ASPECT, 2);
                    stand.setItem(blade);
                });
            }
            case 480 -> screenshot = "11-fire-aspect-cooked-sheath";
            case 487 -> onServer(mc, player -> {
                var stand = standardStand(player);
                var blade = stand.getItem().copy();
                EnchantmentHelper.setEnchantments(Map.of(), blade);
                stand.setItem(blade);
            });
            case 517 -> screenshot = "12-fire-aspect-removed";
            case 535 -> {
                if (captures != 17) throw new IllegalStateException("Expected 17 captures, got " + captures);
                rejoin = true;
                starting = false;
                mc.level.disconnect();
                mc.clearLevel(new TitleScreen());
            }
            default -> { }
        }
        if (screenshot != null) {
            mc.getToasts().clear();
            mc.gui.getChat().clearMessages(true);
        }
    }

    private static void verifyRejoin(Minecraft mc) {
        if (mc.screen != null) return;
        if (reloaded == null) {
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            reloaded = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var saved = player.getInventory().getItem(7);
                var expected = samples.caughtBlade();
                var guard = SaltyFishParts.salvagedGuard(saved);
                var bench = (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH);
                return saved.is(ModItems.MODULAR_SLASHBLADE.get())
                        && SaltyFishParts.blade(saved) == SaltyFishParts.blade(expected)
                        && SaltyFishParts.saya(saved) == SaltyFishParts.saya(expected)
                        && saved.getDamageValue() == expected.getDamageValue()
                        && guard != null && guard.equals(SaltyFishParts.salvagedGuard(expected))
                        && bench != null && Arrays.asList(bench.getUnlockedSchematics())
                                .containsAll(ScrollData.read(samples.scroll()).schematics);
            });
            return;
        }
        if (reloaded.isDone()) {
            if (!reloaded.join()) throw new IllegalStateException("Fishing construction or scroll lost on rejoin");
            LogUtils.getLogger().info("SALTY_FISH_REVIEW_PASS: native scroll switch, four styles and adaptive sheath, saved fishing blade and workbench after world rejoin, {} framebuffer captures", captures);
            mc.stop();
        }
    }

    private static void openWorld(Minecraft mc) {
        String id = "salty-fish-review";
        if (Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(id).resolve("level.dat"))) {
            // This disposable world has experimental dimensions. Skip only its backup
            // prompt so the same native loader can reopen it without a human click.
            try {
                var load = WorldOpenFlows.class.getDeclaredMethod("doLoadLevel",
                        Screen.class, String.class, boolean.class, boolean.class);
                load.setAccessible(true);
                load.invoke(mc.createWorldOpenFlows(), mc.screen, id, false, false);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not reopen the disposable review world", failure);
            }
            return;
        }
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel(id,
                new LevelSettings(id, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(158L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static SaltyFishSmoke.Samples prepare(ServerPlayer player) {
        try {
            var level = player.serverLevel();
            level.setDayTime(6000);
            level.removeBlock(BENCH.above(), false);
            var samples = SaltyFishSmoke.verify(level, player, BENCH);
            var bench = (WorkbenchTile) level.getBlockEntity(BENCH);
            var inventory = (IItemHandlerModifiable) bench.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            inventory.setStackInSlot(0, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
            player.getInventory().setItem(7, samples.caughtBlade().copy());
            var standItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("slashblade", "bladestand_1w"));
            if (standItem == null) throw new IllegalStateException("Native SlashBlade stand unavailable");
            for (int x = -5; x <= 9; x++) {
                for (int y = -60; y <= -57; y++) {
                    var backdrop = x >= 6 ? Blocks.GRAY_TERRACOTTA : Blocks.SMOOTH_SANDSTONE;
                    level.setBlockAndUpdate(new BlockPos(x, y, -1), backdrop.defaultBlockState());
                }
            }
            level.getEntitiesOfClass(BladeStandEntity.class,
                    new net.minecraft.world.phys.AABB(-5, -60, -1, 10, -55, 2)).forEach(stand -> stand.discard());
            ItemStack[] blades = {samples.shortBlade(), samples.standardBlade(), samples.longBlade(), samples.mixedBlade()};
            int[] positions = {-3, 0, 3, 7};
            for (int i = 0; i < blades.length; i++) {
                var stand = BladeStandEntity.createInstanceFromPos(level,
                        new BlockPos(positions[i], -58, 0), Direction.SOUTH, standItem);
                stand.setItem(blades[i].copy());
                level.addFreshEntity(stand);
            }
            return samples;
        } catch (Exception failure) {
            throw new IllegalStateException("Native Salty Fish verification failed", failure);
        }
    }

    private static BladeStandEntity standardStand(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(BladeStandEntity.class,
                new net.minecraft.world.phys.AABB(-1, -60, -1, 2, -55, 2)).stream().findFirst().orElseThrow();
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void drawBlade(Minecraft mc, ResourceLocation combo) {
        // Drive the existing SlashBlade motion on the logical server and local renderer.
        onServer(mc, player -> player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> state.updateComboSeq(player, combo)));
        mc.player.getMainHandItem().getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(state -> state.updateComboSeq(mc.player, combo));
    }

    private static void camera(Minecraft mc, double x, double y, double z, int fov, float yaw, float pitch) {
        mc.options.fov().set(fov);
        onServer(mc, player -> {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(x, y, z, yaw, pitch);
        });
    }

    private static void selectSlot(Minecraft mc, String slot) {
        if (!(mc.screen instanceof WorkbenchScreen screen)) {
            throw new IllegalStateException("Native Tetra screen did not open");
        }
        try {
            var method = WorkbenchScreen.class.getDeclaredMethod("selectSlot", String.class);
            method.setAccessible(true);
            method.invoke(screen, slot);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || screenshot == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = screenshot;
        screenshot = null;
        var directory = mc.gameDirectory.toPath().resolve("evidence");
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(name + ".png"));
            captures++;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private SaltyFishReview() {}
}
