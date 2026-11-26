package dev.bladetetra.export;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Tracks the active resources consumed by one bake, with bounded input reads. */
final class ExportArtResources implements ResourceManager {
    private static final long MAX_INPUT_BYTES = 32L * 1024 * 1024;
    private static final long PNG_SIGNATURE = 0x89504e470d0a1a0aL;
    private final ResourceManager delegate;
    private final Map<ResourceLocation, ExportResourceEvidence> inputs = new LinkedHashMap<>();
    private long total;

    ExportArtResources(ResourceManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation id) {
        if (id.getPath().startsWith("/") || id.getPath().contains("..")) {
            throw new IllegalArgumentException("Invalid resource path: " + id);
        }
        return delegate.getResource(id).map(resource ->
                new Resource(resource.source(), () -> open(id, resource), resource::metadata));
    }

    private ByteArrayInputStream open(ResourceLocation id, Resource resource) throws IOException {
        byte[] bytes;
        try (var stream = resource.open()) {
            bytes = stream.readNBytes(BladeExportArchive.MAX_ENTRY_BYTES + 1);
        }
        if (bytes.length > BladeExportArchive.MAX_ENTRY_BYTES || (total += bytes.length) > MAX_INPUT_BYTES) {
            throw new IOException("Sealed model input budget exceeded: " + id);
        }
        if (id.getPath().endsWith(".png")) {
            var header = java.nio.ByteBuffer.wrap(bytes);
            if (bytes.length < 24 || header.getLong() != PNG_SIGNATURE
                    || header.getInt() != 13 || header.getInt() != 0x49484452) {
                throw new IOException("Invalid PNG header: " + id);
            }
            int width = header.getInt(), height = header.getInt();
            if (width < 1 || height < 1 || width > 2048 || height > 2048) {
                throw new IOException("Source PNG exceeds 2048 pixels: " + id);
            }
        }
        if (!inputs.containsKey(id)) inputs.put(id, ExportResourceEvidence.embedded(id, resource, bytes));
        return new ByteArrayInputStream(bytes);
    }

    void include(ResourceLocation id) throws IOException {
        if (!inputs.containsKey(id)) try (var ignored = open(id)) { }
    }

    List<ExportResourceEvidence> inputs() { return List.copyOf(inputs.values()); }
    @Override public Set<String> getNamespaces() { return delegate.getNamespaces(); }
    @Override public List<Resource> getResourceStack(ResourceLocation id) { return delegate.getResourceStack(id); }
    @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        return delegate.listResources(path, filter);
    }
    @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        return delegate.listResourceStacks(path, filter);
    }
    @Override public Stream<PackResources> listPacks() { return delegate.listPacks(); }
}
