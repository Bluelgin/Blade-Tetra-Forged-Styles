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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
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
import se.mickelus.tetra.blocks.workbench.WorkbenchTile;
import se.mickelus.tetra.blocks.workbench.gui.WorkbenchScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Disposable integrated world and native framebuffer captures for this feature. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class SaltyFishReview {
    private static final BlockPos BENCH = new BlockPos(11, -60, 0);
    private static CompletableFuture<SaltyFishSmoke.Samples> prepared;
    private static SaltyFishSmoke.Samples samples;
    private static boolean starting;
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
        if (prepared == null) {
            if (mc.screen != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            mc.options.guiScale().set(3);
            mc.options.renderDistance().set(5);
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
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
            case 1 -> camera(mc, 0.5, -59.2, 7.0, 51, 180, 0);
            case 32 -> screenshot = "01-three-lengths";
            case 40 -> camera(mc, 0.5, -59.2, 2.9, 35, 180, 0);
            case 62 -> screenshot = "02-standard-sheathed";
            case 70 -> camera(mc, -0.48, -59.15, 1.3, 31, 164, 1);
            case 92 -> screenshot = "03-standard-guard";
            case 100 -> camera(mc, 2.50, -59.15, 1.3, 31, 164, 1);
            case 122 -> screenshot = "04-second-guard";
            case 130 -> camera(mc, 7.45, -59.2, 3.0, 37, 180, 0);
            case 152 -> screenshot = "05-mixed-length";
            case 160 -> {
                mc.options.fov().set(46);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.standardBlade().copy());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                    player.connection.teleport(-6, -60, 2, 20, 8);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                });
            }
            case 185 -> drawBlade(mc);
            case 189, 193, 197 -> screenshot = "06-standard-drawn-" + (tick - 185);
            case 228 -> screenshot = "07-standard-resheathed";
            case 236 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                camera(mc, 11.5, -59.0, 2.9, 42, 180, 22);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.scroll().copy());
                    player.inventoryMenu.broadcastChanges();
                });
            }
            case 263 -> screenshot = "08-scroll-unfolded-above-workbench";
            case 272 -> {
                mc.options.hideGui = false;
                onServer(mc, player -> NetworkHooks.openScreen(player,
                        (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH), BENCH));
            }
            case 315 -> selectSlot(mc, ModularSlashBladeItem.BLADE_SLOT);
            case 335 -> screenshot = "09-workbench-fishbone";
            case 345 -> selectSlot(mc, ModularSlashBladeItem.SAYA_SLOT);
            case 365 -> screenshot = "10-workbench-fish-saya";
            case 385 -> {
                if (captures != 12) throw new IllegalStateException("Expected 12 captures, got " + captures);
                LogUtils.getLogger().info("SALTY_FISH_REVIEW_PASS: native scroll, six upgrades, save round trip, seeded fishing and {} framebuffer captures", captures);
                mc.stop();
            }
            default -> { }
        }
    }

    private static void openWorld(Minecraft mc) {
        String id = "salty-fish-review";
        if (Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(id).resolve("level.dat"))) {
            mc.createWorldOpenFlows().loadLevel(mc.screen, id);
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
            var standItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("slashblade", "bladestand_1w"));
            if (standItem == null) throw new IllegalStateException("Native SlashBlade stand unavailable");
            for (int x = -5; x <= 9; x++) {
                for (int y = -60; y <= -57; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, -1), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
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

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void drawBlade(Minecraft mc) {
        // Drive the existing SlashBlade motion on the logical server and local renderer.
        var combo = ComboStateRegistry.UPPERSLASH.getId();
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
