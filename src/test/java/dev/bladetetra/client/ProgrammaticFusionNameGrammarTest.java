package dev.bladetetra.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bladetetra.combat.ProgrammaticFusionProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammaticFusionNameGrammarTest {
    @Test
    void everySemanticHasANameInBothBundledLocales() throws Exception {
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            String resource = "/assets/blade_tetra/lang/" + locale + ".json";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                JsonObject translations = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(locale.equals("zh_cn") ? "%1$s·%2$s" : "%1$s · %2$s",
                        translations.get("name.blade_tetra.fusion.format").getAsString());
                checkTerms(translations, "entry", ProgrammaticFusionProfile.Entry.values());
                checkTerms(translations, "response", ProgrammaticFusionProfile.Response.values());
            }
        }
    }

    @Test
    void keepsTranslatedAddonNamesAndFallsBackForNativeOrMissingNames() {
        var semantic = ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO;
        var addon = new ResourceLocation("example", "echo");
        var nativeArt = new ResourceLocation("slashblade", "judgement_cut");
        assertEquals("slash_art.example.echo",
                ProgrammaticFusionNameGrammar.termKey(addon, "response", semantic, key -> true));
        String fallback = "name.blade_tetra.fusion.response.judgement_echo";
        assertEquals(fallback,
                ProgrammaticFusionNameGrammar.termKey(addon, "response", semantic, key -> false));
        assertEquals(fallback,
                ProgrammaticFusionNameGrammar.termKey(nativeArt, "response", semantic, key -> true));
    }

    private static void checkTerms(JsonObject translations, String role, Enum<?>[] semantics) {
        for (Enum<?> semantic : semantics) {
            String key = ProgrammaticFusionNameGrammar.termKey(null, role, semantic, k -> false);
            assertTrue(translations.has(key), key);
            assertTrue(!translations.get(key).getAsString().isBlank(), key);
        }
    }
}
