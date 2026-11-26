package dev.bladetetra.export;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.easteregg.AkatsukiAwakening;
import dev.bladetetra.easteregg.BladeLegacyEasterEggs;
import dev.bladetetra.easteregg.KyoukaAwakening;
import dev.bladetetra.easteregg.SenbonzakuraAwakening;
import dev.bladetetra.forging.LegacyImprintKind;
import dev.bladetetra.forging.NamedBackplate;
import dev.bladetetra.forging.NamedLegacyImprintStorage;
import dev.bladetetra.item.ModularSlashBladeItem;
import dev.bladetetra.registry.ModItems;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.resource.PathPackResources;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

@Mod.EventBusSubscriber(modid = BladeTetra.MOD_ID)
public final class BladeExportPackageSmoke {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("blade_tetra.exportPackageSmoke")) return;
        try {
            var player = FakePlayerFactory.getMinecraft(event.getServer().overworld());
            if (BladeExportSnapshot.mayExport(player)) throw new IllegalStateException("Unauthorized export");
            verifyMixedSources(event.getServer().overworld());
            verifyAwakeningOmission(event.getServer().overworld());
            verifyAuthorization(event.getServer().overworld());
            verifyPersistentWorkbench(event.getServer().overworld());
            verifyPrecisionHabaki(event.getServer().overworld());
            var modVersion = ModList.get().getModContainerById("slashblade").orElseThrow()
                    .getModInfo().getVersion().toString();
            String expectedVersion = System.getProperty("blade_tetra.expectedSlashbladeVersion", "1.9.63");
            if (!expectedVersion.equals(modVersion)) {
                throw new IllegalStateException("Wrong SlashBlade version in export smoke: expected "
                        + expectedVersion + ", got " + modVersion);
            }
            LOGGER.info("BLADE_EXPORT_SLASHBLADE_VERSION: {}", modVersion);
            var spec = new NativeBladeExport(7.5F, 120, ComboStateRegistry.STANDBY.getId(),
                    SlashArtsRegistry.WAVE_EDGE.getId(), List.of(), List.of(),
                    DefaultResources.resourceDefaultModel, DefaultResources.resourceDefaultTexture, 0xDA405E,
                    false, CarryType.values()[0], true, false, false);
            var metadata = new BladeExportArchive.Metadata("bt_export_test", "1.0.0",
                    "封存 \"试刀\"", "Blade Tetra", "Export package smoke test");
            var evidence = List.of(ExportResourceEvidence.installedModAsset(spec.model(), "slashblade"),
                    ExportResourceEvidence.installedModAsset(spec.texture(), "slashblade"));
            Path file = BladeExportArchive.write(Path.of("."), metadata, "test", spec,
                    List.of(new BladeExportArchive.Dependency("slashblade", modVersion)),
                    List.of(), evidence, Map.of(), new ExportBattleProfile(1, List.of(), List.of(),
                    new BladeExportSnapshot.ComponentEffects(0, 0, 0, false, 0, 0, 0, false, false, false)));
            try (ZipFile archive = new ZipFile(file.toFile())) {
                // Every sealed blade, including one with only native combos, carries the shared runtime.
                if (archive.getEntry("META-INF/jarjar/metadata.json") == null
                        || archive.getEntry("META-INF/jarjar/bt-export-runtime-"
                                + ExportBattleProfile.RUNTIME_VERSION + ".jar") == null
                        || archive.getEntry("data/bt_export_test/blade_export/sealed_blade.json") == null) {
                    throw new IllegalStateException("Unified export omitted its portable runtime or profile");
                }
                if (archive.stream().anyMatch(entry -> entry.getName().matches("data/[^/]+/(recipes|loot_tables)/.*"))) {
                    throw new IllegalStateException("Generated add-on contains a survival acquisition path");
                }
                verifyEmbeddedRuntimeMetadata(archive);
                var descriptor = new String(archive.getInputStream(
                        archive.getEntry("META-INF/mods.toml")).readAllBytes(), StandardCharsets.UTF_8);
                if (descriptor.contains("modId=\"blade_tetra\"")
                        || descriptor.contains("modId=\"tetra\"")) {
                    throw new IllegalStateException("Generated add-on depends on the exporter");
                }
                var definitionEntry = archive.getEntry(
                        "data/bt_export_test/slashblade/named_blades/sealed_blade.json");
                try (var reader = new InputStreamReader(archive.getInputStream(definitionEntry),
                        StandardCharsets.UTF_8)) {
                    var definition = SlashBladeDefinition.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                            .result().orElseThrow();
                    var state = definition.getBlade().getCapability(ItemSlashBlade.BLADESTATE)
                            .resolve().orElseThrow();
                    if (state.getBaseAttackModifier() != spec.attack() || state.getMaxDamage() != spec.durability()
                            || !state.getComboRoot().equals(spec.combo())
                            || !state.getSlashArtsKey().equals(spec.slashArt())
                            || !state.isDefaultBewitched()) {
                        throw new IllegalStateException("Exported native blade state differs from input");
                    }
                }
                var certificateEntry = archive.getEntry("blade-export.json");
                try (var reader = new InputStreamReader(archive.getInputStream(certificateEntry),
                        StandardCharsets.UTF_8)) {
                    var certificate = JsonParser.parseReader(reader).getAsJsonObject();
                    var capabilities = certificate.getAsJsonArray("capabilities");
                    if (capabilities == null || capabilities.size() != 1
                            || !capabilities.get(0).getAsString().equals("slashblade:native_blade_v1")) {
                        throw new IllegalStateException("Missing base capability in sealed certificate");
                    }
                    var portable = JsonParser.parseString(new String(archive.getInputStream(
                            archive.getEntry("data/bt_export_test/blade_export/sealed_blade.json")).readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!portable.get("comboRoot").getAsString().equals(spec.combo().toString())
                            || !capabilities.equals(portable.getAsJsonArray("capabilities"))) {
                        throw new IllegalStateException("Runtime and certificate disagree on portable abilities");
                    }
                    var hashes = certificate.getAsJsonObject("sha256");
                    if (hashes.size() != archive.size() - 1) {
                        throw new IllegalStateException("Certificate did not cover every exported file");
                    }
                    for (var hash : hashes.entrySet()) {
                        try (var input = archive.getInputStream(archive.getEntry(hash.getKey()))) {
                            if (!BladeExportArchive.hash(input.readAllBytes()).equals(hash.getValue().getAsString())) {
                                throw new IllegalStateException("Hash mismatch: " + hash.getKey());
                            }
                        }
                    }
                }
            }
            writeRuntimeFixtures(event.getServer().overworld());
            LOGGER.info("BLADE_EXPORT_PACKAGE_SMOKE_PASS: {}", file.toAbsolutePath().normalize());
        } catch (Exception e) {
            LOGGER.error("BLADE_EXPORT_PACKAGE_SMOKE_FAIL", e);
            throw new IllegalStateException(e);
        } finally {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
                event.getServer().halt(false);
            }
        }
    }

    private static void verifyEmbeddedRuntimeMetadata(ZipFile archive) throws Exception {
        String path = "META-INF/jarjar/bt-export-runtime-" + ExportBattleProfile.RUNTIME_VERSION + ".jar";
        var runtimeEntry = archive.getEntry(path);
        if (runtimeEntry == null) throw new IllegalStateException("Missing nested runtime: " + path);
        String descriptor = null;
        try (var inner = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(archive.getInputStream(runtimeEntry).readAllBytes()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = inner.getNextEntry()) != null) {
                if ("META-INF/mods.toml".equals(entry.getName())) {
                    descriptor = new String(inner.readAllBytes(), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        if (descriptor == null
                || !descriptor.contains("modId=\"bt_export_runtime\"")
                || !descriptor.contains("version=\"" + ExportBattleProfile.RUNTIME_VERSION + "\"")
                || !descriptor.contains("versionRange=\"[1.9.63,2)\"")) {
            throw new IllegalStateException("Inner Forge runtime metadata disagrees with Jar-in-Jar identity");
        }
    }

    private static void writeRuntimeFixtures(ServerLevel level) throws Exception {
        var dependency = ModList.get().getModFileById("slashblade").getFile();
        var assets = dependency.findResource("assets");
        var pack = new PathPackResources("mod:slashblade", true, assets.getParent());
        var projectAssets = ModList.get().getModFileById("blade_tetra").getFile().findResource("assets");
        var projectPack = new PathPackResources("mod:blade_tetra", true, projectAssets.getParent());
        var exported = new java.util.ArrayList<Path>();
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack, projectPack))) {
            var first = ComboStateRegistry.COMBO_A1.getId();
            var second = ComboStateRegistry.COMBO_C.getId();
            var admin = creativeAdmin(level);
            for (String slug : List.of("custom", "custom_second", "dangaku", "reinforced")) {
                var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
                var tag = blade.getOrCreateTag();
                if (slug.equals("dangaku")) {
                    tag.putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.NODACHI_MODULE);
                    tag.putString(ModularSlashBladeItem.NODACHI_MODULE + "_material", "nodachi_blade/iron");
                } else {
                    tag.putString(ModularSlashBladeItem.BLADE_SLOT, ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE);
                    tag.putString(ModularSlashBladeItem.CUSTOM_STANDARD_BLADE_MODULE + "_material", "custom_standard_blade/iron");
                    var moves = slug.equals("custom") ? List.of(first, first, second) : List.of(second);
                    tag.put(CustomStyleSpec.STACK_KEY, new CustomStyleSpec(slug, moves).encode());
                }
                if (slug.equals("custom_second")) {
                    tag.putString(ModularSlashBladeItem.TSUBA_SLOT, ModularSlashBladeItem.GUARD_TSUBA_MODULE);
                    tag.putString(ModularSlashBladeItem.GUARD_TSUBA_MODULE + "_material", "guard_tsuba/iron");
                    tag.putString(ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.PRECISION_HABAKI_MODULE);
                    tag.putString(ModularSlashBladeItem.PRECISION_HABAKI_MODULE + "_material", "precision_habaki/iron");
                }
                if (slug.equals("reinforced")) {
                    tag.putString(ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.REINFORCED_HABAKI_MODULE);
                    tag.putString(ModularSlashBladeItem.REINFORCED_HABAKI_MODULE + "_material", "reinforced_habaki/iron");
                }
                admin.setItemInHand(InteractionHand.MAIN_HAND, blade);
                var original = BladeExportSnapshot.capture(admin, InteractionHand.MAIN_HAND);
                var snapshot = BladeSealPackets.decodeSnapshot(BladeSealPackets.encodeSnapshot(original));
                if (!original.equals(snapshot)) throw new IllegalStateException("Export packet changed the blade");
                StandaloneBladeExport.checkCombat(snapshot);
                if (slug.equals("custom_second")
                        && (!snapshot.componentEffects().precisionHabaki()
                            || !ExportBattleProfile.from(snapshot).capabilities(ExportBattleProfile.CUSTOM_ROOT)
                                .contains("bt_export_runtime:precision_habaki_v1"))) {
                    throw new IllegalStateException("Mixed guard/precision sword lost Rank retention");
                }
                if (slug.equals("reinforced")
                        && (!snapshot.componentEffects().reinforcedHabaki()
                            || snapshot.componentEffects().unsupported()
                            || !ExportBattleProfile.from(snapshot).capabilities(ExportBattleProfile.CUSTOM_ROOT)
                                .contains("bt_export_runtime:reinforced_habaki_v1"))) {
                    throw new IllegalStateException("Reinforced habaki was not preserved by the portable profile");
                }
                if (!ExportBattleProfile.from(snapshot).supports(
                        "CUSTOM".equals(snapshot.style()) ? ExportBattleProfile.CUSTOM_ROOT
                                : ExportBattleProfile.DANGAKU_ROOT)) {
                    throw new IllegalStateException("Sealed style lost its portable combo root");
                }
                var metadata = new BladeExportArchive.Metadata("bt_export_" + slug, "1.0.0",
                        "封存 " + slug, "Blade Tetra", "Export runtime smoke test");
                boolean client = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient();
                Path file;
                if (client) {
                    var art = BladeExportArt.bake(metadata.modId(), snapshot, blade, resources);
                    file = StandaloneBladeExport.write(Path.of("."), resources, snapshot, metadata, art);
                    verifyArtwork(file, metadata.modId());
                    LOGGER.info("BLADE_EXPORT_BAKED_FIXTURE: {}", file.toAbsolutePath().normalize());
                } else {
                    file = StandaloneBladeExport.write(Path.of("."), resources, snapshot, metadata);
                }
                exported.add(file);
                LOGGER.info("BLADE_EXPORT_RUNTIME_FIXTURE: {}", file.toAbsolutePath().normalize());
            }
        }
        verifySharedRuntime(exported);
    }

    /** Structural coexistence proof; an isolated receiving server is still required. */
    private static void verifySharedRuntime(List<Path> exported) throws Exception {
        if (exported.size() != 4) throw new IllegalStateException("Missing generated sword fixtures");
        String commonRuntimeHash = null;
        var identities = new java.util.HashSet<String>();
        for (Path file : exported) {
            try (var zip = new ZipFile(file.toFile())) {
                var runtime = zip.getEntry("META-INF/jarjar/bt-export-runtime-"
                        + ExportBattleProfile.RUNTIME_VERSION + ".jar");
                var descriptor = zip.getEntry("META-INF/jarjar/metadata.json");
                var certificate = zip.getEntry("blade-export.json");
                if (runtime == null || descriptor == null || certificate == null) {
                    throw new IllegalStateException("Sealed sword lacks a versioned runtime: " + file);
                }
                String digest = BladeExportArchive.hash(zip.getInputStream(runtime).readAllBytes());
                if (commonRuntimeHash == null) commonRuntimeHash = digest;
                else if (!commonRuntimeHash.equals(digest)) {
                    throw new IllegalStateException("Exports shipped conflicting bytes for one runtime version");
                }
                var jarJar = JsonParser.parseString(new String(zip.getInputStream(descriptor).readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                var info = jarJar.getAsJsonArray("jars").get(0).getAsJsonObject();
                if (!"bt-export-runtime".equals(info.getAsJsonObject("identifier").get("artifact").getAsString())
                        || !ExportBattleProfile.RUNTIME_VERSION.equals(
                                info.getAsJsonObject("version").get("artifactVersion").getAsString())
                        || !ExportBattleProfile.RUNTIME_RANGE.equals(
                                info.getAsJsonObject("version").get("range").getAsString())) {
                    throw new IllegalStateException("Inconsistent Jar-in-Jar runtime coordinates");
                }
                var manifest = JsonParser.parseString(new String(zip.getInputStream(certificate).readAllBytes(),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                String swordId = manifest.get("blade").getAsString();
                if (!identities.add(swordId)) {
                    throw new IllegalStateException("Two sealed swords share the same immutable definition ID");
                }
                var capabilities = manifest.getAsJsonArray("capabilities");
                String expected = swordId.equals("bt_export_dangaku:sealed_blade")
                        ? "bt_export_runtime:dangaku_v1" : "bt_export_runtime:custom_combo_v1";
                boolean present = false;
                for (var capability : capabilities) {
                    if (expected.equals(capability.getAsString())) present = true;
                }
                if (!present) throw new IllegalStateException("Missing combat capability " + expected);
                if (swordId.equals("bt_export_reinforced:sealed_blade")) {
                    boolean reinforced = false;
                    for (var capability : capabilities) {
                        if ("bt_export_runtime:reinforced_habaki_v1".equals(capability.getAsString())) {
                            reinforced = true;
                        }
                    }
                    if (!reinforced) throw new IllegalStateException("Reinforced fitting was lost from capability contract");
                }
                var portable = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(
                        "data/" + swordId.replace(":sealed_blade", "") + "/blade_export/sealed_blade.json"))
                        .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!capabilities.equals(portable.getAsJsonArray("capabilities"))) {
                    throw new IllegalStateException("Generated runtime profile does not match signed capability list");
                }
            }
        }
        LOGGER.info("BLADE_EXPORT_SHARED_RUNTIME_PASS");
    }

    static void verifyArtwork(Path file, String modId) throws Exception {
        try (var zip = new ZipFile(file.toFile())) {
            var modelEntry = zip.getEntry("assets/" + modId + "/model/sealed_blade.obj");
            try (var input = zip.getInputStream(modelEntry)) {
                var model = new mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject(modId, input);
                for (var group : List.of("blade", "sheath", "item_blade")) {
                    var faces = model.groupObjects.stream().filter(entry -> entry.name.equals(group))
                            .findFirst().orElseThrow().faces;
                    if (faces.isEmpty()) throw new IllegalStateException("Empty artwork group: " + group);
                    boolean inventory = group.startsWith("item_");
                    for (var face : faces) for (var uv : face.textureCoordinates) {
                        if (inventory ? uv.v < .5F : uv.v > .5F) {
                            throw new IllegalStateException("Wrong atlas half for " + group);
                        }
                    }
                }
            }
            try (var input = zip.getInputStream(zip.getEntry("assets/" + modId + "/model/sealed_blade.png"))) {
                var image = javax.imageio.ImageIO.read(input);
                if (image == null || image.getWidth() != 256 || image.getHeight() != 512) {
                    throw new IllegalStateException("Invalid sealed-blade atlas");
                }
            }
        }
    }

    private static void verifyAuthorization(ServerLevel level) {
        var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var admin = new FakePlayer(level, new GameProfile(java.util.UUID.randomUUID(), "SealPermissions")) {
            @Override public boolean isCreative() { return true; }
            @Override protected int getPermissionLevel() { return allowed.get() ? 2 : 0; }
        };
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var menu = workbench(admin, blade);
        var permission = BladeSealSessions.begin(admin, menu).token();
        allowed.set(false);
        if (BladeSealSessions.takeConfirmed(admin, permission) != null) {
            throw new IllegalStateException("Export authorization survived an operator downgrade");
        }
        allowed.set(true);
        var swapped = BladeSealSessions.begin(admin, menu).token();
        menu.getSlot(0).set(blade.copy());
        if (BladeSealSessions.takeConfirmed(admin, swapped) != null) {
            throw new IllegalStateException("Swapping a blade kept the old export authorization");
        }
        var modified = BladeSealSessions.begin(admin, menu).token();
        menu.blade().getOrCreateTag().putInt("sealSmokeOnly", 1);
        if (BladeSealSessions.takeConfirmed(admin, modified) != null) {
            throw new IllegalStateException("A mutated blade retained its export authorization");
        }
        menu.blade().getOrCreateTag().remove("sealSmokeOnly");

        menu.blade().getOrCreateTag().remove("bladeState");
        var approved = BladeSealSessions.begin(admin, menu).token();
        // SlashBlade writes its share-tag shadow when Forge serializes the menu slot.
        var shared = menu.blade().getShareTag();
        if (shared == null || !shared.contains("bladeState")) {
            throw new IllegalStateException("Expected the native SlashBlade slot share tag");
        }
        if (BladeSealSessions.takeConfirmed(admin, approved) == null
                || BladeSealSessions.takeConfirmed(admin, approved) != null) {
            throw new IllegalStateException("Expected a single-use export authorization");
        }
    }

    private static void verifyPersistentWorkbench(ServerLevel level) {
        var owner = creativeAdmin(level);
        var visitor = creativeAdmin(level);
        var spawn = level.getSharedSpawnPos();
        var position = spawn.above(2);
        owner.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        visitor.setPos(position.getX() + .5, position.getY(), position.getZ() + .5);
        var state = BladeSealWorkbench.BLOCK.get().defaultBlockState();
        var previous = level.getBlockState(position);
        level.setBlockAndUpdate(position, state);
        try {
            var storage = (BladeSealWorkbenchStorage) level.getBlockEntity(position);
            if (storage == null) throw new IllegalStateException("Seal workbench storage did not register");
            var menu = new BladeSealWorkbench.WorkbenchMenu(42, owner.getInventory(), storage);
            owner.containerMenu = menu;
            var original = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            original.setHoverName(net.minecraft.network.chat.Component.literal("Stored seal fixture"));
            menu.getSlot(0).set(original);
            var visitorMenu = new BladeSealWorkbench.WorkbenchMenu(43, visitor.getInventory(), storage);
            if (visitorMenu.stillValid(visitor) || visitorMenu.getSlot(0).mayPickup(visitor)
                    || visitorMenu.quickMoveStack(visitor, 0) != ItemStack.EMPTY) {
                throw new IllegalStateException("Another player can retrieve a stored blade");
            }
            var preview = BladeSealSessions.begin(owner, menu);
            menu.removed(owner);
            if (BladeSealSessions.takeConfirmed(owner, preview.token()) != null || menu.blade() != original) {
                throw new IllegalStateException("Closing the menu lost the blade or kept an old authorization");
            }
            var reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                    position, state, storage.saveWithFullMetadata());
            if (!(reloaded instanceof BladeSealWorkbenchStorage restored)
                    || !ItemStack.matches(original, restored.inventory().getItem(0))
                    || !restored.canUse(owner) || restored.canUse(visitor)
                    || !preview.projectId().equals(restored.studioProject())) {
                throw new IllegalStateException("Workbench save/reload lost blade data or its depositor");
            }
            var reopened = new BladeSealWorkbench.WorkbenchMenu(44, owner.getInventory(), storage);
            if (reopened.quickMoveStack(owner, 0).isEmpty() || !storage.inventory().isEmpty()
                    || !storage.canUse(visitor)) {
                throw new IllegalStateException("The depositor could not reclaim the blade and free the slot");
            }
            reopened.getSlot(0).set(original.copy());
            if (preview.projectId().equals(reopened.studioProject())) {
                throw new IllegalStateException("A new deposit inherited another project's identity");
            }
            LOGGER.info("BLADE_STUDIO_WORKBENCH_STORAGE_PASS");
        } finally {
            level.setBlockAndUpdate(position, previous);
        }
    }

    private static BladeSealWorkbench.WorkbenchMenu workbench(ServerPlayer player, ItemStack blade) {
        var menu = new BladeSealWorkbench.WorkbenchMenu(42, player.getInventory(),
                null);
        menu.getSlot(0).set(blade);
        player.containerMenu = menu;
        return menu;
    }

    private static void verifyPrecisionHabaki(ServerLevel level) {
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = blade.getOrCreateTag();
        tag.putString(ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.PRECISION_HABAKI_MODULE);
        var player = creativeAdmin(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var snapshot = BladeExportSnapshot.capture(player, InteractionHand.MAIN_HAND);
        if (!snapshot.componentEffects().precisionHabaki() || snapshot.componentEffects().unsupported()) {
            throw new IllegalStateException("Precision habaki must be portable without dropping other effects");
        }
        StandaloneBladeExport.checkCombat(snapshot);
        if (!ExportBattleProfile.from(snapshot).capabilities(snapshot.combo())
                .contains("bt_export_runtime:precision_habaki_v1")) {
            throw new IllegalStateException("Precision habaki is missing from the generated capabilities");
        }

        tag.putString(ModularSlashBladeItem.HABAKI_SLOT, ModularSlashBladeItem.REINFORCED_HABAKI_MODULE);
        var reinforced = BladeExportSnapshot.capture(player, InteractionHand.MAIN_HAND);
        if (!reinforced.componentEffects().reinforcedHabaki()
                || reinforced.componentEffects().unsupported()
                || reinforced.componentEffects().precisionHabaki()) {
            throw new IllegalStateException("Reinforced habaki was not captured separately from precision");
        }
        StandaloneBladeExport.checkCombat(reinforced);
        if (!ExportBattleProfile.from(reinforced).capabilities(reinforced.combo())
                .contains("bt_export_runtime:reinforced_habaki_v1")) {
            throw new IllegalStateException("Reinforced habaki is missing from the portable capability manifest");
        }
    }

    private static FakePlayer creativeAdmin(ServerLevel level) {
        return new FakePlayer(level, new GameProfile(java.util.UUID.randomUUID(), "ExportSmoke")) {
            @Override public boolean isCreative() { return true; }
            @Override protected int getPermissionLevel() { return 2; }
        };
    }

    private static void verifyMixedSources(ServerLevel level) {
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        var tag = blade.getOrCreateTag();
        Map<String, LegacyImprintKind> sources = Map.of("blade", LegacyImprintKind.BLACK_FOX,
                "saya", LegacyImprintKind.WOODEN_PUPPET, "tsuba", LegacyImprintKind.WHITE_FOX);
        for (var entry : sources.entrySet()) {
            String part = entry.getKey();
            LegacyImprintKind source = entry.getValue();
            String module = tag.getString("slashblade/" + part);
            tag.putString(module + "_material", NamedLegacyImprintStorage.genericVariant(part));
            NamedLegacyImprintStorage.putSource(tag, part, source.id());
            NamedLegacyImprintStorage.putSnapshot(blade, part, source);
        }
        tag.putString(ModularSlashBladeItem.BACKPLATE_SLOT, NamedBackplate.MODULE);
        blade.getOrCreateTag().putString("blade_tetra_backplate_source", LegacyImprintKind.WHITE_FOX.id());
        var admin = creativeAdmin(level);
        admin.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var original = blade.save(new net.minecraft.nbt.CompoundTag());
        var snapshot = BladeExportSnapshot.capture(admin, InteractionHand.MAIN_HAND);
        if (!original.equals(blade.save(new net.minecraft.nbt.CompoundTag()))) {
            throw new IllegalStateException("Capturing a blade modified the original");
        }
        var captured = snapshot.namedParts();
        if (captured.size() != 4) throw new IllegalStateException("Missing named part provenance: " + captured);
        var menu = workbench(admin, blade);
        var preview = BladeSealSessions.begin(admin, menu);
        var packet = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            preview.encode(packet);
            var decoded = BladeSealPackets.Preview.decode(packet);
            if (!decoded.equals(preview)) {
                throw new IllegalStateException("Blade Seal compatibility diagnosis changed in transit");
            }
        } finally {
            packet.release();
        }
        // Recognized native assets are now eligible for the client-side static baker.
        // A successful server preview still does not promise client resource/license acceptance.
        if (!preview.ready() || preview.omitsAwakening()
                || BladeSealSessions.takeConfirmed(admin, preview.token()) == null
                || BladeSealSessions.takeConfirmed(admin, preview.token()) != null) {
            throw new IllegalStateException("Supported named fitting export was rejected or reused: " + preview);
        }
        for (var part : captured) {
            var expected = part.slot().equals("backplate") ? LegacyImprintKind.WHITE_FOX : sources.get(part.slot());
            if (!expected.id().equals(part.sourceId())) {
                throw new IllegalStateException("Named part provenance attributed to wrong blade: " + part);
            }
        }

        // An unknown provider is never acceptable merely because its semantic snapshot exists.
        var forgedSource = new LegacyImprintKind("untrusted_export_smoke",
                ResourceLocation.parse("untrusted_provider:blade"),
                ResourceLocation.parse("untrusted_provider:model/blade.obj"),
                ResourceLocation.parse("untrusted_provider:model/blade.png"),
                "minecraft:stone", LegacyImprintKind.BLACK_FOX.defaultProfile(),
                3.0D, 200, null, List.of());
        NamedLegacyImprintStorage.putSource(tag, "blade", forgedSource.id());
        NamedLegacyImprintStorage.putSnapshot(blade, "blade", forgedSource);
        var denied = BladeSealSessions.begin(admin, menu);
        if (denied.ready() || !denied.summary().contains("appearance:")
                || BladeSealSessions.takeConfirmed(admin, denied.token()) != null) {
            throw new IllegalStateException("Unlicensed model provider bypassed preflight: " + denied);
        }
    }


    private static void verifyAwakeningOmission(ServerLevel level) {
        // Both single and dual soul inscriptions must preserve the ordinary
        // completed build, while requiring explicit permission to omit abilities.
        Map<String, String> variants = Map.of(
                "awakened_soul_inscription/blood_crystal", AkatsukiAwakening.TAG_UNLOCKED,
                "awakened_soul_inscription/proudsoul_sphere", KyoukaAwakening.TAG_UNLOCKED,
                "awakened_soul_inscription/sakura_crystal", SenbonzakuraAwakening.TAG_UNLOCKED,
                "awakened_soul_inscription/raikiri", BladeLegacyEasterEggs.TAG_RAIKIRI_UNLOCKED,
                "awakened_soul_inscription/akatsuki_kyouka", AkatsukiAwakening.TAG_UNLOCKED);
        for (var entry : variants.entrySet()) {
            var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
            var originalTag = blade.getOrCreateTag();
            originalTag.putString(ModularSlashBladeItem.INSCRIPTION_SLOT,
                    ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE);
            originalTag.putString(ModularSlashBladeItem.AWAKENED_SOUL_INSCRIPTION_MODULE + "_material",
                    entry.getKey());
            originalTag.putBoolean(entry.getValue(), true);
            originalTag.putString("seal_non_awakening_metadata", "KEEP_ME");
            boolean akatsuki = entry.getKey().contains("akatsuki") || entry.getKey().contains("blood_crystal");
            if (akatsuki) blade.enchant(Enchantments.POWER_ARROWS, 1);
            blade.enchant(Enchantments.SHARPNESS, 2);

            var admin = creativeAdmin(level);
            admin.setItemInHand(InteractionHand.MAIN_HAND, blade);
            var menu = workbench(admin, blade);
            var original = blade.save(new net.minecraft.nbt.CompoundTag());
            var preview = BladeSealSessions.begin(admin, menu);
            if (!preview.ready() || !preview.omitsAwakening()) {
                throw new IllegalStateException("Awakened ordinary blade was rejected without a warning: "
                        + entry.getKey() + ": " + preview);
            }
            if (!original.equals(blade.save(new net.minecraft.nbt.CompoundTag()))) {
                throw new IllegalStateException("Awakening projection mutated source blade: " + entry.getKey());
            }
            var serialized = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                preview.encode(serialized);
                if (!BladeSealPackets.Preview.decode(serialized).equals(preview))
                    throw new IllegalStateException("Awakening-loss warning dropped from preview packet");
            } finally {
                serialized.release();
            }
            var forbidden = BladeSealSessions.takeConfirmed(admin, preview.token());
            if (forbidden != null) {
                throw new IllegalStateException("Awakened blade exported without explicit loss consent");
            }
            var approved = BladeSealSessions.takeConfirmed(admin, preview.token(), true);
            if (approved == null || BladeSealSessions.takeConfirmed(admin, preview.token(), true) != null) {
                throw new IllegalStateException("Acknowledged awakened export was rejected or replayable");
            }
            var exported = approved.blade();
            var cleanTag = exported.getTag();
            if (cleanTag == null || !cleanTag.getString("seal_non_awakening_metadata").equals("KEEP_ME")
                    || cleanTag.contains(ModularSlashBladeItem.INSCRIPTION_SLOT)
                    || cleanTag.contains(entry.getValue())) {
                throw new IllegalStateException("Wrong awakening stripping boundary: " + entry.getKey());
            }
            if (!approved.data().inscriptions().isEmpty()
                    || approved.data().enchantments().stream()
                    .noneMatch(enchantment -> enchantment.id().equals(
                            ResourceLocation.parse("minecraft:sharpness")))) {
                throw new IllegalStateException("Base enchantment lost or awakening leaked: " + entry.getKey());
            }
            if (akatsuki && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, exported) != 0) {
                throw new IllegalStateException("Akatsuki-granted Power I survived stripped export");
            }
            StandaloneBladeExport.checkCombat(approved.data());
            BladeExportArt.check(approved.data(), exported);
            if (!original.equals(blade.save(new net.minecraft.nbt.CompoundTag()))) {
                throw new IllegalStateException("Authorizing the sealed copy changed the original blade");
            }

            // Other unported features are NOT bundled into the opt-in awakening waiver.
            originalTag.putBoolean(BladeLegacyEasterEggs.TAG_SHOSHIN_UNLOCKED, true);
            var denied = BladeSealSessions.begin(admin, menu);
            if (denied.ready() || !denied.omitsAwakening() || !denied.summary().contains("combat:")) {
                throw new IllegalStateException("Awakening consent bypassed independent capability block: " + denied);
            }
        }

        var normal = creativeAdmin(level);
        normal.setItemInHand(InteractionHand.MAIN_HAND,
                ModItems.MODULAR_SLASHBLADE.get().createDefaultStack());
        var normalMenu = workbench(normal, normal.getMainHandItem());
        var preview = BladeSealSessions.begin(normal, normalMenu);
        if (!preview.ready() || preview.omitsAwakening()
                || BladeSealSessions.takeConfirmed(normal, preview.token()) == null) {
            throw new IllegalStateException("Ordinary blades must not require awakening consent");
        }
        LOGGER.info("BLADE_EXPORT_AWAKENING_OMISSION_PASS");
    }

    private BladeExportPackageSmoke() {}
}
