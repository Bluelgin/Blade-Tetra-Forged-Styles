package dev.bladetetra.network;

import dev.bladetetra.client.IaidoImpactClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record IaidoImpactPacket(int tier) {
    public static void encode(IaidoImpactPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.tier);
    }

    public static IaidoImpactPacket decode(FriendlyByteBuf buffer) {
        return new IaidoImpactPacket(buffer.readUnsignedByte());
    }

    public static void handle(
            IaidoImpactPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> IaidoImpactClient.trigger(packet.tier)));
        context.setPacketHandled(true);
    }
}
