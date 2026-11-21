package dev.bladetetra.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Writes a generated Forge addon using only explicitly supplied, validated resources. */
final class BladeExportArchive {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .setPrettyPrinting().create();
    private static final LocalDateTime ZIP_EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0, 0);
    private static final String LICENSE_TITLE = "Private modpack use only";
    private static final String NOTICE =
            "This Blade Tetra export is for use in private modpacks. Do not publish the generated "
                    + "add-on or repackage it for distribution on public mod platforms. "
                    + "The original terms of any third-party material and components still apply.";
    static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 48L * 1024 * 1024;

    record Metadata(String modId, String version, String title, String author, String description) {
        Metadata {
            if (modId.equals(ExportBattleProfile.RUNTIME_ID) || !modId.matches("bt_export_[a-z0-9_]{1,50}") || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                    || version.length() > 30 || title.isBlank() || title.length() > 100
                    || author.length() > 64 || description.length() > 800) {
                throw new IllegalArgumentException("Invalid exported add-on metadata");
            }
        }
    }

    record Dependency(String modId, String version) {
        Dependency {
            if (!modId.matches("[a-z][a-z0-9_]{1,63}") || !version.matches("[a-zA-Z0-9_.+\\-]{1,64}")
                    || modId.equals("blade_tetra") || modId.equals("tetra")) {
                throw new IllegalArgumentException("Invalid or non-portable dependency: " + modId);
            }
        }
    }

    record Fitting(NamedPartProvenance source, List<ExportResourceEvidence> assets) {
        Fitting {
            assets = List.copyOf(assets);
            if (assets.isEmpty()) {
                throw new IllegalArgumentException("Missing resource provenance: " + source.slot());
            }
        }
    }

    static Path write(Path gameDirectory, Metadata metadata, String exporterVersion,
            NativeBladeExport definition, BladeExportRecipe recipe, List<Dependency> dependencies,
            List<Fitting> fittings, List<ExportResourceEvidence> appearanceSources,
            Map<ResourceLocation, byte[]> bakedAssets, ExportBattleProfile battle) throws IOException {
        Objects.requireNonNull(battle, "A sealed export always includes a portable battle profile");
        if (!battle.moves().isEmpty() && !battle.supports(definition.combo())) {
            throw new IllegalArgumentException("Custom moves require the portable custom combo root");
        }
        var blade = ResourceLocation.fromNamespaceAndPath(metadata.modId(), "sealed_blade");
        Set<String> mods = new HashSet<>(Set.of("minecraft", "forge"));
        for (Dependency dependency : dependencies) {
            if (!mods.add(dependency.modId())) {
                throw new IllegalArgumentException("Duplicate dependency: " + dependency.modId());
            }
        }
        if (!mods.contains("slashblade") || !mods.containsAll(recipe.namespaces())) {
            throw new IllegalArgumentException("Missing target dependencies");
        }
        if (bakedAssets.size() > 128) {
            throw new IllegalArgumentException("Too many exported assets");
        }
        Set<String> slots = new HashSet<>();
        for (Fitting fitting : fittings) {
            String slot = fitting.source().slot();
            if (!Set.of("blade", "saya", "tsuba", "backplate").contains(slot) || !slots.add(slot)) {
                throw new IllegalArgumentException("Unexpected or duplicate named fitting: " + slot);
            }
        }

        for (ResourceLocation asset : List.of(definition.model(), definition.texture())) {
            if (!asset.getNamespace().equals(metadata.modId())
                    && appearanceSources.stream().noneMatch(source -> asset.equals(source.id()) && !source.embedded()
                            && dependencies.stream().anyMatch(dep -> dep.modId().equals(source.ownerModId())
                                    && dep.version().equals(source.ownerModVersion())))) {
                throw new IllegalArgumentException("Unverified appearance dependency: " + asset);
            }
        }
        if (!bakedAssets.isEmpty() && appearanceSources.stream().noneMatch(ExportResourceEvidence::embedded)) {
            throw new IllegalArgumentException("Baked appearance has no recorded inputs");
        }

        Map<String, byte[]> entries = new TreeMap<>();
        add(entries, "META-INF/mods.toml", modDescriptor(metadata, dependencies));
        add(entries, "pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Blade Tetra sealed blade\"}}");
        add(entries, "data/" + metadata.modId() + "/slashblade/named_blades/sealed_blade.json",
                JSON.toJson(definition.encode(blade, mods, bakedAssets.keySet(), battle)));
        add(entries, "data/" + metadata.modId() + "/recipes/sealed_blade.json",
                JSON.toJson(recipe.encode(blade)));
        JsonObject lang = new JsonObject();
        lang.addProperty("item." + blade.getNamespace() + "." + blade.getPath(), metadata.title());
        add(entries, "assets/" + metadata.modId() + "/lang/en_us.json", JSON.toJson(lang));
        add(entries, "assets/" + metadata.modId() + "/lang/zh_cn.json", JSON.toJson(lang));
        add(entries, "PRIVATE-MODPACK-USE.txt", NOTICE + "\n\n仅限私人整合包使用。请勿将生成的附属或其二次打包版本发布到公开 Mod 平台。第三方素材与依赖仍各自适用原许可。\n");

        StringBuilder thirdParty = new StringBuilder("Third-party references and inputs\n\n");
        for (Fitting fitting : fittings) {
            var source = fitting.source();
            thirdParty.append(source.slot()).append(" — ").append(source.sourceId()).append('\n');
            fitting.assets().forEach(asset -> appendEvidence(thirdParty, asset));
        }
        thirdParty.append("\nOther appearance inputs\n");
        appearanceSources.forEach(asset -> appendEvidence(thirdParty, asset));
        add(entries, "THIRD-PARTY-NOTICES.txt", thirdParty.toString());

        if (!bakedAssets.isEmpty()) {
            try (var license = BladeExportArchive.class.getResourceAsStream("/META-INF/blade-export/project-license.txt")) {
                if (license == null) throw new IOException("Blade Tetra asset license is missing from this build");
                entries.put("BLADE-TETRA-ASSET-LICENSE.txt", license.readAllBytes());
            }
        }

        for (var entry : bakedAssets.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getNamespace().equals(metadata.modId())
                    || !id.getPath().matches("model/(?:[a-z0-9_-]+/)*[a-z0-9_-]+\\.(?:obj|png)")) {
                throw new IllegalArgumentException("Invalid generated appearance resource: " + id);
            }
            String path = "assets/" + id.getNamespace() + "/" + id.getPath();
            if (entries.putIfAbsent(path, entry.getValue().clone()) != null) {
                throw new IllegalArgumentException("Duplicate archive entry: " + path);
            }
        }

        addBattleRuntime(entries, blade, battle);

        UUID exportId = UUID.randomUUID();
        Map<String, String> digests = new TreeMap<>();
        entries.forEach((name, bytes) -> digests.put(name, hash(bytes)));
        add(entries, "blade-export.json", JSON.toJson(new Certificate(1, exportId, exporterVersion,
                blade, battle.capabilities(definition.combo()), dependencies, fittings, appearanceSources, digests)));
        return writeArchive(gameDirectory, metadata, exportId, entries);
    }

    private static void addBattleRuntime(Map<String, byte[]> entries, ResourceLocation blade,
            ExportBattleProfile battle) throws IOException {
        String path = "META-INF/jarjar/bt-export-runtime-" + ExportBattleProfile.RUNTIME_VERSION + ".jar";
        try (var runtime = BladeExportArchive.class.getResourceAsStream(ExportBattleProfile.RESOURCE)) {
            if (runtime == null) throw new IOException("Sealed blade runtime is missing from this build");
            byte[] bytes = runtime.readNBytes(MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("Sealed blade runtime exceeds package size limit");
            entries.put(path, bytes);
        }
        var identifier = new JsonObject();
        identifier.addProperty("group", "dev.btsealed");
        identifier.addProperty("artifact", "bt-export-runtime");
        var version = new JsonObject();
        version.addProperty("range", "[" + ExportBattleProfile.RUNTIME_VERSION + "]");
        version.addProperty("artifactVersion", ExportBattleProfile.RUNTIME_VERSION);
        var artifact = new JsonObject();
        artifact.add("identifier", identifier);
        artifact.add("version", version);
        artifact.addProperty("path", path);
        artifact.addProperty("isObfuscated", true);
        var jars = new com.google.gson.JsonArray();
        jars.add(artifact);
        var jarJar = new JsonObject();
        jarJar.add("jars", jars);
        add(entries, "META-INF/jarjar/metadata.json", JSON.toJson(jarJar));

        String profile = "/" + blade.getNamespace() + "/blade_export/" + blade.getPath() + ".json";
        add(entries, "data" + profile, JSON.toJson(battle));
        add(entries, "assets" + profile, JSON.toJson(battle));
    }

    private record Certificate(int schema, UUID exportId, String exporterVersion, ResourceLocation blade,
                               List<String> capabilities, List<Dependency> dependencies, List<Fitting> namedFittings,
                               List<ExportResourceEvidence> appearanceInputs, Map<String, String> sha256) {}

    private static void appendEvidence(StringBuilder notices, ExportResourceEvidence asset) {
        notices.append("  ").append(asset.id()).append(" | ")
                .append(asset.packId()).append(" | ")
                .append(asset.ownerModId()).append(' ').append(asset.ownerModVersion()).append(" | ")
                .append(asset.embedded() ? "included or baked" : "referenced").append(" | ")
                .append(asset.permission()).append('\n');
    }

    private static String modDescriptor(Metadata metadata, List<Dependency> dependencies) {
        return "modLoader=\"lowcodefml\"\nloaderVersion=\"[47,48)\"\nlicense=" + toml(LICENSE_TITLE)
                + "\n[[mods]]\nmodId=" + toml(metadata.modId()) + "\nversion=" + toml(metadata.version())
                + "\ndisplayName=" + toml(metadata.title()) + "\nauthors=" + toml(metadata.author())
                + "\ndescription=" + toml(metadata.description().strip() + "\n\n" + NOTICE) + "\n"
                + dependency(metadata.modId(), "forge", "[47,48)")
                + dependency(metadata.modId(), "minecraft", "[1.20.1]")
                + dependency(metadata.modId(), ExportBattleProfile.RUNTIME_ID,
                    "[" + ExportBattleProfile.RUNTIME_VERSION + "]")
                + dependencies.stream().sorted(java.util.Comparator.comparing(Dependency::modId))
                        .map(dep -> dependency(metadata.modId(), dep.modId(), "[" + dep.version() + "]"))
                        .collect(java.util.stream.Collectors.joining());
    }

    private static String dependency(String owner, String modId, String range) {
        return "[[dependencies." + owner + "]]\nmodId=" + toml(modId) + "\nmandatory=true\n"
                + "versionRange=" + toml(range) + "\nordering=\"NONE\"\nside=\"BOTH\"\n";
    }

    private static String toml(String value) {
        StringBuilder encoded = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> encoded.append("\\\"");
                case '\\' -> encoded.append("\\\\");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                case '\t' -> encoded.append("\\t");
                default -> {
                    if (c < 32 || c == 127) {
                        throw new IllegalArgumentException("Control character in export metadata");
                    }
                    encoded.append(c);
                }
            }
        }
        return encoded.append('"').toString();
    }

    private static void add(Map<String, byte[]> entries, String path, String value) {
        entries.put(path, value.getBytes(StandardCharsets.UTF_8));
    }

    private static Path writeArchive(Path gameDirectory, Metadata metadata, UUID id,
            Map<String, byte[]> entries) throws IOException {
        long size = 0;
        for (byte[] bytes : entries.values()) {
            size += bytes.length;
            if (bytes.length > MAX_ENTRY_BYTES || size > MAX_ARCHIVE_BYTES) {
                throw new IOException("Generated files exceed the export size limit");
            }
        }
        Path destination = gameDirectory.resolve("blade-exports");
        Files.createDirectories(destination);
        if (!destination.toRealPath().getParent().equals(gameDirectory.toRealPath())) {
            throw new IOException("Export directory points outside the game directory");
        }
        Path output = destination.resolve(metadata.modId() + "-" + metadata.version() + "-" + id + ".jar");
        Path temporary = Files.createTempFile(destination, ".blade-export-", ".tmp");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                for (var entry : entries.entrySet()) {
                    ZipEntry file = new ZipEntry(entry.getKey());
                    file.setTimeLocal(ZIP_EPOCH);
                    zip.putNextEntry(file);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            // An export must be fully readable before it becomes visible as a .jar.
            verifyArchive(temporary, entries);
            try {
                return Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Some removable drives and filesystems cannot atomically rename.
                // This is a same-directory move of a closed, fully verified file.
                return Files.move(temporary, output);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyArchive(Path archive, Map<String, byte[]> expected) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.size() != expected.size()) {
                throw new IOException("Export archive entry count mismatch");
            }
            for (var entry : expected.entrySet()) {
                ZipEntry zipped = zip.getEntry(entry.getKey());
                if (zipped == null || zipped.isDirectory() || zipped.getSize() != entry.getValue().length) {
                    throw new IOException("Export archive is missing or truncates " + entry.getKey());
                }
                try (var input = zip.getInputStream(zipped)) {
                    byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (bytes.length > MAX_ENTRY_BYTES || !hash(bytes).equals(hash(entry.getValue()))) {
                        throw new IOException("Export archive verification failed: " + entry.getKey());
                    }
                }
            }
        }
    }

    static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private BladeExportArchive() {}
}
