package dev.bladetetra.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;

/**
 * Maps the curved blade UV islands back into a straight blade coordinate
 * system. Red stores blade-root to blade-tip progress and green stores
 * spine-to-edge progress. This lets procedural material art follow the actual
 * sword instead of drawing horizontal bands across the atlas.
 */
final class BladeCoordinateMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE =
            "/assets/blade_tetra/textures/material_patterns/blade_coordinates.png";
    private static volatile MapData data;

    private BladeCoordinateMap() {
    }

    static Coordinates sample(int x, int y, int imageWidth, int imageHeight) {
        MapData map = data;
        if (map == null) {
            synchronized (BladeCoordinateMap.class) {
                map = data;
                if (map == null) {
                    map = load();
                    data = map;
                }
            }
        }
        if (map == MapData.EMPTY) {
            return Coordinates.INVALID;
        }

        int mappedX = Math.min(
                map.width() - 1,
                Math.max(0, (int) ((x + 0.5F) * map.width() / imageWidth)));
        int mappedY = Math.min(
                map.height() - 1,
                Math.max(0, (int) ((y + 0.5F) * map.height() / imageHeight)));
        int index = mappedY * map.width() + mappedX;
        if ((map.alpha()[index] & 0xFF) == 0) {
            return Coordinates.INVALID;
        }
        return new Coordinates(
                (map.longitudinal()[index] & 0xFF) / 255.0F,
                (map.crossSection()[index] & 0xFF) / 255.0F,
                true);
    }

    private static MapData load() {
        try (InputStream stream = BladeCoordinateMap.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.warn("Missing blade coordinate map: {}", RESOURCE);
                return MapData.EMPTY;
            }
            try (NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                byte[] longitudinal = new byte[width * height];
                byte[] crossSection = new byte[width * height];
                byte[] alpha = new byte[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        int index = y * width + x;
                        // NativeImage exposes PNG pixels in ABGR integer order.
                        longitudinal[index] = (byte) (pixel & 0xFF);
                        crossSection[index] = (byte) ((pixel >>> 8) & 0xFF);
                        alpha[index] = (byte) ((pixel >>> 24) & 0xFF);
                    }
                }
                return new MapData(width, height, longitudinal, crossSection, alpha);
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed to read blade coordinate map: {}", RESOURCE, exception);
            return MapData.EMPTY;
        }
    }

    record Coordinates(float longitudinal, float crossSection, boolean valid) {
        private static final Coordinates INVALID = new Coordinates(0.0F, 0.0F, false);

        float bladeX() {
            return 1.0F + longitudinal * 62.0F;
        }

        float bladeY() {
            return 1.0F + crossSection * 30.0F;
        }
    }

    private record MapData(
            int width,
            int height,
            byte[] longitudinal,
            byte[] crossSection,
            byte[] alpha) {
        private static final MapData EMPTY =
                new MapData(1, 1, new byte[1], new byte[1], new byte[1]);
    }
}
