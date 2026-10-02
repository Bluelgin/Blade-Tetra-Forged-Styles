package dev.bladetetra.client;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Core builds must include the default rig, its atlas and artwork license, without local packs. */
class MikageBundledAssetsTest {
    private static InputStream resource(String path) {
        InputStream stream = MikageBundledAssetsTest.class.getResourceAsStream("/" + path);
        assertNotNull(stream, "Missing bundled resource: " + path);
        return stream;
    }
    @Test void builtInRigContainsRequiredBonesAndEyeShrink() throws Exception {
        JsonObject rig;
        try (var reader = new InputStreamReader(resource("assets/blade_tetra/models/entity/mikage_tailored.json"),
                StandardCharsets.UTF_8)) {
            rig = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals(1, rig.get("version").getAsInt());
        assertEquals(1024, rig.get("textureWidth").getAsInt());
        Map<String,JsonObject> bones = new HashMap<>();
        collect(rig.getAsJsonArray("bones"), bones);
        for (String name : List.of("AllBody","AllHead","UpperBody","LeftArm","RightArm","RightForeArm","RightHand"))
            assertTrue(bones.containsKey(name), "Missing bone: " + name);
        for (String eye : List.of("LeftEyeDot","RightEyeDot")) {
            JsonArray cubes = bones.get(eye).getAsJsonArray("cubes");
            assertFalse(cubes.isEmpty());
            assertEquals(-.35F, cubes.get(0).getAsJsonObject().get("inflate").getAsFloat(), .00001F);
        }
    }
    private static void collect(JsonArray children, Map<String,JsonObject> bones) {
        for (var entry : children) {
            JsonObject bone = entry.getAsJsonObject();
            bones.put(bone.get("name").getAsString(), bone);
            collect(bone.getAsJsonArray("children"), bones);
        }
    }
    @Test void builtInAtlasIsValidAndMatchesRigResolution() throws Exception {
        try (InputStream stream = resource("assets/blade_tetra/textures/entity/mikage_tailored.png")) {
            var image = ImageIO.read(stream);
            assertNotNull(image); assertEquals(1024,image.getWidth()); assertEquals(1024,image.getHeight());
        }
    }
    @Test void licensedArtworkIncludesAttributionAndFullLicense() throws Exception {
        try (InputStream stream = resource("META-INF/notices/mikage-winefox.txt")) {
            String credits = new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(credits.contains("TartaricAcid")); assertTrue(credits.contains("CC BY-NC-SA 4.0"));
        }
        try (InputStream stream = resource("META-INF/licenses/winefox-CC-BY-NC-SA-4.0.txt")) {
            String license = new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(license.contains("Attribution-NonCommercial-ShareAlike 4.0 International"));
            assertTrue(license.length()>10000, "Include the full license, not only a URL");
        }
    }
}
