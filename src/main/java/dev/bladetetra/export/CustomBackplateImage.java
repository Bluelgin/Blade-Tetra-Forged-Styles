package dev.bladetetra.export;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bladetetra.client.NamedBackplateExportSource;
import dev.bladetetra.client.MaterialTextureManager;
import dev.bladetetra.forging.NamedBackplate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Client-only source of a static, portable durability portrait; never stored in item NBT. */
final class CustomBackplateImage {
    enum Mode { ORIGINAL, REPLACE, OVERLAY }
    record Selection(Mode mode, String filename) {
        Selection {
            if (mode == null) throw new IllegalArgumentException("Missing backplate mode");
            if (filename == null || filename.length() > 1024) throw new IllegalArgumentException("Invalid picture path");
        }
        static Selection original() { return new Selection(Mode.ORIGINAL, ""); }
    }
    record Artwork(byte[] png, ExportResourceEvidence evidence) {}

    static Artwork bake(String modId, Selection selection, ItemStack blade, ResourceManager resources)
            throws IOException {
        if (selection == null) selection = Selection.original();
        boolean named = NamedBackplate.sourceId(blade) != null;
        try (NativeImage original = selection.mode() == Mode.REPLACE ? null
                : named ? NamedBackplateExportSource.bake(blade, resources)
                        : MaterialTextureManager.bakeMaterialBackplate(blade, resources);
             NativeImage custom = selection.mode() == Mode.ORIGINAL ? null : readUserImage(selection.filename());
             NativeImage result = new NativeImage(256, 256, true)) {
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                int base = original == null ? 0 : original.getPixelRGBA(
                        x * original.getWidth() / 256, y * original.getHeight() / 256);
                int top = custom == null ? 0 : custom.getPixelRGBA(x, y);
                result.setPixelRGBA(x, y, over(base, top));
            }
            byte[] png = result.asByteArray();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId, "model/sealed_backplate.png");
            var evidence = new ExportResourceEvidence(id, "sealed-backplate",
                    "bt_export_artwork", "1", BladeExportArchive.hash(png),
                    selection.mode() == Mode.ORIGINAL ? "Original portrait or modular material tint"
                            : "User-selected local picture for private modpack only; user must own rights",
                    true);
            return new Artwork(png, evidence);
        }
    }

    static byte[] readLogo(String filename) throws IOException {
        if (filename.isBlank()) return new byte[0];
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".png")) throw new IOException("Choose a PNG logo");
        try (NativeImage logo = readUserImage(filename)) {
            return logo.asByteArray();
        }
    }

    private static NativeImage readUserImage(String filename) throws IOException {
        if (filename == null || filename.isBlank()) throw new IOException("Choose a PNG or JPG portrait first");
        Path path = Path.of(filename).toRealPath();
        if (!Files.isRegularFile(path) || Files.size(path) > 4 * 1024 * 1024) {
            throw new IOException("Picture must be a regular file of at most 4 MiB");
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) {
            throw new IOException("Only PNG and JPEG portraits are supported");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(4 * 1024 * 1024 + 1);
        }
        if (bytes.length > 4 * 1024 * 1024)
            throw new IOException("Picture grew beyond the 4 MiB input limit");
        BufferedImage image;
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Cannot open image decoder");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported PNG or JPEG encoding");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".png") ? format.equals("png") : format.equals("jpeg"))) {
                    throw new IOException("Picture contents do not match its PNG or JPEG extension");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048) {
                    throw new IOException("Picture dimensions must be between 1 and 2048 pixels");
                }
                image = reader.read(0);
                if (image == null) throw new IOException("Image decoder returned no pixels");
            } finally {
                reader.dispose();
            }
        }
        NativeImage normalized = new NativeImage(256, 256, true);
        // Letterbox while preserving the aspect ratio; PNG transparency is retained.
        double ratio = Math.min(256.0 / image.getWidth(), 256.0 / image.getHeight());
        int w = Math.max(1, (int) Math.round(image.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(image.getHeight() * ratio));
        int left = (256 - w) / 2, top = (256 - h) / 2;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = image.getRGB(Math.min(image.getWidth() - 1, x * image.getWidth() / w),
                    Math.min(image.getHeight() - 1, y * image.getHeight() / h));
            int abgr = (argb & 0xff00ff00) | ((argb & 0x00ff0000) >>> 16)
                    | ((argb & 0x000000ff) << 16);
            normalized.setPixelRGBA(left + x, top + y, abgr);
        }
        return normalized;
    }

    private static int over(int base, int top) {
        int a = top >>> 24;
        if (a == 255) return top;
        if (a == 0) return base;
        int underAlpha = base >>> 24;
        int total = a * 255 + underAlpha * (255 - a);
        if (total == 0) return 0;
        int out = (total + 127) / 255;
        int r = channel(top, 0) * a * 255 + channel(base, 0) * underAlpha * (255 - a);
        int g = channel(top, 8) * a * 255 + channel(base, 8) * underAlpha * (255 - a);
        int b = channel(top, 16) * a * 255 + channel(base, 16) * underAlpha * (255 - a);
        return out << 24 | (b / total) << 16 | (g / total) << 8 | r / total;
    }
    private static int channel(int color, int shift) { return (color >>> shift) & 255; }
    private CustomBackplateImage() {}
}
