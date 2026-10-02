package dev.bladetetra.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MikageBladeVisualsTest {
    @Test void nativeLayerBlacklistMergesWithoutRemovingOtherModsEntries() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/slashblade/tags/entity_types/blacklist/render_layer.json")) {
            assertNotNull(stream);
            var tag = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(tag.get("replace").getAsBoolean());
            Set<String> values = new HashSet<>();
            tag.getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
            assertEquals(Set.of("blade_tetra:mikage", "blade_tetra:mikage_divine_companion",
                    "blade_tetra:mikage_echo"), values);
        }
    }

    @Test void largerSwordKeepsUniformScale() {
        assertEquals(.0045F * 1.25F, MikageBladeVisuals.MODEL_SCALE, 1e-7F);
    }

    @Test void nestedAppearanceScopeRestoresOuterState() {
        assertFalse(MikageBladeVisuals.isRendering());
        MikageBladeVisuals.render(() -> {
            assertTrue(MikageBladeVisuals.isRendering());
            MikageBladeVisuals.render(() -> assertTrue(MikageBladeVisuals.isRendering()));
            assertTrue(MikageBladeVisuals.isRendering());
        });
        assertFalse(MikageBladeVisuals.isRendering());
    }

    @Test void failedBossRenderingCannotTintTheNextPlayerWeapon() {
        assertThrows(IllegalStateException.class, () -> MikageBladeVisuals.render(() -> {
            throw new IllegalStateException("render failure");
        }));
        assertFalse(MikageBladeVisuals.isRendering());
    }
}
