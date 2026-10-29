package dev.bladetetra.easteregg;

import dev.bladetetra.network.AwakeningPresentationPacket;
import dev.bladetetra.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

/** Existing successful unlocks call once. No server animation loop or item writes. */
public final class AwakeningPresentationService {
    public static void success(ServerPlayer player, SoulLegacyState.Legacy unlocked) {
        if (!AwakenedBladeCombat.replacesLegacy() || unlocked == SoulLegacyState.Legacy.NONE) return;
        var blade = player.getMainHandItem();
        var tag = blade.getTag();
        if (tag == null || tag.getString("id").isBlank()) return;
        // Keep the established primary-soul ordering for dual inscriptions.
        var primary = SoulLegacyState.active(blade);
        if (primary == SoulLegacyState.Legacy.NONE) return;
        var packet = new AwakeningPresentationPacket(UUID.randomUUID(), player.getUUID(),
                tag.getString("id"), player.level().dimension().location(),
                player.level().getGameTime(), player.getInventory().selected, primary.ordinal());
        if (!packet.valid()) return;
        player.containerMenu.broadcastChanges();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
    private AwakeningPresentationService() {}
}
