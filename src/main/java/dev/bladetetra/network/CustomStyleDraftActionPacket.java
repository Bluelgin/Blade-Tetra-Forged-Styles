package dev.bladetetra.network;

import dev.bladetetra.customstyle.CustomStyleScrolls;
import dev.bladetetra.customstyle.CustomStyleSpec;
import dev.bladetetra.customstyle.StyleRecordingSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Bounded client requests. Server mutates only a trusted in-memory draft. */
public record CustomStyleDraftActionPacket(Action action, int position, String value) {
    public enum Action { RENAME, UNDO, CLEAR, FINISH, MOVE_UP, MOVE_DOWN, REMOVE }

    public static void encode(CustomStyleDraftActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.action.ordinal());
        buffer.writeVarInt(packet.position);
        buffer.writeUtf(packet.value, CustomStyleSpec.MAX_NAME_LENGTH);
    }

    public static CustomStyleDraftActionPacket decode(FriendlyByteBuf buffer) {
        int code = buffer.readUnsignedByte();
        int position = buffer.readVarInt();
        String value = buffer.readUtf(CustomStyleSpec.MAX_NAME_LENGTH);
        return new CustomStyleDraftActionPacket(
                code < Action.values().length ? Action.values()[code] : null, position, value);
    }

    public static void handle(CustomStyleDraftActionPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                if (packet.action == null || !player.isAlive()) return;
                boolean finished = false;
                switch (packet.action) {
                    case RENAME -> StyleRecordingSession.rename(player, packet.value);
                    case UNDO -> StyleRecordingSession.undo(player);
                    case CLEAR -> StyleRecordingSession.clear(player);
                    case MOVE_UP -> StyleRecordingSession.move(player, packet.position, -1);
                    case MOVE_DOWN -> StyleRecordingSession.move(player, packet.position, 1);
                    case REMOVE -> StyleRecordingSession.remove(player, packet.position);
                    case FINISH -> {
                        // Finishing commits the currently visible GUI name too.
                        if (!StyleRecordingSession.rename(player, packet.value)) {
                            CustomStyleDraftSyncPacket.send(player, false);
                            return;
                        }
                        CustomStyleSpec spec = StyleRecordingSession.finish(player);
                        if (spec != null) {
                            ItemStack scroll = CustomStyleScrolls.create(spec);
                            if (!player.getInventory().add(scroll)) player.drop(scroll, false);
                            player.containerMenu.broadcastChanges();
                            finished = true;
                        }
                    }
                }
                CustomStyleDraftSyncPacket.send(player, finished);
            });
        }
        context.setPacketHandled(true);
    }
}
