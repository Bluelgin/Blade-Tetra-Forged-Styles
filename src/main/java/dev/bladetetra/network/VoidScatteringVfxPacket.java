package dev.bladetetra.network;

import dev.bladetetra.client.VoidScatteringVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Small state updates used to reconstruct Void Scattering entirely client-side. */
public record VoidScatteringVfxPacket(int type, int playerEntityId,
        int storedSlots, int duration, int seed) {
    public static final int OPEN = 0;
    public static final int CAPTURE = 1;
    public static final int COLLAPSE = 2;
    public static final int CANCEL = 3;
    public static final int RESIDUAL = 4;
    public static final int READY = 5;

    public static void encode(VoidScatteringVfxPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.type);
        buffer.writeVarInt(packet.playerEntityId + 1);
        buffer.writeByte(packet.storedSlots);
        buffer.writeVarInt(packet.duration);
        buffer.writeInt(packet.seed);
    }

    public static VoidScatteringVfxPacket decode(FriendlyByteBuf buffer) {
        return new VoidScatteringVfxPacket(buffer.readUnsignedByte(),
                buffer.readVarInt() - 1, buffer.readUnsignedByte(),
                buffer.readVarInt(), buffer.readInt());
    }

    public static void handle(VoidScatteringVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT, () -> () -> VoidScatteringVfxClient.accept(packet)));
        context.setPacketHandled(true);
    }
}
