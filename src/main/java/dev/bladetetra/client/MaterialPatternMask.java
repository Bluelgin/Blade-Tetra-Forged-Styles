package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;

/**
 * Small generated grayscale masks used by the material texture generator.
 *
 * <p>The source artwork lives outside the packaged resources. Only the
 * 64-by-64, quantized masks are loaded here, keeping the runtime footprint
 * negligible.</p>
 */
enum MaterialPatternMask {
    MOLTEN("molten"),
    IRONWOOD_LAYERS("ironwood_layers"),
    STEELEAF_VEINS("steeleaf_veins"),
    SPECTRAL_CURSE("spectral_curse"),
    VOID_RUNES("void_runes"),
    CRYSTAL_FACETS("crystal_facets"),
    WITHER_CRACKS("wither_cracks"),
    ARCANE_CIRCUIT("arcane_circuit");

    private static final int FALLBACK = 128;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String resourceName;
    private volatile MaskData data;

    MaterialPatternMask(String resourceName) {
        this.resourceName = resourceName;
    }

    int sample(int x, int y, int seed) {
        MaskData mask = data;
        if (mask == null) {
            synchronized (this) {
                mask = data;
                if (mask == null) {
                    mask = load();
                    data = mask;
                }
            }
        }
        if (mask == MaskData.EMPTY) {
            return FALLBACK;
        }

        int shiftedX = Math.floorMod(x + seed, mask.width());
        int shiftedY = Math.floorMod(y + Integer.rotateRight(seed, 11), mask.height());
        return mask.pixels()[shiftedY * mask.width() + shiftedX] & 0xFF;
    }

    private MaskData load() {
        String path = "/assets/blade_tetra/textures/material_patterns/"
                + resourceName + ".png";
        try (InputStream stream = MaterialPatternMask.class.getResourceAsStream(path)) {
            if (stream == null) {
                LOGGER.warn("Missing material pattern mask: {}", path);
                return MaskData.EMPTY;
            }
            try (NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                byte[] pixels = new byte[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // The masks are grayscale, so every RGB channel carries
                        // the same value regardless of NativeImage byte order.
                        pixels[y * width + x] = (byte) (image.getPixelRGBA(x, y) & 0xFF);
                    }
                }
                return new MaskData(width, height, pixels);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to read material pattern mask: {}", path, exception);
            return MaskData.EMPTY;
        }
    }

    private record MaskData(int width, int height, byte[] pixels) {
        private static final MaskData EMPTY = new MaskData(1, 1, new byte[] {(byte) FALLBACK});
    }
}
