package dev.bladetetra.challenge;

import dev.bladetetra.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.List;
import static dev.bladetetra.challenge.ChallengeManager.*;
import static dev.bladetetra.challenge.MikageNativeSmokeTest.check;

/** Isolated migration regressions; invoked only by the opt-in native smoke profile. */
final class MikageChallengeAccessSmokeTest {
    static void run(ServerPlayer player, ServerPlayer outsider) throws Exception {
        check(!gateModeAvailable(GateMode.NORMAL) && !gateModeAvailable(GateMode.REMINISCENCE),
                "both combat entry modes are sealed");
        check(gateModeAvailable(GateMode.VISITOR), "non-combat visitor mode remains available");
        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean("blade_tetra_mikage_cleared", true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        var charm = new ItemStack(ModItems.BOUNDARY_GATE_CHARM.get());
        player.getInventory().setItem(20, charm);
        data.putString(ORIGIN_DIMENSION, "minecraft:overworld");
        data.putDouble(ORIGIN_X, 7.5); data.putDouble(ORIGIN_Y, 80); data.putDouble(ORIGIN_Z, 9.5);

        // This diagnostic player has a level connection but is not in the server's online player list.
        // Suspension must leave its persistent recovery point intact, like a disconnected old save.
        var old = new ChallengeSession(Long.MIN_VALUE + 70, GateMode.NORMAL, 50);
        old.participants.add(player.getUUID());
        data.putLong(PLAYER_CHALLENGE, old.id);
        var server = player.getServer();
        old.tick(server, player.serverLevel(), server.getLevel(EASTER_REALM));
        check(old.closed && data.contains(ORIGIN_X) && data.contains(PLAYER_CHALLENGE),
                "suspending offline combat preserves its persistent recovery data");
        playerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        check(player.level() == server.overworld() && Math.abs(player.getX() - 7.5) < .001
                && Math.abs(player.getZ() - 9.5) < .001, "old challenge login returns to saved origin");
        check(!data.contains(PLAYER_CHALLENGE) && !data.contains(ORIGIN_DIMENSION),
                "return clears temporary challenge state");
        check(data.getCompound(Player.PERSISTED_NBT_TAG).getBoolean("blade_tetra_mikage_cleared")
                && player.getInventory().getItem(20) == charm && charm.getCount() == 1,
                "migration preserves permanent completion and the exact inventory item");

        var gatesField = ChallengeManager.class.getDeclaredField("GATES"); gatesField.setAccessible(true);
        int before = ((List<?>) gatesField.get(null)).size();
        openGate(player, GateMode.NORMAL); openGate(player, GateMode.REMINISCENCE);
        check(((List<?>) gatesField.get(null)).size() == before,
                "server entry API creates no gates for sealed combat modes");
        var blocked = new ChallengeSession(Long.MIN_VALUE + 71, GateMode.NORMAL, 51);
        blocked.join(player, server);
        check(blocked.participants.isEmpty() && player.level() == server.overworld()
                && !data.contains(PLAYER_CHALLENGE), "direct session joining cannot bypass release gate");

        var invalid = outsider.getPersistentData();
        invalid.putString(ORIGIN_DIMENSION, MIRROR_REALM.location().toString());
        invalid.putDouble(ORIGIN_X, 180); invalid.putDouble(ORIGIN_Y, 65); invalid.putDouble(ORIGIN_Z, -10);
        returnFromPersistentOrigin(outsider);
        var spawn = server.overworld().getSharedSpawnPos();
        check(outsider.level() == server.overworld() && Math.abs(outsider.getX() - spawn.getX() - .5) < .001,
                "invalid realm-to-realm return falls back to overworld spawn instead of trapping player");
    }
    private MikageChallengeAccessSmokeTest() { }
}
