package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MikageComboBTracksTest {
    @Test void realNativeSamplesLoadIncludingConstantRotationsAndRetainAllSevenForms() throws Exception {
        var resource = getClass().getResourceAsStream("/assets/blade_tetra/animations/mikage_combo_b.json");
        assertNotNull(resource);
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            var clips = MikageComboBTracks.read(reader);
            for (int stage = 1; stage <= 7; stage++) {
                var start = clips.sample(stage, 0);
                var middle = clips.sample(stage, .1F);
                var end = clips.sample(stage, 10);
                assertTrue(start.containsKey("UpperBody"), "Constant body rotations must load");
                assertTrue(middle.containsKey("RightForeArm"), "Do not freeze the native sword elbow");
                assertTrue(end.containsKey("RightHand"), "Sword socket rotation must survive the finisher");
                for (var value : middle.values()) assertTrue(value.isFinite());
                assertFalse(middle.equals(end), "Every B form must contain sampled motion");
                assertEquals(start, clips.sample(stage, -1), "Early clock clamps to first frame");
            }
            assertTrue(clips.sample(0, 0).isEmpty(), "Standby has no native override");
        }
        try (var license = getClass().getResourceAsStream("/META-INF/licenses/SlashBlade-animation-MIT.txt")) {
            assertNotNull(license);
            assertTrue(new String(license.readAllBytes(), StandardCharsets.UTF_8).contains("Mysterious Mountain"));
        }
    }
}
