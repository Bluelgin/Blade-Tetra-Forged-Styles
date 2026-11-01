package dev.bladetetra.network;

import dev.bladetetra.client.SoulSwitchHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Only a server-confirmed success may animate or change the local combat selection. */
public record SoulSwitchReply(int slot, UUID blade, ResourceLocation dimension, int soul, int result) {
    public static final int SUCCESS = 0, COOLDOWN = 1, BUSY = 2, DORMANT = 3;
    public boolean valid() {
        return slot >= 0 && slot < 9 && soul >= 1 && soul <= 4 && result >= 0 && result <= 3;
    }
    public static void encode(SoulSwitchReply p, FriendlyByteBuf b) {
        b.writeByte(p.slot); b.writeUUID(p.blade); b.writeResourceLocation(p.dimension);
        b.writeByte(p.soul); b.writeByte(p.result);
    }
    public static SoulSwitchReply decode(FriendlyByteBuf b) {
        return new SoulSwitchReply(b.readUnsignedByte(), b.readUUID(), b.readResourceLocation(),
                b.readUnsignedByte(), b.readUnsignedByte());
    }
    public static void handle(SoulSwitchReply p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            if (p.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SoulSwitchHud.accept(p));
        });
        ctx.setPacketHandled(true);
    }
}
