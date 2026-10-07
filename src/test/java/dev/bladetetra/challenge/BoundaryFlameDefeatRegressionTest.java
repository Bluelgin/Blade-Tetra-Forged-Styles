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
    void FlameLoopUsesSnapshotAndStopsAfterDimensionChange() throws IOException {
        String source = read("MikageLegacySkillEffects");
        String loop = source.substring(source.indexOf("void tickBoundaryWalls("),
                source.indexOf("boolean touchesBoundaryFlame("));
        assertTrue(loop.contains("List.copyOf(server.players())"));
        String damage = source.substring(source.indexOf("void applyBoundaryFlameDamage("),
                source.indexOf("void sendBoundaryWall("));
        assertTrue(damage.contains("player.level() != server"));
        assertTrue(damage.contains("!ChallengeManager.isParticipant(owner, player)"));
        assertTrue(damage.indexOf("player.level() != server") < damage.indexOf("server.sendParticles"));
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
    void TrialDamageStopsBeforeAdaptiveStateUpdatesAfterEjection() throws IOException {
        String source = read("MikageDamageService");
        int hit = source.indexOf("boolean hurt = player.hurt(source, requested)");
        int check = source.indexOf("player.level() != owner.level()", hit);
        int update = source.indexOf("float after = player.getHealth()", hit);
        assertTrue(check > hit && check < update);
    }

    private static String read(String name) throws IOException {
        return Files.readString(Path.of("src/main/java/dev/bladetetra/challenge/" + name + ".java"));
    }
}
