package dev.bladetetra.network;

import dev.bladetetra.forging.LegacyImprinting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Explicit client request used because cancelling the workbench click also cancels vanilla's packet. */
public record LegacyImprintBeginPacket(BlockPos pos, boolean offhand) {
    public static void encode(LegacyImprintBeginPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeBoolean(packet.offhand);
    }

    public static LegacyImprintBeginPacket decode(FriendlyByteBuf buffer) {
        return new LegacyImprintBeginPacket(buffer.readBlockPos(), buffer.readBoolean());
    }

    public static void handle(LegacyImprintBeginPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> LegacyImprinting.beginFromPacket(sender,
                    packet.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    packet.pos));
        }
        context.setPacketHandled(true);
    }
}
