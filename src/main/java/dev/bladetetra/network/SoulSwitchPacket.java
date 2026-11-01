package dev.bladetetra.network;

import dev.bladetetra.easteregg.SoulSwitchService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** A request against the exact held blade and previously acknowledged selection. */
public record SoulSwitchPacket(int slot, UUID blade, int previous) {
    public boolean valid() { return slot >= 0 && slot < 9 && previous >= 1 && previous <= 4; }
    public static void encode(SoulSwitchPacket p, FriendlyByteBuf b) {
        b.writeByte(p.slot); b.writeUUID(p.blade); b.writeByte(p.previous);
    }
    public static SoulSwitchPacket decode(FriendlyByteBuf b) {
        return new SoulSwitchPacket(b.readUnsignedByte(), b.readUUID(), b.readUnsignedByte());
    }
    public static void handle(SoulSwitchPacket p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        if (p.valid() && ctx.getSender() != null)
            ctx.enqueueWork(() -> SoulSwitchService.request(ctx.getSender(), p));
        ctx.setPacketHandled(true);
    }
}
