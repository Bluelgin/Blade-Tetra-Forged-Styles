package dev.bladetetra.network;

import dev.bladetetra.client.AwakenedBladeVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-confirmed awakened-blade visual cue. Never carries damage or client authority. */
public record AwakenedBladeVfxPacket(int soul, int type,
        double x, double y, double z, double endX, double endY, double endZ,
        int variant) {
    public static final int AWAKEN = 0;
    public static final int MARK = 1;
    public static final int RELEASE = 2;
    public static final int STEP = 3;
    public static final int STANCE = 4;

    public static void encode(AwakenedBladeVfxPacket value, FriendlyByteBuf buf) {
        buf.writeByte(value.soul);
        buf.writeByte(value.type);
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
        buf.writeDouble(value.endX);
        buf.writeDouble(value.endY);
        buf.writeDouble(value.endZ);
        buf.writeVarInt(value.variant);
    }

    public static AwakenedBladeVfxPacket decode(FriendlyByteBuf buf) {
        return new AwakenedBladeVfxPacket(buf.readUnsignedByte(), buf.readUnsignedByte(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt());
    }

    public static void handle(AwakenedBladeVfxPacket value,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AwakenedBladeVfxClient.spawn(value)));
        ctx.setPacketHandled(true);
    }
}
