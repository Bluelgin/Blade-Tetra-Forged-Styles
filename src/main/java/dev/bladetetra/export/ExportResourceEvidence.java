package dev.bladetetra.export;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

/** Identifies the bytes actually selected by the resource manager, including pack overrides. */
public record ExportResourceEvidence(ResourceLocation id, String packId, String ownerModId,
                                     String ownerModVersion, String sha256, String permission,
                                     boolean embedded) {
    public ExportResourceEvidence {
        Objects.requireNonNull(id);
        if (packId.isBlank() || ownerModId.isBlank() || ownerModVersion.isBlank() || permission.isBlank()
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Incomplete resource evidence: " + id);
        }
    }

    /** A reference is portable only when the installed dependency supplies the selected bytes. */
    public static ExportResourceEvidence reference(ResourceManager resources, ResourceLocation id, String modId)
            throws IOException {
        var resource = resources.getResourceOrThrow(id);
        var declared = installedModAsset(id, modId);
        try (var stream = resource.open()) {
            String selected = digest(id, stream);
            if (!selected.equals(declared.sha256())) {
                throw new IOException("Active resource differs from installed provider " + modId + ": " + id);
            }
            return new ExportResourceEvidence(id, resource.sourcePackId(), modId, declared.ownerModVersion(),
                    selected, declared.permission(), false);
        }
    }

    static ExportResourceEvidence installedModAsset(ResourceLocation id, String modId) throws IOException {
        var file = ModList.get().getModFileById(modId);
        var container = ModList.get().getModContainerById(modId).orElseThrow(
                () -> new IOException("Resource provider is missing: " + modId));
        if (id.getPath().contains("..") || id.getPath().startsWith("/")) {
            throw new IOException("Invalid resource path: " + id);
        }
        var path = file.getFile().findResource("assets", id.getNamespace(), id.getPath());
        try (var stream = Files.newInputStream(path)) {
            return new ExportResourceEvidence(id, "mod:" + modId, modId,
                    container.getModInfo().getVersion().toString(), digest(id, stream),
                    "Referenced from the installed provider; original license applies", false);
        }
    }

    static String digest(ResourceLocation id, InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(BladeExportArchive.MAX_ENTRY_BYTES + 1);
        if (bytes.length > BladeExportArchive.MAX_ENTRY_BYTES) {
            throw new IOException("Resource too large to verify: " + id);
        }
        return BladeExportArchive.hash(bytes);
    }

    public static ExportResourceEvidence fingerprint(ResourceLocation id, Resource resource,
            String ownerModId, String ownerModVersion, String permission, boolean embedded) throws IOException {
        try (var stream = resource.open()) {
            return new ExportResourceEvidence(id, resource.sourcePackId(), ownerModId, ownerModVersion,
                    digest(id, stream), permission, embedded);
        }
    }
}
