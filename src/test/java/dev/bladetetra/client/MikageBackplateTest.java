package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MikageBackplateTest {
    @Test
    void NativeFrameColorIsNotResetUntilAfterFrameIsDrawn() throws Exception {
        String manager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        int start = manager.indexOf("if (\"base\".equals(event.getOriginalTarget()))");
        int frame = manager.indexOf("DefaultResources.resourceDurabilityTexture,", start);
        int reset = manager.indexOf("BladeRenderState.resetCol();", start);
        int portrait = manager.indexOf("event.getModel(), \"portrait\"", start);
        assertTrue(frame > start && reset > frame && reset < portrait);
    }

    @Test
    void FlowBackplateIsPackagedWithTransparentCorners() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/blade_tetra/textures/item/flow_backplate.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertEquals(255, image.getRGB(128, 128) >>> 24);
        }
    }

    @Test
    void ActiveAkatsukiSelectsPortraitAndOtherBladesSelectFlowWithSeparateCacheKeys() throws Exception {
        String manager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        assertTrue(manager.contains("AkatsukiAwakening.isActive(event.getStack())"));
        assertTrue(manager.contains("akatsuki ? \"|mikage\" : \"|flow\""));
        assertTrue(manager.contains("if (textureLayout == TextureLayout.ITEM)"));
        String cache = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureCache.java"));
        assertTrue(cache.contains("akatsuki ? portraitTemplate : flowTemplate"));
        assertTrue(cache.contains("flowTemplate.close()"));
    }

    @Test
    void ObjPortraitDoesNotUseVanillaQuadRenderTypeOrBlurFiltering() throws Exception {
        String renderType = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/dev/bladetetra/client/MikageBackplateRenderType.java"));
        assertTrue(renderType.contains("VertexFormat.Mode.TRIANGLES"));
        assertTrue(renderType.contains("new TextureStateShard(texture, false, false)"));
        assertTrue(renderType.contains("POSITION_COLOR_TEX_SHADER"));
        assertTrue(renderType.contains("setTransparencyState(TRANSLUCENT_TRANSPARENCY)"));
        String manager = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/dev/bladetetra/client/MaterialTextureManager.java"));
        assertTrue(manager.contains("MikageBackplateRenderType::get"));
        assertFalse(manager.contains("RenderType::entityTranslucentEmissive"));
    }

    @Test
    void HairMaskLeavesWarmSkinAndRedDetailsUnchanged() {
        assertTrue(MikageBackplateTint.isHair(240, 210, 224));
        assertTrue(MikageBackplateTint.isHair(185, 126, 152));
        assertFalse(MikageBackplateTint.isHair(255, 213, 189));
        assertFalse(MikageBackplateTint.isHair(238, 26, 39));
        assertFalse(MikageBackplateTint.isHair(79, 19, 30));
        assertFalse(MikageBackplateTint.isHair(237, 177, 73));
    }

    @Test
    void PortraitHasRealTransparencyAndHighResolution() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/blade_tetra/textures/item/mikage_backplate.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            assertEquals(image.getWidth(), image.getHeight());
            assertTrue(image.getWidth() >= 256);
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertTrue((image.getRGB(image.getWidth()/2, image.getHeight()/2) >>> 24) > 0);
        }
    }

    @Test
    void PortraitUsesSeparateFullImageUvsWithoutReplacingNativeGauges() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/blade_tetra/model/util/durability_filled.obj")) {
            assertNotNull(stream);
            String model = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(model.contains("g portrait\nf 29/25/1 32/28/1 31/27/1"));
            assertTrue(model.contains("g color\nf 12/11/1 11/10/1 10/9/1"));
            assertTrue(model.contains("g color_r\nf 22/19/1 21/18/1 20/17/1"));
            String base = model.substring(model.indexOf("g base"), model.indexOf("g portrait"));
            assertEquals(8, base.lines().filter(line -> line.startsWith("f ")).count());
            assertEquals(32, model.lines().filter(line -> line.startsWith("v ")).count());
        }
    }
}
