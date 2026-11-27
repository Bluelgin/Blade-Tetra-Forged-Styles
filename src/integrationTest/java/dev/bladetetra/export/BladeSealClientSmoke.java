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
import net.minecraft.client.gui.components.MultiLineEditBox;
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
    private static int previewRenders;
    private static boolean previewVerified;
    private static boolean advancedConfigured;
    private static boolean advancedCaptured;
    private static boolean comparisonOpened;
    private static net.minecraft.client.gui.screens.ConfirmScreen renderedResourceNotice;
    private static boolean noticeCancelled;
    private static boolean retriedNotice;
    private static boolean requestedExport;
    private static String outputVersion;
    private static long inscriptionStart;
    private static final boolean[] inscriptionFrames = new boolean[5];

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
        if (client.screen instanceof BladeStudioPreviewScreen preview && preview.hasBakedModel() && previewRenders >= 2) {
            previewVerified = true;
            preview.onClose();
            return;
        }
        if (client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen notice
                && notice.getTitle().equals(Component.translatable("blade_tetra.export.resource_notice.title"))) {
            if (notice != renderedResourceNotice) return;
            var label = noticeCancelled ? Component.translatable("blade_tetra.export.resource_notice.confirm")
                    : net.minecraft.network.chat.CommonComponents.GUI_CANCEL;
            noticeCancelled = true;
            var button = notice.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(control -> control.getMessage().equals(label)).findFirst().orElseThrow();
            if (client.font.width(label) > button.getWidth() - 8) {
                throw new IllegalStateException("Resource notice button label does not fit: " + label.getString());
            }
            button.onPress();
            return;
        }
        if (client.screen instanceof BladeInscriptionScreen ceremony) {
            if (inscriptionStart == 0) inscriptionStart = net.minecraft.Util.getMillis();
            if (net.minecraft.Util.getMillis() - inscriptionStart > 3400) ceremony.onClose();
            return;
        }
        if (!(client.screen instanceof BladeSealScreen screen)) return;
        if (++studioFrames < 5 || before == null && !screen.hasInlinePreview()) return;
        Path directory = client.gameDirectory.toPath().resolve("blade-exports");
        try {
            Files.createDirectories(directory);
            if (before == null) {
                try (var files = Files.list(directory)) {
                    before = files.map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                }
                verifyStockResourceTerms(client);
                verifyFishFixtures(client);
                verifyProviderExports(client);
                SealedFusionExportSmoke.verify(client.gameDirectory.toPath(), client.getResourceManager());
                if (!findField(screen, "blade_tetra.export.name").visible
                        || !findField(screen, "blade_tetra.export.description").visible
                        || findButton(screen, "blade_tetra.export.page.appearance").visible) {
                    throw new IllegalStateException("Studio did not open on its simple page");
                }
                String defaultId = findField(screen, "blade_tetra.export.id").getValue();
                if (!defaultId.matches("blade_[a-f0-9]{32}")) {
                    throw new IllegalStateException("Invalid default Studio project ID: " + defaultId);
                }
                try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    screenshot.writeToFile(client.gameDirectory.toPath().resolve("blade-studio-simple.png"));
                }
                findField(screen, "blade_tetra.export.name").setValue("Sealed at dawn");
                findField(screen, "blade_tetra.export.description").setValue("A simple-mode draft");
                findButton(screen, "blade_tetra.export.mode.advanced").onPress();
                // Test an actually user-provided transparent image, not an image bundled in either mod.
                Path picture = client.gameDirectory.toPath().resolve("custom-sealed-portrait.png");
                var image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
                    image.setRGB(x, y, (x + y) % 5 == 0 ? 0 : 0xE0BC3255);
                if (!ImageIO.write(image, "png", picture.toFile()))
                    throw new IllegalStateException("No PNG encoder available");
                findButton(screen, "blade_tetra.export.page.appearance").onPress();
                if (!findField(screen, "blade_tetra.export.logo").visible
                        || findField(screen, "blade_tetra.export.name").visible) {
                    throw new IllegalStateException("The appearance page exposed the wrong fields");
                }
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
                findButton(screen, "blade_tetra.export.page.text").onPress();
                var story = screen.children().stream().filter(MultiLineEditBox.class::isInstance)
                        .map(MultiLineEditBox.class::cast)
                        .filter(box -> box.getMessage().equals(Component.translatable("blade_tetra.export.lore")))
                        .findFirst().orElseThrow();
                if (!story.visible || findField(screen, "blade_tetra.export.logo").visible) {
                    throw new IllegalStateException("The text page exposed the wrong fields");
                }
                story.setValue("Forged in winter.\nSealed at first light.");
                findField(screen, "blade_tetra.export.chinese_name").setValue("封存实机验收刀");
                outputVersion = findField(screen, "blade_tetra.export.version").getValue();
                advancedConfigured = true;
                return;
            }
            if (advancedCaptured && !comparisonOpened) {
                comparisonOpened = true;
                Path picture = client.gameDirectory.toPath().resolve("custom-sealed-portrait.png");
                findButton(screen, "blade_tetra.export.page.project").onPress();
                findButton(screen, "blade_tetra.export.project.save").onPress();
                String addon = findField(screen, "blade_tetra.export.id").getValue();
                var project = BladeStudioProject.load(BladeStudioProject.file(client.gameDirectory.toPath(), addon));
                if (!project.chineseName().equals("封存实机验收刀")
                        || !project.lore().equals("Forged in winter.\nSealed at first light.")
                        || !project.logoPath().equals(picture.toAbsolutePath().toString())) {
                    throw new IllegalStateException("Switching editor pages lost project fields");
                }
                findButton(screen, "blade_tetra.export.page.blade").onPress();
                findButton(screen, "blade_tetra.export.preview").onPress();
                return;
            }
            if (previewVerified && !requestedExport) {
                requestedExport = true;
                findButton(screen, "blade_tetra.export.mode.simple").onPress();
                if (!findField(screen, "blade_tetra.export.name").getValue().equals("Sealed at dawn")
                        || !findField(screen, "blade_tetra.export.description").getValue().equals("A simple-mode draft")
                        || findField(screen, "blade_tetra.export.logo").visible) {
                    throw new IllegalStateException("Switching Studio modes lost the shared draft");
                }
                LogUtils.getLogger().info("BLADE_STUDIO_SIMPLE_MODE_PASS");
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
            if (noticeCancelled && !retriedNotice) {
                try (var files = Files.list(directory)) {
                    if (files.anyMatch(path -> path.getFileName().toString().startsWith("bt_export_blade_")
                            && !before.contains(path.getFileName().toString()))) {
                        throw new IllegalStateException("Cancelling the resource notice published an add-on");
                    }
                }
                if (!findField(screen, "blade_tetra.export.name").getValue().equals("Sealed at dawn")
                        || !screen.workbenchOpen()) {
                    throw new IllegalStateException("Cancelling the resource notice lost the draft or workbench");
                }
                retriedNotice = true;
                findButton(screen, "blade_tetra.export.confirm").onPress();
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

    @SubscribeEvent
    public static void renderPreview(TickEvent.RenderTickEvent event) {
        if (!enabled() || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen notice
                && notice != renderedResourceNotice
                && notice.getTitle().equals(Component.translatable("blade_tetra.export.resource_notice.title"))) {
            if (!noticeCancelled) saveScreenshot(client, "blade-studio-material-notice.png");
            renderedResourceNotice = notice;
        }
        if (client.screen instanceof BladeInscriptionScreen && inscriptionStart != 0) {
            long elapsed = net.minecraft.Util.getMillis() - inscriptionStart;
            int phase = elapsed < 600 ? 0 : elapsed < 1400 ? 1 : elapsed < 2200 ? 2 : elapsed < 3000 ? 3 : 4;
            if (!inscriptionFrames[phase]) {
                saveScreenshot(client, "blade-inscription-" + phase + ".png");
                inscriptionFrames[phase] = true;
            }
        }
        if (previewVerified) return;
        if (advancedConfigured && !advancedCaptured && client.screen instanceof BladeSealScreen) {
            saveScreenshot(client, "blade-studio-client.png");
            advancedCaptured = true;
        }
        if (!(client.screen instanceof BladeStudioPreviewScreen preview) || !preview.hasBakedModel()) return;
        if (++previewRenders != 2) return;
        client.getToasts().clear();
        saveScreenshot(client, "blade-studio-sealed-preview.png");
        LogUtils.getLogger().info("BLADE_STUDIO_PREVIEW_PASS");
    }

    private static void saveScreenshot(Minecraft client, String filename) {
        try (var screenshot = net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            screenshot.writeToFile(client.gameDirectory.toPath().resolve(filename));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not capture " + filename, failure);
        }
    }

    private static Button findButton(BladeSealScreen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(Component.translatable(key))).findFirst().orElseThrow();
    }

    private static EditBox findField(BladeSealScreen screen, String key) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().equals(Component.translatable(key))).findFirst().orElseThrow();
    }

    private static void verifyFishFixtures(Minecraft client) throws Exception {
        var resources = client.getResourceManager();
        for (var variant : java.util.List.of(dev.bladetetra.item.SaltyFishParts.Blade.RENGEKI,
                dev.bladetetra.item.SaltyFishParts.Blade.STANDARD, dev.bladetetra.item.SaltyFishParts.Blade.DANGAKU)) {
            boolean salvageGuard = variant == dev.bladetetra.item.SaltyFishParts.Blade.STANDARD;
            var blade = dev.bladetetra.item.SaltyFishParts.create(variant, salvageGuard ? LegacyImprintKind.BLACK_FOX : null);
            var snapshot = BladeExportSnapshot.captureOwnedStack(blade);
            var metadata = new BladeExportArchive.Metadata("bt_export_salty_fish", "1.0.0",
                    "Salty Fish Ichimonji", "Blade Tetra", "Portable fishbone and saya fixture");
            var report = BladeExportPreflight.inspect(snapshot, blade);
            if (!report.ready()) throw new IllegalStateException(report.summary());
            var art = BladeExportArt.bake(metadata.modId(), snapshot, blade, resources);
            var mesh = new mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject(
                    art.model().toString(), new java.io.ByteArrayInputStream(art.assets().get(art.model())));
            var expected = dev.bladetetra.item.SaltyFishParts.blade(blade);
            float bladeTip = mesh.groupObjects.stream().filter(group -> "blade".equals(group.name))
                    .flatMap(group -> group.faces.stream()).flatMap(face -> java.util.Arrays.stream(face.vertices))
                    .map(vertex -> vertex.x).min(Float::compare).orElseThrow();
            if (Math.abs(expected.bladeTip - bladeTip) > 1.0F
                    || !ExportBattleProfile.from(snapshot).capabilities(switch (variant) {
                        case RENGEKI -> ExportBattleProfile.RENGEKI_ROOT;
                        case DANGAKU -> ExportBattleProfile.DANGAKU_ROOT;
                        default -> snapshot.combo();
                    }).contains("bt_export_runtime:salty_fish_feedback_v1")) {
                throw new IllegalStateException("Fishbone geometry or feedback missing for " + variant + ": " + bladeTip);
            }
            if (salvageGuard) {
                if (art.inputs().stream().noneMatch(input -> input.id().equals(LegacyImprintKind.BLACK_FOX.texture()))) {
                    throw new IllegalStateException("Sealed Salty Fish omitted guard provenance");
                }
                Path file = StandaloneBladeExport.write(client.gameDirectory.toPath(), resources, snapshot, metadata, art);
                try (var archive = new ZipFile(file.toFile())) {
                    String prefix = "assets/" + metadata.modId() + "/model/sealed_blade";
                    var generatedLogo = archive.getEntry("blade-studio-logo.png");
                    if (generatedLogo == null) throw new IllegalStateException("Fish archive omitted its automatic icon");
                    try (var input = archive.getInputStream(generatedLogo)) {
                        var png = ImageIO.read(input);
                        if (png == null || png.getWidth() != 64 || png.getHeight() != 64) {
                            throw new IllegalStateException("Fish archive includes an invalid automatic icon");
                        }
                    }
                    if (archive.getEntry(prefix + ".obj") == null || archive.getEntry(prefix + ".png") == null
                            || archive.getEntry("META-INF/jarjar/bt-export-runtime-"
                                + ExportBattleProfile.RUNTIME_VERSION + ".jar") == null) {
                        throw new IllegalStateException("Sealed Salty Fish is missing its baked art or runtime");
                    }
                }
            }
        }
        verifyMixedFishFits(resources, false);
        verifyMixedFishFits(resources, true);
        LogUtils.getLogger().info("BLADE_STUDIO_SALTY_FISH_EXPORT_PASS");
    }

    private static void verifyStockResourceTerms(Minecraft client) throws Exception {
        var resources = client.getResourceManager();
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var snapshot = BladeExportSnapshot.captureOwnedStack(blade);
        var modId = "bt_export_mit_fixture";
        var meta = new BladeExportArchive.Metadata(modId, "1.0.0", "MIT stock blade", "Blade Tetra", "License fixture");
        var art = BladeExportArt.bake(modId, snapshot, blade, resources);
        if (art.requiresResourceNotice(meta)) {
            throw new IllegalStateException("Stock blade unexpectedly uses external resources: "
                    + art.inputs().stream().filter(ExportResourceEvidence::externalEmbedding).toList());
        }
        var path = StandaloneBladeExport.write(client.gameDirectory.toPath(), resources, snapshot, meta, art);
        try (var archive = new ZipFile(path.toFile())) {
            if (archive.getEntry("BLADE-TETRA-ASSET-LICENSE.txt") == null
                    || archive.getEntry("THIRD-PARTY-NOTICES.txt") != null
                    || archive.getEntry("PRIVATE-MODPACK-USE.txt") != null) {
                throw new IllegalStateException("Stock export generated the wrong notice files");
            }
        }
        var asset = new net.minecraft.resources.ResourceLocation("blade_tetra", "model/modular/standard_256.png");
        var source = resources.getResourceOrThrow(asset);
        try (var input = source.open()) {
            byte[] bytes = input.readAllBytes();
            var override = new net.minecraft.server.packs.PathPackResources("blade_studio_override",
                    client.gameDirectory.toPath(), false);
            var selected = new net.minecraft.server.packs.resources.Resource(override,
                    () -> new java.io.ByteArrayInputStream(bytes));
            if (!ExportResourceEvidence.embedded(asset, selected, bytes).externalEmbedding()) {
                throw new IllegalStateException("Resource-pack override inherited the project's MIT classification");
            }
        }
        LogUtils.getLogger().info("BLADE_STUDIO_RESOURCE_TERMS_PASS");
    }

    private static void verifyProviderExports(Minecraft client) throws Exception {
        var mods = net.minecraftforge.fml.ModList.get();
        if (!mods.isLoaded("slashblade_addon") || !mods.isLoaded("yakumoblade")) return;
        var resources = client.getResourceManager();
        var sjap = java.util.Objects.requireNonNull(dev.bladetetra.forging.NamedLegacyCatalog.get("slashblade_addon/murakumo"));
        var yakumo = java.util.Objects.requireNonNull(dev.bladetetra.forging.NamedLegacyCatalog.get("yakumoblade/hisonotsurugi"));
        var green = java.util.Objects.requireNonNull(dev.bladetetra.forging.NamedLegacyCatalog.get("yakumoblade/green_inflammation"));
        for (boolean fishBlade : new boolean[]{true, false}) {
            var blade = dev.bladetetra.item.SaltyFishParts.create(dev.bladetetra.item.SaltyFishParts.Blade.STANDARD, null);
            if (fishBlade) installNamed(blade, "saya", sjap, ModularSlashBladeItem.SAYA_MODULE);
            else installNamed(blade, "blade", green, ModularSlashBladeItem.BLADE_MODULE);
            installNamed(blade, "tsuba", LegacyImprintKind.BLACK_FOX, ModularSlashBladeItem.TSUBA_MODULE);
            blade.getOrCreateTag().putString(ModularSlashBladeItem.BACKPLATE_SLOT, NamedBackplate.MODULE);
            blade.getOrCreateTag().putString("blade_tetra_backplate_source", fishBlade ? yakumo.id() : sjap.id());
            se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(blade);
            ModItems.MODULAR_SLASHBLADE.get().syncDerivedBladeState(blade);
            var snapshot = BladeExportSnapshot.captureOwnedStack(blade);
            var modId = "bt_export_mixed_fish_" + (fishBlade ? "bone" : "saya");
            var meta = new BladeExportArchive.Metadata(modId, "1.0.0", "Mixed Fish", "Blade Tetra", "Provider fixture");
            var art = BladeExportArt.bake(modId, snapshot, blade, resources);
            if (!art.requiresResourceNotice(meta) || art.inputs().stream().noneMatch(e -> e.id().equals(sjap.texture()))
                    || art.inputs().stream().noneMatch(e -> e.id().equals((fishBlade ? yakumo : green).texture()))) {
                throw new IllegalStateException("Mixed provider export lost active model or portrait provenance");
            }
            var path = StandaloneBladeExport.write(client.gameDirectory.toPath().resolve("private-provider-fixtures"),
                    resources, snapshot, meta, art);
            try (var archive = new ZipFile(path.toFile())) {
                if (archive.getEntry("RESOURCE-USAGE-NOTICE.txt") == null
                        || archive.getEntry("THIRD-PARTY-NOTICES.txt") == null
                        || archive.getEntry("PRIVATE-MODPACK-USE.txt") != null) {
                    throw new IllegalStateException("External export generated the wrong notice files");
                }
            }
        }
        LogUtils.getLogger().info("BLADE_STUDIO_EXTERNAL_MODELS_PASS");
    }

    private static void installNamed(ItemStack blade, String slot, LegacyImprintKind input, String module) {
        se.mickelus.tetra.items.modular.IModularItem.putModuleInSlot(blade, "slashblade/" + slot, module,
                module + "_material", NamedLegacyImprintStorage.genericVariant(slot));
        var tag = blade.getOrCreateTag();
        NamedLegacyImprintStorage.putSource(tag, slot, input.id());
        NamedLegacyImprintStorage.putSnapshot(blade, slot, input);
    }

    private static void verifyMixedFishFits(net.minecraft.server.packs.resources.ResourceManager resources,
                                            boolean stockBlade) throws Exception {
        var item = ModItems.MODULAR_SLASHBLADE.get();
        var stock = item.createDefaultStack();
        var mixed = dev.bladetetra.item.SaltyFishParts.create(dev.bladetetra.item.SaltyFishParts.Blade.STANDARD, null);
        String slot = stockBlade ? ModularSlashBladeItem.BLADE_SLOT : ModularSlashBladeItem.SAYA_SLOT;
        var stockTag = stock.getOrCreateTag();
        String module = stockTag.getString(slot);
        se.mickelus.tetra.items.modular.IModularItem.putModuleInSlot(mixed, slot, module, module + "_material",
                stockTag.getString(module + "_material"));
        se.mickelus.tetra.items.modular.IModularItem.updateIdentifier(mixed);
        item.syncDerivedBladeState(mixed);
        var snapshot = BladeExportSnapshot.captureOwnedStack(mixed);
        var report = BladeExportPreflight.inspect(snapshot, mixed);
        if (!report.ready()) throw new IllegalStateException("Fish and stock parts should mix: " + report.summary());
        var art = BladeExportArt.bake("bt_export_fish_mixed", snapshot, mixed, resources);
        var groups = new WavefrontObject(art.model().toString(),
                new java.io.ByteArrayInputStream(art.assets().get(art.model()))).groupObjects;
        String group = stockBlade ? "sheath" : "blade";
        float tip = groups.stream().filter(mesh -> group.equals(mesh.name))
                .flatMap(mesh -> mesh.faces.stream()).flatMap(face -> java.util.Arrays.stream(face.vertices))
                .map(vertex -> vertex.x).min(Float::compare).orElseThrow();
        var length = dev.bladetetra.item.SaltyFishParts.Length.STANDARD;
        if (Math.abs(tip - (stockBlade ? length.sayaTip : length.bladeTip)) > 1.0F
                || snapshot.componentEffects().saltyFish() == stockBlade) {
            throw new IllegalStateException("Mixed fish model or hit feedback disagrees with the fitted part: " + group);
        }
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
