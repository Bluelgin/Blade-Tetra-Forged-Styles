package dev.bladetetra.network;

import dev.bladetetra.client.MikageDialogueClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Displays one cinematic subtitle and optionally plays its external voice event. */
public record MikageDialoguePacket(String translationKey, String voiceEvent, int holdTicks,
        int delayTicks) {
    public static void encode(MikageDialoguePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.translationKey, 160);
        buffer.writeUtf(packet.voiceEvent, 160);
        buffer.writeVarInt(packet.holdTicks);
        buffer.writeVarInt(packet.delayTicks);
    }

    public static MikageDialoguePacket decode(FriendlyByteBuf buffer) {
        return new MikageDialoguePacket(buffer.readUtf(160), buffer.readUtf(160),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(MikageDialoguePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MikageDialogueClient.show(packet.translationKey,
                        packet.voiceEvent, packet.holdTicks, packet.delayTicks)));
        context.setPacketHandled(true);
    }
}
