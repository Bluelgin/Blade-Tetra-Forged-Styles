package dev.bladetetra.network;

import dev.bladetetra.client.AwakenedMirrorEchoClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A purely visual request: the server reports exactly which actual player
 * combo frame to archive, then where that archived frame may be shown.
 * Never accepts a client->server cast, hit, position, damage or reward.
 */
public record AwakenedMirrorEchoPacket(int action, int entityId, int cutId,
        double x, double y, double z, float yaw) {
    public static final int CAPTURE = 0;
    public static final int PLAY = 1;

    public static void encode(AwakenedMirrorEchoPacket msg, FriendlyByteBuf out) {
        out.writeByte(msg.action);
        out.writeVarInt(msg.entityId);
        out.writeVarInt(msg.cutId);
        out.writeDouble(msg.x);
        out.writeDouble(msg.y);
        out.writeDouble(msg.z);
        out.writeFloat(msg.yaw);
    }

    public static AwakenedMirrorEchoPacket decode(FriendlyByteBuf in) {
        return new AwakenedMirrorEchoPacket(in.readUnsignedByte(), in.readVarInt(),
                in.readVarInt(), in.readDouble(), in.readDouble(), in.readDouble(),
                in.readFloat());
    }

    public static void handle(AwakenedMirrorEchoPacket msg,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AwakenedMirrorEchoClient.receive(msg)));
        ctx.setPacketHandled(true);
    }
}
