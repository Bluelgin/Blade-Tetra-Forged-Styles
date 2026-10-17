package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** The release contains only lightweight player skins, not the retired tailored rig. */
class MikageBundledAssetsTest {
    @Test void originalSkinsAreValidPlayerTextures() throws Exception {
        for (String name : new String[]{"mikage.png", "mikage_blink.png"}) {
            try (var stream = getClass().getResourceAsStream("/assets/blade_tetra/textures/entity/" + name)) {
                assertNotNull(stream);
                var image = ImageIO.read(stream);
                assertNotNull(image); assertEquals(64, image.getWidth()); assertEquals(64, image.getHeight());
            }
        }
    }
    @Test void retiredArtworkIsNotOnTheRuntimeClasspath() {
        assertNull(getClass().getResource("/assets/blade_tetra/models/entity/mikage_tailored.json"));
        assertNull(getClass().getResource("/assets/blade_tetra/textures/entity/mikage_tailored.png"));
    }
}
