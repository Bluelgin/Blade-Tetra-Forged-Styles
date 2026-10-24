package dev.bladetetra.aura;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LifengScrollAccessTest {
    @Test void tooltipUsesTwoLocalizedVersesInsteadOfInstructions() throws Exception {
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/blade_tetra/lang/" + locale + ".json"))).getAsJsonObject();
            String first = language.get("tooltip.blade_tetra.lifeng_scroll").getAsString();
            String second = language.get("tooltip.blade_tetra.lifeng_scroll.verse").getAsString();
            assertFalse(first.isBlank());
            assertFalse(second.isBlank());
            assertFalse((first + second).contains("SSS"));
            assertFalse((first + second).contains("右键"));
            if (locale.equals("zh_cn")) {
                assertEquals("刃止于形，锋行于外。", first);
                assertEquals("心随剑至，余势自成离锋。", second);
            }
        }
        String item = Files.readString(Path.of("src/main/java/dev/bladetetra/item/LifengScrollItem.java"));
        assertTrue(item.contains("Component.translatable(\"tooltip.blade_tetra.lifeng_scroll.verse\")"));
    }
    @Test void itemUsesTheApprovedTransparentScrollTexture() throws Exception {
        var model = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/blade_tetra/models/item/lifeng_scroll.json"))).getAsJsonObject();
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("blade_tetra:item/lifeng_scroll", model.getAsJsonObject("textures").get("layer0").getAsString());
        try (var stream = getClass().getResourceAsStream("/assets/blade_tetra/textures/item/lifeng_scroll.png")) {
            assertNotNull(stream);
            var image = javax.imageio.ImageIO.read(stream);
            assertNotNull(image); assertEquals(32, image.getWidth()); assertEquals(32, image.getHeight());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
        }
    }
    @Test void recipeUsesEndMaterialsWithoutDragonBreath() throws Exception {
        var recipe = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/blade_tetra/recipes/lifeng_scroll.json"))).getAsJsonObject();
        Set<String> items = new HashSet<>();
        recipe.getAsJsonObject("key").entrySet().forEach(entry ->
                items.add(entry.getValue().getAsJsonObject().get("item").getAsString()));
        assertEquals(Set.of("minecraft:paper", "minecraft:shulker_shell", "minecraft:end_stone",
                "minecraft:ender_pearl", "slashblade:proudsoul", "minecraft:chorus_flower"), items);
        assertEquals("blade_tetra:lifeng_scroll", recipe.getAsJsonObject("result").get("item").getAsString());
    }
    @Test void studyHasNoWorldDragonGateButStillConsumesOnlyANewLearning() throws Exception {
        String item = Files.readString(Path.of("src/main/java/dev/bladetetra/item/LifengScrollItem.java"));
        String mastery = Files.readString(Path.of("src/main/java/dev/bladetetra/aura/SwordAuraMastery.java"));
        assertFalse(item.contains("worldUnlocked"));
        assertFalse(item.contains("lifeng.locked"));
        assertFalse(mastery.contains("getDragonFight"));
        assertTrue(item.contains("SwordAuraMastery.knows(serverPlayer)"));
        assertTrue(item.contains("if (SwordAuraMastery.learn(serverPlayer))"));
        assertTrue(item.contains("if (!player.getAbilities().instabuild) scroll.shrink(1)"));
        assertTrue(mastery.contains("preserveOnDeath"));
    }
}
