package dev.bladetetra.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class MikageDialoguePortraitsTest {
    private static ResourceManager resources(Map<ResourceLocation, String> files) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource")) {
                        String content = files.get(args[0]);
                        return content == null ? Optional.empty() : Optional.of(new Resource(null,
                                () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
    @Test void coreDoesNotEnablePortraitsWithoutAnExternalDescriptor() {
        assertTrue(MikageDialoguePortraits.resolve(resources(Map.of()), "soft").isEmpty());
        assertNull(getClass().getResource("/assets/blade_tetra/dialogue/mikage_portraits.json"));
        for (String expression : new String[]{"neutral", "soft", "serious", "distant"}) {
            assertNull(getClass().getResource("/assets/blade_tetra/textures/gui/mikage_dialogue/mikage_"
                    + expression + ".png"), "Core must not package optional dialogue artwork");
        }
    }
    @Test void storyModResourceEnablesPortraitAndUnknownExpressionFallsBack() {
        var texture = new ResourceLocation("story", "textures/gui/mikage.png");
        String descriptor = "{\"width\":512,\"height\":560,\"expressions\":{\"neutral\":\"story:textures/gui/mikage.png\"}}";
        var manager = resources(Map.of(MikageDialoguePortraits.DESCRIPTOR, descriptor, texture, "texture"));
        var portrait = MikageDialoguePortraits.resolve(manager, "soft").orElseThrow();
        assertEquals(texture, portrait.texture());
        assertEquals(512, portrait.width());
        assertEquals(560, portrait.height());
    }
    @Test void malformedMissingOrOversizedAssetsFailClosed() {
        for (String descriptor : new String[]{"not json", "{}",
                "{\"width\":0,\"height\":560,\"expressions\":{}}",
                "{\"width\":8192,\"height\":560,\"expressions\":{}}",
                "{\"width\":512,\"height\":560,\"expressions\":{\"neutral\":\"story:missing.png\"}}"}) {
            assertTrue(MikageDialoguePortraits.resolve(resources(Map.of(MikageDialoguePortraits.DESCRIPTOR, descriptor)), "neutral").isEmpty());
        }
    }
}
