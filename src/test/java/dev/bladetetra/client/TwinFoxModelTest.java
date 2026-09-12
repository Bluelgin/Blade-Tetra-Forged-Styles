package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwinFoxModelTest {
    @Test void allThreeNativeMeshesLoadWithSlashBlade() throws Exception {
        for (String name : new String[]{"spirit", "crescent", "seal"}) {
            String path = "/assets/blade_tetra/models/effect/twin_fox/" + name + ".obj";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var model = new WavefrontObject(name, stream);
                assertEquals(1, model.groupObjects.size(), name);
                assertEquals(name, model.groupObjects.get(0).name);
                assertFalse(model.groupObjects.get(0).faces.isEmpty(), name);
            }
        }
    }
}
