package dev.bladetetra.network;

import dev.bladetetra.client.MikageVisitorDialogueClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Opens or updates Mikage's peaceful visitor dialogue. */
public record MikageVisitorDialoguePacket(String nodeId, String textKey,
        String voiceEvent, String expression, List<Option> options) {
    public record Option(String id, String labelKey) {
    }

    public static void encode(MikageVisitorDialoguePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId, 64);
        buffer.writeUtf(packet.textKey, 192);
        buffer.writeUtf(packet.voiceEvent, 192);
        buffer.writeUtf(packet.expression, 64);
        buffer.writeVarInt(packet.options.size());
        for (Option option : packet.options) {
            buffer.writeUtf(option.id, 64);
            buffer.writeUtf(option.labelKey, 192);
        }
    }

    public static MikageVisitorDialoguePacket decode(FriendlyByteBuf buffer) {
        String nodeId = buffer.readUtf(64);
        String textKey = buffer.readUtf(192);
        String voiceEvent = buffer.readUtf(192);
        String expression = buffer.readUtf(64);
        int size = Math.min(buffer.readVarInt(), 8);
        List<Option> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            options.add(new Option(buffer.readUtf(64), buffer.readUtf(192)));
        }
        return new MikageVisitorDialoguePacket(nodeId, textKey, voiceEvent,
                expression, List.copyOf(options));
    }

    public static void handle(MikageVisitorDialoguePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MikageVisitorDialogueClient.open(packet)));
        context.setPacketHandled(true);
    }
}
