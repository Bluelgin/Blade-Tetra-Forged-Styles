package dev.bladetetra.challenge;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DivineDomainItemArtTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/blade_tetra");
    private static final String[] TIERS = {"echo", "grudge", "hundred_ghosts", "asura", "avici"};
    @Test void everyFrozenTierKeepsItsOriginalModelPredicateAndUsesTheNewTexture() throws Exception {
        var base = JsonParser.parseString(Files.readString(ROOT.resolve("models/item/karmic_puppet.json"))).getAsJsonObject();
        assertEquals("blade_tetra:item/karmic_mirror_unmarked", base.getAsJsonObject("textures").get("layer0").getAsString());
        var overrides = base.getAsJsonArray("overrides");
        assertEquals(5, overrides.size());
        for (int i = 0; i < TIERS.length; i++) {
            var entry = overrides.get(i).getAsJsonObject();
            assertEquals(i + 1, entry.getAsJsonObject("predicate").get("custom_model_data").getAsInt());
            String name = "karmic_mirror_" + TIERS[i];
            assertEquals("blade_tetra:item/" + name, entry.get("model").getAsString());
            checkModel(name, name);
        }
        checkModel("dead_thought_soul_seal", "dead_thought_soul_seal");
        checkModel("sword_ghost_remnant", "sword_ghost_remnant");
    }
    private static void checkModel(String model, String texture) throws Exception {
        var data = JsonParser.parseString(Files.readString(ROOT.resolve("models/item/" + model + ".json"))).getAsJsonObject();
        assertEquals("blade_tetra:item/" + texture, data.getAsJsonObject("textures").get("layer0").getAsString());
    }
    @Test void allEightIconsHaveTransparentBordersAndDistinctArtwork() throws Exception {
        var hashes = new java.util.HashSet<Integer>();
        for (String name : new String[]{"karmic_mirror_unmarked", "karmic_mirror_echo", "karmic_mirror_grudge",
                "karmic_mirror_hundred_ghosts", "karmic_mirror_asura", "karmic_mirror_avici", "dead_thought_soul_seal", "sword_ghost_remnant"}) {
            Path texture = ROOT.resolve("textures/item/" + name + ".png");
            var png = ImageIO.read(texture.toFile());
            assertEquals(32, png.getWidth());
            assertEquals(32, png.getHeight());
            assertEquals(0, png.getRGB(0, 0) >>> 24);
            assertEquals(0, png.getRGB(31, 31) >>> 24);
            int[] pixels = png.getRGB(0, 0, 32, 32, null, 0, 32);
            for (int pixel : pixels) assertTrue((pixel >>> 24) == 0 || (pixel >>> 24) == 255, name + " has no antialiased edges");
            assertTrue(hashes.add(java.util.Arrays.hashCode(pixels)), name + " must differ");
            assertTrue(Files.isRegularFile(Path.of("art/svg/divine_domain/" + name + ".svg")));
        }
    }
}
