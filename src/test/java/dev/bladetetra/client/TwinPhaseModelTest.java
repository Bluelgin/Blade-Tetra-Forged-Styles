package dev.bladetetra.client;

import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TwinPhaseModelTest {
    @Test
    void blockbenchMeshLoadsWithAllRuntimeGroups() throws Exception {
        String path = "/assets/blade_tetra/models/effect/twin_phase/twin_phase.obj";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var model = new WavefrontObject("twin_phase", stream);
            assertEquals(Set.of("yasha_slash_arc", "kikoku_broken_ring",
                            "twin_phase_finisher"),
                    model.groupObjects.stream().map(group -> group.name)
                            .collect(Collectors.toSet()));
            model.groupObjects.forEach(group ->
                    assertFalse(group.faces.isEmpty(), group.name));
        }
    }

    @Test
    void dedicatedTexturesAreReadableAndNotFlatPlaceholders() throws Exception {
        for (String name : new String[]{"light", "shadow"}) {
            String path = "/assets/blade_tetra/textures/effect/twin_phase/"
                    + name + ".png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertNotNull(image, path);
                Set<Integer> colors = new HashSet<>();
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        colors.add(image.getRGB(x, y));
                    }
                }
                assertFalse(colors.size() < 4, path + " should retain painted detail");
            }
        }
    }
}
