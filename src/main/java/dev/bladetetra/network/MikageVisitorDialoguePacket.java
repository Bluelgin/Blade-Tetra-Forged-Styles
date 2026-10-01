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
    private static final int MAX_OPTIONS = 10;
    public record Option(String id, String labelKey) {
    }

    public static void encode(MikageVisitorDialoguePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.nodeId, 64);
        buffer.writeUtf(packet.textKey, 512);
        buffer.writeUtf(packet.voiceEvent, 192);
        buffer.writeUtf(packet.expression, 64);
        List<Option> options = packet.nodeId.equals("topics")
                && packet.options.stream().noneMatch(option -> option.id.equals("divine_lore"))
                ? appendDivineTopic(packet.options) : packet.options;
        if (options.size() > MAX_OPTIONS) {
            throw new io.netty.handler.codec.EncoderException("Too many visitor dialogue options");
        }
        buffer.writeVarInt(options.size());
        for (Option option : options) {
            buffer.writeUtf(option.id, 64);
            buffer.writeUtf(option.labelKey, 192);
        }
    }

    private static List<Option> appendDivineTopic(List<Option> original) {
        List<Option> result = new ArrayList<>(original);
        result.add(new Option("divine_lore", "关于神域"));
        return List.copyOf(result);
    }

    public static MikageVisitorDialoguePacket decode(FriendlyByteBuf buffer) {
        String nodeId = buffer.readUtf(64);
        String textKey = buffer.readUtf(512);
        String voiceEvent = buffer.readUtf(192);
        String expression = buffer.readUtf(64);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_OPTIONS) {
            throw new io.netty.handler.codec.DecoderException("Invalid visitor dialogue option count: " + size);
        }
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
