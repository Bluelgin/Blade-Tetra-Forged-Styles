package dev.bladetetra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Participant-only HUD snapshot, at most once per second plus guard/cleanup transitions. */
public record DivineSupportStatePacket(long session, boolean active, int tier, int arrayTicks, int wallTicks, boolean guard, boolean available) {
    public static void encode(DivineSupportStatePacket p,FriendlyByteBuf b) {
        b.writeLong(p.session); b.writeBoolean(p.active); b.writeVarInt(p.tier);
        b.writeVarInt(p.arrayTicks); b.writeVarInt(p.wallTicks); b.writeBoolean(p.guard); b.writeBoolean(p.available);
    }
    public static DivineSupportStatePacket decode(FriendlyByteBuf b) {
        return new DivineSupportStatePacket(b.readLong(),b.readBoolean(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean());
    }
    public static void handle(DivineSupportStatePacket p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get(); context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->dev.bladetetra.client.MikageDivineHud.accept(p))); context.setPacketHandled(true);
    }
}
