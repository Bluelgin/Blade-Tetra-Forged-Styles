package dev.bladetetra.easteregg;

import dev.bladetetra.network.AwakeningPresentationPacket;
import dev.bladetetra.network.AwakenedBladeVfxPacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Thin authoritative completion facade. Existing awakening rituals call once
 * after unlock writes succeed. Only the player receives full-screen title;
 * nearby observers receive the ordinary world VFX. No unlock logic or client
 * supplied success event lives here.
 */
public final class AwakeningPresentationService {
    public static void success(ServerPlayer player, SoulLegacyState.Legacy soul) {
        if (!AwakenedBladeCombat.replacesLegacy()
                || soul == SoulLegacyState.Legacy.NONE
                || !AwakeningPresentationTimeline.validSoul(soul.ordinal()))
            return;
        Vec3 position = player.position().add(0.0D, 1.1D, 0.0D);
        AwakenedBladeVfxPacket world = new AwakenedBladeVfxPacket(
                soul.ordinal(), AwakenedBladeVfxPacket.AWAKEN,
                position.x, position.y, position.z,
                position.x, position.y, position.z, 0);
        // Both presentations originate on exactly the same server tick.
        for (ServerPlayer viewer : player.serverLevel().players()) {
            if (viewer.distanceToSqr(position) <= 64.0D * 64.0D) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), world);
            }
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new AwakeningPresentationPacket(soul.ordinal()));
    }

    private AwakeningPresentationService() {}
}
