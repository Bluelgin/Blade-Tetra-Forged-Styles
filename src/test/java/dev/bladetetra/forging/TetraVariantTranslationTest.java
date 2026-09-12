package dev.bladetetra.forging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetraVariantTranslationTest {
    private static final List<String> FIXED_MODULES = List.of(
            "soul_inscription",
            "awakened_soul_inscription",
            "fox_saya",
            "fox_tsuba",
            "tsubaless");

    @Test
    void everyFixedVariantHasEnglishAndChineseNames() throws Exception {
        JsonObject english = resource("/assets/blade_tetra/lang/en_us.json");
        JsonObject chinese = resource("/assets/blade_tetra/lang/zh_cn.json");

        for (String moduleName : FIXED_MODULES) {
            JsonObject module = resource(
                    "/data/tetra/modules/slashblade/" + moduleName + ".json");
            module.getAsJsonArray("variants").forEach(element -> {
                String variant = element.getAsJsonObject().get("key").getAsString();
                if (variant.endsWith("/")) {
                    return;
                }
                String translation = "tetra.variant." + variant;
                assertTrue(english.has(translation), "missing English " + translation);
                assertTrue(chinese.has(translation), "missing Chinese " + translation);
            });
        }
    }

    private static JsonObject resource(String path) throws Exception {
        var stream = TetraVariantTranslationTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing test resource " + path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
