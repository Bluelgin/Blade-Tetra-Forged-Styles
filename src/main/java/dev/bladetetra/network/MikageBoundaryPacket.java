package dev.bladetetra.network;

import dev.bladetetra.client.MikageBoundaryClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Synchronizes the one arena boundary relevant to this client. */
public record MikageBoundaryPacket(long challengeId, boolean active,
        double minX, double maxX, double minZ, double maxZ, double floorY) {
    public static MikageBoundaryPacket show(long challengeId,
            double minX, double maxX, double minZ, double maxZ, double floorY) {
        return new MikageBoundaryPacket(challengeId, true,
                minX, maxX, minZ, maxZ, floorY);
    }

    public static MikageBoundaryPacket hide() {
        return new MikageBoundaryPacket(0L, false, 0, 0, 0, 0, 0);
    }

    public static void encode(MikageBoundaryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.challengeId);
        buffer.writeBoolean(packet.active);
        if (packet.active) {
            buffer.writeDouble(packet.minX);
            buffer.writeDouble(packet.maxX);
            buffer.writeDouble(packet.minZ);
            buffer.writeDouble(packet.maxZ);
            buffer.writeDouble(packet.floorY);
        }
    }

    public static MikageBoundaryPacket decode(FriendlyByteBuf buffer) {
        long challengeId = buffer.readVarLong();
        boolean active = buffer.readBoolean();
        return active
                ? show(challengeId, buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                : hide();
    }

    public static void handle(MikageBoundaryPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (packet.active) {
                MikageBoundaryClient.show(packet.challengeId, packet.minX, packet.maxX,
                        packet.minZ, packet.maxZ, packet.floorY);
            } else {
                MikageBoundaryClient.hide();
            }
        }));
        context.setPacketHandled(true);
    }
}
