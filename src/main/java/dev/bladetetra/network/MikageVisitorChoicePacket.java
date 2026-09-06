package dev.bladetetra.network;

import dev.bladetetra.challenge.ChallengeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sends one validated dialogue choice back to the active visitor session. */
public record MikageVisitorChoicePacket(String choice) {
    public static void encode(MikageVisitorChoicePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.choice, 64);
    }

    public static MikageVisitorChoicePacket decode(FriendlyByteBuf buffer) {
        return new MikageVisitorChoicePacket(buffer.readUtf(64));
    }

    public static void handle(MikageVisitorChoicePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ChallengeManager.handleVisitorDialogueChoice(
                    sender, packet.choice));
        }
        context.setPacketHandled(true);
    }
}
