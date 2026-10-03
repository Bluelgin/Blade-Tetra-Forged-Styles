package dev.bladetetra.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Participant-only rescue HUD snapshot. Protocol 12 removes the obsolete array/wall slots. */
public record DivineSupportStatePacket(long session, boolean active, int remainingTicks, boolean ready, boolean rescuing) {
    public static void encode(DivineSupportStatePacket p,FriendlyByteBuf b) {
        b.writeLong(p.session); b.writeBoolean(p.active); b.writeVarInt(p.remainingTicks);
        b.writeBoolean(p.ready); b.writeBoolean(p.rescuing);
    }
    public static DivineSupportStatePacket decode(FriendlyByteBuf b) {
        return new DivineSupportStatePacket(b.readLong(),b.readBoolean(),b.readVarInt(),b.readBoolean(),b.readBoolean());
    }
    public static void handle(DivineSupportStatePacket p,Supplier<NetworkEvent.Context> supplier) {
        var context=supplier.get(); context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->dev.bladetetra.client.MikageDivineHud.accept(p))); context.setPacketHandled(true);
    }
}
