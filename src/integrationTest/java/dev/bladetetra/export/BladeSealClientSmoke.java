package dev.bladetetra.export;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.forging.NamedBackplate;
import dev.bladetetra.forging.NamedLegacyImprintStorage;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/** Opt-in real source-client path: named fitting -> custom PNG -> generated portable JAR. */
@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID, value = Dist.CLIENT)
public final class BladeSealClientSmoke {
    private static boolean worldStarted, opened;
    private static Set<String> before;
    private static int waited, ticks;
    private static boolean openedStudio;
    private static int studioFrames;
    private static String outputVersion;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!enabled() || opened || event.phase != TickEvent.Phase.END) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null || player.tickCount < 30) return;
        opened = true;
        event.getServer().getPlayerList().op(player.getGameProfile());
        player.setGameMode(GameType.CREATIVE);
        ItemStack blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        Map<String, LegacyImprintKind> sources = Map.of("blade", LegacyImprintKind.BLACK_FOX,
                "saya", LegacyImprintKind.WOODEN_PUPPET, "tsuba", LegacyImprintKind.WHITE_FOX);
        for (var part : sources.entrySet()) {
            var tag = blade.getOrCreateTag();
            String module = tag.getString("slashblade/" + part.getKey());
            tag.putString(module + "_material", NamedLegacyImprintStorage.genericVariant(part.getKey()));
            NamedLegacyImprintStorage.putSource(tag, part.getKey(), part.getValue().id());
            NamedLegacyImprintStorage.putSnapshot(blade, part.getKey(), part.getValue());
        }
        blade.getOrCreateTag().putString(ModularSlashBladeItem.BACKPLATE_SLOT, NamedBackplate.MODULE);
        blade.getOrCreateTag().putString("blade_tetra_backplate_source", LegacyImprintKind.WHITE_FOX.id());
        // Also verify that the real GUI cannot export the awakened weapon until
        // the user explicitly acknowledges removal of awakening-only behavior.
        blade.getOrCreateTag().putString(ModularSlashBladeItem.INSCRIPTION_SLOT,
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
        blade.getOrCreateTag().putString(
                ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material",
                "awakened_soul_inscription/sakura_crystal");
        blade.getOrCreateTag().putBoolean(SenbonzakuraAwakening.TAG_UNLOCKED, true);
        var position = player.blockPosition().below();
        player.serverLevel().setBlockAndUpdate(position, BladeSealWorkbench.BLOCK.get().defaultBlockState());
        BladeSealWorkbench.open(player, position);
        if (!(player.containerMenu instanceof BladeSealWorkbench.WorkbenchMenu workbench)) {
            throw new IllegalStateException("Blade Sealing Workbench did not open");
        }
        workbench.getSlot(0).set(blade);
        workbench.broadcastChanges();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (++ticks > 3500) throw new IllegalStateException("Named Blade Seal client smoke timed out");
        if (!worldStarted && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            worldStarted = true;
            String name = "blade-seal-client-smoke";
            var dir = client.gameDirectory.toPath().resolve("saves/" + name + "/level.dat");
            if (Files.exists(dir)) client.createWorldOpenFlows().loadLevel(client.screen, name);
            else client.createWorldOpenFlows().createFreshLevel(name,
                    new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(158L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
        if (!openedStudio && client.screen instanceof BladeSealWorkbenchScreen workbench
                && !workbench.getMenu().blade().isEmpty()) {
            openedStudio = true;
            client.gameMode.handleInventoryButtonClick(workbench.getMenu().containerId,
                    BladeSealWorkbench.WorkbenchMenu.OPEN_STUDIO);
        }
        if (!(client.screen instanceof BladeSealScreen screen)) return;
        if (++studioFrames < 5) return;
        Path directory = client.gameDirectory.toPath().resolve("blade-exports");
        try {
            Files.createDirectories(directory);
            if (before == null) {
                try (var files = Files.list(directory)) {
                    before = files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                }
                // Test an actually user-provided transparent image, not an image bundled in either mod.
                Path picture = client.gameDirectory.toPath().resolve("custom-sealed-portrait.png");
                var image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    image.setRGB(x, y, (x + y) % 5 == 0 ? 0 : 0xE0BC3255);
                if (!ImageIO.write(image, "png", picture.toFile()))
                    throw new IllegalStateException("No PNG encoder available");
                var mode = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getMessage().equals(
                                Component.translatable("blade_tetra.export.backplate.original")))
                        .findFirst().orElseThrow();
                mode.onPress(); // original -> user-image replacement
                var field = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .filter(box -> box.getMessage().equals(Component.translatable("blade_tetra.export.backplate_file")))
                        .findFirst().orElseThrow();
                field.setValue(picture.toAbsolutePath().toString());
                findField(screen, "blade_tetra.export.logo").setValue(picture.toAbsolutePath().toString());
                findField(screen, "blade_tetra.export.lore").setValue("Forged for the archived-sword client test.");
                findField(screen, "blade_tetra.export.chinese_name").setValue("封存实机验收刀");
                outputVersion = findField(screen, "blade_tetra.export.version").getValue();
                try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    screenshot.writeToFile(client.gameDirectory.toPath().resolve("blade-studio-client.png"));
                }
                Button consent = screen.children().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> button.getMessage().equals(
                                Component.translatable("blade_tetra.export.awakening.acknowledge")))
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "Awakened source must display an explicit loss-acknowledgement control"));
                Button export = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(button -> button.getMessage().equals(
                                Component.translatable("blade_tetra.export.confirm")))
                        .findFirst().orElseThrow();
                if (export.active) {
                    throw new IllegalStateException("Awakened export was enabled before opt-in");
                }
                consent.onPress();
                if (!export.active) {
                    throw new IllegalStateException("Acknowledging awakening omission did not enable export");
                }
                export.onPress();
                return;
            }
            if (++waited % 10 != 0) return;
            try (var files = Files.list(directory)) {
                var produced = files.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("bt_export_blade_") && name.contains("-" + outputVersion + "-") && name.endsWith(".jar") && !before.contains(name);
                }).findFirst();
                if (produced.isPresent()) {
                    Path file = produced.get();
                    String modId = file.getFileName().toString().split("-", 2)[0];
                    verifyNamedPortraitArchive(file, modId);
                    LogUtils.getLogger().info("BLADE_SEAL_NAMED_CLIENT_PASS: {}", file.toAbsolutePath());
                    client.stop();
                } else if (waited >= 500) {
                    throw new IllegalStateException("Named seal did not publish a standalone add-on. Studio status: "
                            + (screen.exportStatus() == null ? "(none)" : screen.exportStatus().getString()));
                }
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("BLADE_SEAL_NAMED_CLIENT_FAIL", error);
            throw new IllegalStateException(error);
        }
    }

    private static EditBox findField(BladeSealScreen screen, String key) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().equals(Component.translatable(key))).findFirst().orElseThrow();
    }

    private static void verifyNamedPortraitArchive(Path archive, String id) throws Exception {
        try (var zip = new ZipFile(archive.toFile())) {
            if (zip.getEntry("blade-studio-logo.png") == null
                    || zip.getEntry("data/" + id + "/recipes/sealed_blade.json") != null) {
                throw new IllegalStateException("Independent add-on omitted its logo or exports a survival recipe");
            }
            var chinese = zip.getEntry("assets/" + id + "/lang/zh_cn.json");
            try (var input = zip.getInputStream(chinese)) {
                var locale = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"封存实机验收刀".equals(locale.get("item." + id + ".sealed_blade").getAsString())) {
                    throw new IllegalStateException("Translated blade name did not survive packaging");
                }
            }
            String prefix = "assets/" + id + "/model/";
            var model = zip.getEntry(prefix + "sealed_blade.obj");
            var atlas = zip.getEntry(prefix + "sealed_blade.png");
            var portrait = zip.getEntry(prefix + "sealed_backplate.png");
            var frame = zip.getEntry(prefix + "sealed_backplate_frame.obj");
            if (model == null || atlas == null || portrait == null || frame == null)
                throw new IllegalStateException("Named export omitted model/atlas/portrait/frame");
            try (var source = zip.getInputStream(model)) {
                var obj = new WavefrontObject(id, source);
                var groups = obj.groupObjects.stream().map(group -> group.name).collect(Collectors.toSet());
                for (var required : Set.of("blade", "sheath", "item_blade", "effect"))
                    if (!groups.contains(required)) throw new IllegalStateException("Missing output group " + required);
                for (var group : obj.groupObjects) for (var face : group.faces) {
                    if (face.vertices == null || face.textureCoordinates == null)
                        throw new IllegalStateException("Unrenderable exported face in " + group.name);
                    for (var uv : face.textureCoordinates) {
                        if (uv == null || !Float.isFinite(uv.u) || !Float.isFinite(uv.v)
                                || uv.u < 0 || uv.u > 1 || uv.v < 0 || uv.v > 1)
                            throw new IllegalStateException("Output atlas UV escaped its tile: " + group.name);
                    }
                }
            }
            try (var image = zip.getInputStream(atlas)) {
                var png = ImageIO.read(image);
                if (png == null || png.getWidth() <= 256 || png.getHeight() < 512)
                    throw new IllegalStateException("Named source atlases not combined");
            }
            try (var picture = zip.getInputStream(portrait)) {
                var png = ImageIO.read(picture);
                if (png == null || png.getWidth() != 256 || png.getHeight() != 256)
                    throw new IllegalStateException("Custom portrait was not normalized");
            }
            var signature = zip.getEntry("blade-export.json");
            if (signature == null) throw new IllegalStateException("Missing sealed certificate");
            var certificate = JsonParser.parseString(new String(zip.getInputStream(signature).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (certificate.getAsJsonArray("namedFittings").size() != 4)
                throw new IllegalStateException("Lost original named fitting provenance");
            String contract = new String(zip.getInputStream(zip.getEntry(
                    "data/" + id + "/blade_export/sealed_blade.json")).readAllBytes(),
                    StandardCharsets.UTF_8);
            if (contract.contains("SENBONZAKURA") || contract.contains("sakura_crystal")
                    || contract.contains("awakened_soul")) {
                throw new IllegalStateException("Awakened-specific content leaked into export contract");
            }
            var hashes = certificate.getAsJsonObject("sha256");
            for (var output : Set.of(prefix + "sealed_blade.obj", prefix + "sealed_blade.png",
                    prefix + "sealed_backplate.png", prefix + "sealed_backplate_frame.obj")) {
                if (!hashes.has(output)) throw new IllegalStateException("Unsigned export appearance " + output);
                String digest = BladeExportArchive.hash(zip.getInputStream(zip.getEntry(output)).readAllBytes());
                if (!hashes.get(output).getAsString().equals(digest))
                    throw new IllegalStateException("Exported resource hash mismatch: " + output);
            }
        }
    }

    private static boolean enabled() {
        return Boolean.getBoolean("blade_tetra.exportSealClientSmoke");
    }

    private BladeSealClientSmoke() {}
}
