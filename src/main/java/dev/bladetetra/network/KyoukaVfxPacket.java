package dev.bladetetra.network;

import dev.bladetetra.client.KyoukaVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One tracked mirror-pool or mirror-shatter presentation for Kyouka Suigetsu. */
public record KyoukaVfxPacket(int type, double x, double y, double z,
        int sourceEntityId, int targetEntityId, float scale, int duration, int seed) {
    public static final int REFLECTION_POOL = 0;
    public static final int BREAK_CHARGE = 1;
    public static final int BREAK_SHATTER = 2;

    public static void encode(KyoukaVfxPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.type);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeVarInt(packet.sourceEntityId + 1);
        buffer.writeVarInt(packet.targetEntityId + 1);
        buffer.writeFloat(packet.scale);
        buffer.writeVarInt(packet.duration);
        buffer.writeInt(packet.seed);
    }

    public static KyoukaVfxPacket decode(FriendlyByteBuf buffer) {
        return new KyoukaVfxPacket(buffer.readUnsignedByte(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt() - 1, buffer.readVarInt() - 1,
                buffer.readFloat(), buffer.readVarInt(), buffer.readInt());
    }

    public static void handle(KyoukaVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> KyoukaVfxClient.spawn(packet)));
        context.setPacketHandled(true);
    }
}
