package dev.bladetetra.export;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportArtResourcesTest {
    @TempDir Path packDirectory;

    @Test
    void rejectsEscapingResourcePathsBeforeReading() {
        var resources = new ExportArtResources(ResourceManager.Empty.INSTANCE);
        var id = ResourceLocation.fromNamespaceAndPath("blade_studio_test", "model/../outside.obj");
        assertThrows(IllegalArgumentException.class, () -> resources.getResource(id));
    }

    @Test
    void enforcesInputLimitsBeforeDecodingModelsAndTextures() throws IOException {
        var folder = Files.createDirectories(packDirectory.resolve("assets/blade_studio_test/model"));
        var png = ByteBuffer.allocate(24).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452)
                .putInt(2049).putInt(1).array();
        Files.write(folder.resolve("oversized.png"), png);
        try (var obj = Files.newByteChannel(folder.resolve("oversized.obj"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            obj.position(BladeExportArchive.MAX_ENTRY_BYTES);
            obj.write(ByteBuffer.wrap(new byte[]{1}));
        }
        var pack = new PathPackResources("studio-test-pack", packDirectory, false);
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            var resources = new ExportArtResources(manager);
            var image = ResourceLocation.fromNamespaceAndPath("blade_studio_test", "model/oversized.png");
            var mesh = ResourceLocation.fromNamespaceAndPath("blade_studio_test", "model/oversized.obj");
            assertTrue(assertThrows(IOException.class, () -> resources.open(image)).getMessage().contains("2048"));
            assertTrue(assertThrows(IOException.class, () -> resources.open(mesh)).getMessage().contains("budget"));
        }
    }
}
