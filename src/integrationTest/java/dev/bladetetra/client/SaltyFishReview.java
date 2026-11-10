package dev.bladetetra.client;

import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.SaltyFishSmoke;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.entity.BladeStandEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
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
    private static final BlockPos BENCH = new BlockPos(6, -60, 0);
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
        if (samples == null) samples = prepared.join();
        switch (++tick) {
            case 1 -> camera(mc, 0.5, -59.3, 6.0, 48);
            case 32 -> screenshot = "01-three-lengths";
            case 40 -> camera(mc, 0.5, -59.3, 3.7, 35);
            case 62 -> screenshot = "02-standard-sheathed";
            case 70 -> camera(mc, 0.3, -59.3, 2.5, 30);
            case 92 -> screenshot = "03-standard-guard";
            case 100 -> camera(mc, 2.8, -59.3, 2.5, 30);
            case 122 -> screenshot = "04-second-guard";
            case 130 -> camera(mc, 5.0, -59.3, 3.5, 39);
            case 152 -> screenshot = "05-mixed-length";
            case 160 -> {
                mc.options.fov().set(58);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.standardBlade().copy());
                    player.getInventory().selected = 0;
                    player.inventoryMenu.broadcastChanges();
                    player.connection.teleport(-4, -60, 1, -35, 8);
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                });
            }
            case 185 -> KeyMapping.click(mc.options.keyAttack.getKey());
            case 189, 193, 197 -> screenshot = "06-standard-drawn-" + (tick - 185);
            case 228 -> screenshot = "07-standard-resheathed";
            case 236 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                camera(mc, 6.5, -61.2, 3.1, 46);
                onServer(mc, player -> {
                    player.getInventory().setItem(0, samples.scroll().copy());
                    player.inventoryMenu.broadcastChanges();
                });
            }
            case 263 -> screenshot = "08-scroll-unfolded-above-workbench";
            case 272 -> {
                mc.options.hideGui = false;
                onServer(mc, player -> player.openMenu(
                        (WorkbenchTile) player.serverLevel().getBlockEntity(BENCH)));
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
            var samples = SaltyFishSmoke.verify(level, player, BENCH);
            var bench = (WorkbenchTile) level.getBlockEntity(BENCH);
            var inventory = (IItemHandlerModifiable) bench.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .orElseThrow(IllegalStateException::new);
            inventory.setStackInSlot(0, ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
            var standItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("slashblade", "bladestand_1w"));
            if (standItem == null) throw new IllegalStateException("Native SlashBlade stand unavailable");
            for (int x = -3; x <= 5; x++) {
                for (int y = -60; y <= -57; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, -1), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                }
            }
            ItemStack[] blades = {samples.shortBlade(), samples.standardBlade(), samples.longBlade(), samples.mixedBlade()};
            for (int i = 0; i < blades.length; i++) {
                var stand = BladeStandEntity.createInstanceFromPos(level,
                        new BlockPos(-2 + i * 2, -58, 0), Direction.SOUTH, standItem);
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

    private static void camera(Minecraft mc, double x, double y, double z, int fov) {
        mc.options.fov().set(fov);
        onServer(mc, player -> {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(x, y, z, 180, 0);
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
