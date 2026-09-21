package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionProfile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgrammaticFusionNameGrammarTest {
    @Test
    void vocabularyCoversEveryCombatSemantic() {
        assertTrue(ProgrammaticFusionNameGrammar.coversEverySemantic());
    }

    @Test
    void nativeNativeUsesBladeTetraGrammar() {
        assertEquals("断界·十文字", ProgrammaticFusionNameGrammar.compose(
                id("slashblade:judgement_cut"),
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                "次元斩",
                id("slashblade:sakura_end"),
                ProgrammaticFusionProfile.Response.SAKURA_CROSS,
                "樱之终焉",
                "zh_cn"));
        assertEquals("Severance · Cross", ProgrammaticFusionNameGrammar.compose(
                id("slashblade:judgement_cut"),
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                "Judgement Cut",
                id("slashblade:sakura_end"),
                ProgrammaticFusionProfile.Response.SAKURA_CROSS,
                "Sakura End",
                "en_us"));
    }

    @Test
    void nativeAddonKeepsAddonSourceNameOnResponseSide() {
        assertEquals("断界·Scarlet Moon Requiem", ProgrammaticFusionNameGrammar.compose(
                id("slashblade:judgement_cut"),
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                "Judgement Cut",
                id("yakumoblade:scarlet_moon_requiem"),
                ProgrammaticFusionProfile.Response.SAKURA_CROSS,
                "Scarlet Moon Requiem",
                "zh_cn"));
    }

    @Test
    void addonNativeKeepsAddonSourceNameOnReleaseSide() {
        assertEquals("Scarlet Moon Requiem · Echo", ProgrammaticFusionNameGrammar.compose(
                id("yakumoblade:scarlet_moon_requiem"),
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                "Scarlet Moon Requiem",
                id("slashblade:judgement_cut"),
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO,
                "Judgement Cut",
                "en_us"));
    }

    @Test
    void addonAddonPreservesBothSourceNames() {
        assertEquals("Scarlet Moon Requiem · Heaven's Judgment",
                ProgrammaticFusionNameGrammar.compose(
                        id("yakumoblade:scarlet_moon_requiem"),
                        ProgrammaticFusionProfile.Entry.SAKURA_END,
                        "Scarlet Moon Requiem",
                        id("slashblade_addon:heavens_judgment"),
                        ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO,
                        "Heaven's Judgment",
                        "en_us"));
    }

    @Test
    void missingAddonSourceNameFallsBackToSemanticGrammar() {
        assertEquals("虚空·轮舞", ProgrammaticFusionNameGrammar.compose(
                id("unknown_addon:nameless_void"),
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                null,
                id("unknown_addon:nameless_ring"),
                ProgrammaticFusionProfile.Response.CIRCLE_RING,
                null,
                "zh_cn"));
    }

    @Test
    void everyOrderedSemanticCombinationStillHasGrammarFallback() {
        for (ProgrammaticFusionProfile.Entry entry
                : ProgrammaticFusionProfile.Entry.values()) {
            for (ProgrammaticFusionProfile.Response response
                    : ProgrammaticFusionProfile.Response.values()) {
                String chinese = ProgrammaticFusionNameGrammar.compose(
                        entry, response, "zh_cn");
                String english = ProgrammaticFusionNameGrammar.compose(
                        entry, response, "en_us");
                assertFalse(chinese.isBlank());
                assertFalse(english.isBlank());
                assertTrue(chinese.contains("·"));
                assertTrue(english.contains(" · "));
            }
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
