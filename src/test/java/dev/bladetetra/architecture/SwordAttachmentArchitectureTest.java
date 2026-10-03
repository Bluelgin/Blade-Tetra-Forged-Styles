package dev.bladetetra.architecture;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SwordAttachmentArchitectureTest {
    @Test void materialCoatingUsesProviderMaterialsWithoutReplacingTheBlade() throws Exception {
        var adapter = source("compat/attachments/MaterialFullerCoating.java");
        assertTrue(adapter.contains("mapped.materials = original.materials"));
        assertTrue(adapter.contains("alias.displayType = SchematicType.improvement"));
        assertTrue(adapter.contains("ModularSlashBladeItem.BLADE_SLOT"));
        assertFalse(adapter.contains("mod_loaded"));
        assertFalse(adapter.contains("generic.attack_damage"));
        assertFalse(adapter.contains("mapped.moduleKey ="));
    }
    private String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/" + file));
    }

    @Test void socketResourceOwnsNoMaterialStats() throws Exception {
        var resource = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/tetra/modules/slashblade/socket_kashira.json"))).getAsJsonObject();
        assertEquals("slashblade/kashira", resource.getAsJsonArray("slots").get(0).getAsString());
        assertEquals(0, resource.getAsJsonArray("variants").size());
        assertEquals("blade_tetra:native_sword_socket", resource.get("type").getAsString());
        var socket = source("compat/attachments/NativeSocketKashira.java");
        assertTrue(socket.contains("nativeData.shallowCopy()"));
        assertFalse(socket.contains("attack_damage"));
    }

    @Test void compositorIsNotTheProviderCompatibilityRegistry() throws Exception {
        var manager = source("client/MaterialTextureManager.java");
        assertTrue(manager.contains("MaterialTextureCompositor.recolor("));
        assertFalse(manager.contains("private static void recolor("));
        assertFalse(manager.contains("more_mod_tetra"));
        assertFalse(source("client/AttachmentFinishPainter.java").contains("ModList"));
        assertTrue(Files.lines(Path.of("src/main/java/dev/bladetetra/client/MaterialTextureManager.java")).count() < 1200);
    }

    @Test void gameplayUsesNativeDispatcherOnceAndDoesNotAddAProcTable() throws Exception {
        var delegate = source("compat/attachments/NativeSwordHitEffects.java");
        assertTrue(delegate.contains("ItemEffectHandler.applyHitEffects(blade, target, attacker)"));
        assertFalse(delegate.contains("nextFloat"));
        var item = source("item/ModularSlashBladeItem.java");
        assertEquals(1, item.split("NativeSwordHitEffects.apply", -1).length - 1);
    }

    @Test void translatedSocketRecipeExistsInBothLanguages() throws Exception {
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/blade_tetra/lang/" + locale + ".json"))).getAsJsonObject();
            assertTrue(translations.has("tetra.module.slashblade/socket_kashira.name"));
            assertTrue(translations.has("tetra/schematic/slashblade/attachments/socket_kashira.name"));
        }
    }
}
