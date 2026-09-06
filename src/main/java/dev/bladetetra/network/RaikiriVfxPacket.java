package dev.bladetetra.network;

import dev.bladetetra.client.RaikiriVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One visual-only link in an awakened Raikiri conduction circuit. */
public record RaikiriVfxPacket(double startX, double startY, double startZ,
        double endX, double endY, double endZ, boolean overload, int color,
        int seed) {
    public static void encode(RaikiriVfxPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeDouble(packet.startZ);
        buffer.writeDouble(packet.endX);
        buffer.writeDouble(packet.endY);
        buffer.writeDouble(packet.endZ);
        buffer.writeBoolean(packet.overload);
        buffer.writeInt(packet.color);
        buffer.writeInt(packet.seed);
    }

    public static RaikiriVfxPacket decode(FriendlyByteBuf buffer) {
        return new RaikiriVfxPacket(buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readBoolean(), buffer.readInt(),
                buffer.readInt());
    }

    public static void handle(RaikiriVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> RaikiriVfxClient.spawn(packet)));
        context.setPacketHandled(true);
    }
}
