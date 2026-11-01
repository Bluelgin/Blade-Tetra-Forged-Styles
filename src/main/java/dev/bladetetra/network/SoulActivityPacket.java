package dev.bladetetra.network;

import dev.bladetetra.client.SoulSwitchHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Owner-only, server-confirmed activity. Four soul bits, never combat commands. */
public record SoulActivityPacket(int slot, UUID blade, ResourceLocation dimension, int triggered, int sustained) {
    public boolean valid() {
        return slot >= 0 && slot < 9 && (triggered & ~15) == 0 && (sustained & ~15) == 0;
    }
    public static void encode(SoulActivityPacket p, FriendlyByteBuf b) {
        b.writeByte(p.slot); b.writeUUID(p.blade); b.writeResourceLocation(p.dimension);
        b.writeByte(p.triggered); b.writeByte(p.sustained);
    }
    public static SoulActivityPacket decode(FriendlyByteBuf b) {
        return new SoulActivityPacket(b.readUnsignedByte(), b.readUUID(), b.readResourceLocation(),
                b.readUnsignedByte(), b.readUnsignedByte());
    }
    public static void handle(SoulActivityPacket p, Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> {
            if (p.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SoulSwitchHud.accept(p));
        });
        ctx.setPacketHandled(true);
    }
}
