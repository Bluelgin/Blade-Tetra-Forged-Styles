package dev.bladetetra.network;

import dev.bladetetra.client.MikageBossHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.UUID;

/** Server-authoritative phase and signature-technique state for Mikage's HUD. */
public record MikageHudPacket(boolean active, long challengeId, UUID bossEventId, int phase, boolean reminiscence,
        int technique, int remainingTicks, int totalTicks) {
    public static MikageHudPacket hide() {
        return new MikageHudPacket(false, 0L, new UUID(0L, 0L), 1, false, 0, 0, 0);
    }

    public static void encode(MikageHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        if (packet.active) {
            buffer.writeLong(packet.challengeId);
            buffer.writeUUID(packet.bossEventId);
            buffer.writeByte(packet.phase);
            buffer.writeBoolean(packet.reminiscence);
            buffer.writeByte(packet.technique);
            buffer.writeVarInt(packet.remainingTicks);
            buffer.writeVarInt(packet.totalTicks);
        }
    }

    public static MikageHudPacket decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return hide();
        }
        return new MikageHudPacket(true, buffer.readLong(), buffer.readUUID(), buffer.readUnsignedByte(), buffer.readBoolean(),
                buffer.readUnsignedByte(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(MikageHudPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MikageBossHud.update(packet)));
        context.setPacketHandled(true);
    }
}
