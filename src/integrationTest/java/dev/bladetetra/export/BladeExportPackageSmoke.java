package dev.bladetetra.export;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import dev.bladetetra.BladeTetra;
import dev.bladetetra.customstyle.CustomStyleSpec;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraftforge.resource.PathPackResources;
import net.minecraft.world.InteractionHand;
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
            verifyAuthorization(event.getServer().overworld());
            verifyPrecisionHabaki(event.getServer().overworld());
            var modVersion = ModList.get().getModContainerById("slashblade").orElseThrow()
                    .getModInfo().getVersion().toString();
            String expectedVersion = System.getProperty("blade_tetra.expectedSlashbladeVersion", "1.9.63");
            if (!expectedVersion.equals(modVersion)) {
                throw new IllegalStateException("Wrong SlashBlade version in export smoke: expected "
                        + expectedVersion + ", got " + modVersion);
            }
            LOGGER.info("BLADE_EXPORT_SLASHBLADE_VERSION: {}", modVersion);
            var recipe = new BladeExportRecipe(List.of(ResourceLocation.parse("minecraft:iron_ingot")), 1 << 4);
            var spec = new NativeBladeExport(7.5F, 120, ComboStateRegistry.STANDBY.getId(),
                    SlashArtsRegistry.WAVE_EDGE.getId(), List.of(), List.of(),
                    DefaultResources.resourceDefaultModel, DefaultResources.resourceDefaultTexture, 0xDA405E,
                    false, CarryType.values()[0], true, false, false);
            var metadata = new BladeExportArchive.Metadata("bt_export_test", "1.0.0",
                    "封存 \"试刀\"", "Blade Tetra", "Export package smoke test");
            var evidence = List.of(ExportResourceEvidence.installedModAsset(spec.model(), "slashblade"),
                    ExportResourceEvidence.installedModAsset(spec.texture(), "slashblade"));
            Path file = BladeExportArchive.write(Path.of("."), metadata, "test", spec, recipe,
                    List.of(new BladeExportArchive.Dependency("slashblade", modVersion)),
                    List.of(), evidence, Map.of(), new ExportBattleProfile(1, List.of(), List.of(),
                    new BladeExportSnapshot.ComponentEffects(0, 0, 0, false, 0, 0, 0, false, false)));
            try (ZipFile archive = new ZipFile(file.toFile())) {
                // Every sealed blade, including one with only native combos, carries the shared runtime.
                if (archive.getEntry("META-INF/jarjar/metadata.json") == null
                        || archive.getEntry("META-INF/jarjar/bt-export-runtime-"
                                + ExportBattleProfile.RUNTIME_VERSION + ".jar") == null
                        || archive.getEntry("data/bt_export_test/blade_export/sealed_blade.json") == null) {
                    throw new IllegalStateException("Unified export omitted its portable runtime or profile");
                }
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
            writeRuntimeFixtures(event.getServer().overworld(), recipe);
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

    private static void writeRuntimeFixtures(ServerLevel level, BladeExportRecipe recipe) throws Exception {
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
            for (String slug : List.of("custom", "custom_second", "dangaku")) {
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
                }
                admin.setItemInHand(InteractionHand.MAIN_HAND, blade);
                var original = BladeExportSnapshot.capture(admin, InteractionHand.MAIN_HAND);
                var snapshot = BladeSealPackets.decodeSnapshot(BladeSealPackets.encodeSnapshot(original));
                if (!original.equals(snapshot)) throw new IllegalStateException("Export packet changed the blade");
                StandaloneBladeExport.checkCombat(snapshot);
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
                    file = StandaloneBladeExport.write(Path.of("."), resources, snapshot, metadata, recipe, art);
                    verifyArtwork(file, metadata.modId());
                    LOGGER.info("BLADE_EXPORT_BAKED_FIXTURE: {}", file.toAbsolutePath().normalize());
                } else {
                    file = StandaloneBladeExport.write(Path.of("."), resources, snapshot, metadata, recipe);
                }
                exported.add(file);
                LOGGER.info("BLADE_EXPORT_RUNTIME_FIXTURE: {}", file.toAbsolutePath().normalize());
            }
        }
        verifySharedRuntime(exported);
    }

    /** Structural coexistence proof; an isolated receiving server is still required. */
    private static void verifySharedRuntime(List<Path> exported) throws Exception {
        if (exported.size() != 3) throw new IllegalStateException("Missing generated sword fixtures");
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
                                info.getAsJsonObject("version").get("artifactVersion").getAsString())) {
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
        admin.setItemInHand(InteractionHand.MAIN_HAND, ModItems.BLADE_SEAL.get().getDefaultInstance());
        var blade = ModItems.MODULAR_SLASHBLADE.get().createDefaultStack();
        admin.setItemInHand(InteractionHand.OFF_HAND, blade);
        var permission = BladeSealSessions.begin(admin, InteractionHand.MAIN_HAND).token();
        allowed.set(false);
        if (BladeSealSessions.takeConfirmed(admin, permission) != null) {
            throw new IllegalStateException("Export authorization survived an operator downgrade");
        }
        allowed.set(true);
        var swapped = BladeSealSessions.begin(admin, InteractionHand.MAIN_HAND).token();
        admin.setItemInHand(InteractionHand.OFF_HAND, blade.copy());
        if (BladeSealSessions.takeConfirmed(admin, swapped) != null) {
            throw new IllegalStateException("Swapping a blade kept the old export authorization");
        }
        var modified = BladeSealSessions.begin(admin, InteractionHand.MAIN_HAND).token();
        admin.getItemInHand(InteractionHand.OFF_HAND).getOrCreateTag().putInt("sealSmokeOnly", 1);
        if (BladeSealSessions.takeConfirmed(admin, modified) != null) {
            throw new IllegalStateException("A mutated blade retained its export authorization");
        }
        admin.getItemInHand(InteractionHand.OFF_HAND).getOrCreateTag().remove("sealSmokeOnly");

        var approved = BladeSealSessions.begin(admin, InteractionHand.MAIN_HAND).token();
        if (BladeSealSessions.takeConfirmed(admin, approved) == null
                || BladeSealSessions.takeConfirmed(admin, approved) != null) {
            throw new IllegalStateException("Expected a single-use export authorization");
        }
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
        if (!reinforced.componentEffects().unsupported() || reinforced.componentEffects().precisionHabaki()) {
            throw new IllegalStateException("Reinforced habaki was incorrectly marked portable");
        }
        try {
            StandaloneBladeExport.checkCombat(reinforced);
            throw new IllegalStateException("Reinforced habaki silently passed the portability gate");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Habaki")) throw expected;
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
        NamedBackplate.installSource(blade, LegacyImprintKind.WHITE_FOX.id());
        var admin = creativeAdmin(level);
        admin.setItemInHand(InteractionHand.MAIN_HAND, blade);
        var original = blade.save(new net.minecraft.nbt.CompoundTag());
        var snapshot = BladeExportSnapshot.capture(admin, InteractionHand.MAIN_HAND);
        if (!original.equals(blade.save(new net.minecraft.nbt.CompoundTag()))) {
            throw new IllegalStateException("Capturing a blade modified the original");
        }
        var captured = snapshot.namedParts();
        if (captured.size() != 4) throw new IllegalStateException("Missing named part provenance: " + captured);
        admin.setItemInHand(InteractionHand.OFF_HAND, ModItems.BLADE_SEAL.get().getDefaultInstance());
        var preview = BladeSealSessions.begin(admin, InteractionHand.OFF_HAND);
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
        if (preview.ready() || !preview.summary().contains("appearance:")
                || BladeSealSessions.takeConfirmed(admin, preview.token()) != null) {
            throw new IllegalStateException("Unsupported named fitting export was approved: " + preview);
        }
        for (var part : captured) {
            var expected = part.slot().equals("backplate") ? LegacyImprintKind.WHITE_FOX : sources.get(part.slot());
            if (!expected.id().equals(part.sourceId())) {
                throw new IllegalStateException("Named part provenance attributed to wrong blade: " + part);
            }
        }
    }

    private BladeExportPackageSmoke() {}
}
