package dev.bladetetra.network;

import dev.bladetetra.client.AwakenedBladeHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Owner-only authoritative status, never a player-to-server combat request. */
public record AwakenedBladeStatePacket(UUID blade, int soul, int meter,
        int maximum, boolean dispersed, int minimumRank, int rankCost) {
    public static void encode(AwakenedBladeStatePacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.blade);
        buf.writeVarInt(packet.soul);
        buf.writeVarInt(packet.meter);
        buf.writeVarInt(packet.maximum);
        buf.writeBoolean(packet.dispersed);
        buf.writeVarInt(packet.minimumRank);
        buf.writeVarInt(packet.rankCost);
    }

    public static AwakenedBladeStatePacket decode(FriendlyByteBuf buf) {
        return new AwakenedBladeStatePacket(buf.readUUID(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(AwakenedBladeStatePacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AwakenedBladeHud.accept(packet)));
        context.setPacketHandled(true);
    }
}
