package dev.bladetetra.network;

import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.customstyle.StyleRecordingSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server-owned authoring snapshot. Never accepts arbitrary client actions. */
public record CustomStyleDraftSyncPacket(boolean finished, String name, List<ResourceLocation> actions) {
    public static CustomStyleDraftSyncPacket of(StyleRecordingSession.Snapshot snapshot, boolean finished) {
        return new CustomStyleDraftSyncPacket(finished, snapshot.name(), snapshot.actions());
    }

    public static void send(ServerPlayer player, boolean finished) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                of(StyleRecordingSession.snapshot(player), finished));
    }

    public static void encode(CustomStyleDraftSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.finished);
        buffer.writeUtf(packet.name, CustomStyleSpec.MAX_NAME_LENGTH);
        buffer.writeVarInt(packet.actions.size());
        packet.actions.forEach(buffer::writeResourceLocation);
    }

    public static CustomStyleDraftSyncPacket decode(FriendlyByteBuf buffer) {
        boolean done = buffer.readBoolean();
        String name = buffer.readUtf(CustomStyleSpec.MAX_NAME_LENGTH);
        int size = buffer.readVarInt();
        if (size < 0 || size > CustomStyleSpec.MAX_ACTIONS) {
            throw new IllegalArgumentException("Invalid custom style draft length");
        }
        List<ResourceLocation> actions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) actions.add(buffer.readResourceLocation());
        return new CustomStyleDraftSyncPacket(done, name, List.copyOf(actions));
    }

    public static void handle(CustomStyleDraftSyncPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> dev.bladetetra.client.CustomStyleEditorScreen.accept(packet)));
        context.setPacketHandled(true);
    }
}
