package dev.bladetetra.easteregg;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AkatsukiBloodRulesTest {
    @Test void sheathWaitsUntilTheBloodHasReturnedToTheBody() {
        assertEquals(1,AkatsukiBloodRules.recall(0));assertEquals(0,AkatsukiBloodRules.recall(20));
        assertEquals(0,AkatsukiBloodRules.sheathAfterRecall(20));assertEquals(0,AkatsukiBloodRules.sheathAfterRecall(24));
        assertEquals(1,AkatsukiBloodRules.sheathAfterRecall(28));

    }
}
