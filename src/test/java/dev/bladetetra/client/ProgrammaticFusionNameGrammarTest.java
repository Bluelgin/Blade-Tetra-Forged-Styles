package dev.bladetetra.client;

import dev.bladetetra.combat.ProgrammaticFusionProfile;
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
    void composesChineseAndEnglishNamesFromTheSameSemanticPair() {
        assertEquals("断界·十文字", ProgrammaticFusionNameGrammar.compose(
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS,
                "zh_cn"));
        assertEquals("Severance · Cross", ProgrammaticFusionNameGrammar.compose(
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS,
                "en_us"));
    }

    @Test
    void everyOrderedSemanticCombinationProducesAConcreteName() {
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
}
