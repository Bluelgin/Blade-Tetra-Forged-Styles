package dev.bladetetra.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.bladetetra.client.KyoukaDecoyVisualPolicy.Mode.*;

class KyoukaDecoyVisualPolicyTest {
    @Test void normalTrackedPlayerKeepsFullEcho() {
        assertEquals(PLAYER_ECHO, KyoukaDecoyVisualPolicy.select(false, true, false));
    }
    @Test void ysmAlwaysUsesStandaloneWaterVisual() {
        assertEquals(WATER_SILHOUETTE, KyoukaDecoyVisualPolicy.select(true, true, false));
        assertEquals(WATER_SILHOUETTE, KyoukaDecoyVisualPolicy.select(true, false, false));
    }
    @Test void missingPlayerMustNotAttemptSkinRendering() {
        assertEquals(WATER_SILHOUETTE, KyoukaDecoyVisualPolicy.select(false, false, false));
    }
    @Test void failedPlayerModelNeverRetries() {
        assertEquals(WATER_SILHOUETTE, KyoukaDecoyVisualPolicy.select(false, true, true));
    }
}
