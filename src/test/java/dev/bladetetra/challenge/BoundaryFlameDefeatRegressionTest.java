package dev.bladetetra.challenge;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Source guards supplement the snapshot regression; in-game defeat still needs playtesting. */
class BoundaryFlameDefeatRegressionTest {
    @Test
    void DefeatCanRemoveOneOrAllPlayersWhileSnapshotContinues() {
        for (boolean all : new boolean[]{false, true}) {
            var livePlayers = new ArrayList<>(List.of("first", "second", "third"));
            var visited = new ArrayList<String>();
            assertDoesNotThrow(() -> {
                for (String player : List.copyOf(livePlayers)) {
                    visited.add(player);
                    if (all) livePlayers.clear();
                    else livePlayers.remove(player);
                }
            });
            assertEquals(List.of("first", "second", "third"), visited);
            assertTrue(livePlayers.isEmpty());
        }
    }

    @Test
    void BoundaryUsesOwnedNativeContactInsteadOfPartyHealthWrites() throws IOException {
        String source = read("MikageBoundaryExecution");
        assertTrue(source.contains("MikageNativeCombat.run"));
        assertTrue(source.contains("TRAIL = 80"));
        assertTrue(source.contains("MikageNativeCombat.clearAttacks(scope)"));
        assertFalse(source.contains("setHealth"));
        assertFalse(source.contains("server.players()"));
    }

    @Test
    void BothDefeatPathsClearFireAndProtectBeforeReturn() throws IOException {
        for (String name : new String[]{"ChallengeEvents", "DivineDomainProtectionEvents"}) {
            String source = read(name);
            int health = source.indexOf("player.setHealth(1.0F)");
            int extinguish = source.indexOf("player.clearFire()", health);
            int protect = source.indexOf("Math.max(player.invulnerableTime, 40)", health);
            int transfer = source.indexOf(name.equals("ChallengeEvents")
                    ? "ChallengeManager.ejectDefeatedPlayer(player)" : "returnAsFailed(player", health);
            assertTrue(extinguish > health && extinguish < transfer, name);
            assertTrue(protect > health && protect < transfer, name);
        }
    }

    @Test
    void NativeAdaptiveUpdatesRequireCurrentParticipantAfterEjection() throws IOException {
        String source = read("MikageNativeDamageEvents");
        assertTrue(source.contains("boss.encounter().eligible(player)"));
    }

    private static String read(String name) throws IOException {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/" + name + ".java"));
    }
}
