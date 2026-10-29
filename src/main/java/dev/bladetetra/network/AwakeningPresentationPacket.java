package dev.bladetetra.network;

import dev.bladetetra.client.awakening.AwakeningVisualController;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C visual authorization; no item/NBT, gameplay resource or client request. */
public record AwakeningPresentationPacket(UUID session, UUID player, String bladeId,
        ResourceLocation dimension, long startedAt, int slot, int soul) {
    public boolean valid() {
        return session != null && player != null && dimension != null && startedAt >= 0
                && bladeId != null && !bladeId.isBlank() && bladeId.length() <= 128
                && slot >= 0 && slot < 9 && AwakeningPresentationTimeline.validSoul(soul);
    }
    public static void encode(AwakeningPresentationPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.session); b.writeUUID(p.player); b.writeUtf(p.bladeId, 128);
        b.writeResourceLocation(p.dimension); b.writeLong(p.startedAt);
        b.writeByte(p.slot); b.writeByte(p.soul);
    }
    public static AwakeningPresentationPacket decode(FriendlyByteBuf b) {
        return new AwakeningPresentationPacket(b.readUUID(), b.readUUID(), b.readUtf(128),
                b.readResourceLocation(), b.readLong(), b.readUnsignedByte(), b.readUnsignedByte());
    }
    public static void handle(AwakeningPresentationPacket p, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (p.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AwakeningVisualController.begin(p));
        });
        ctx.setPacketHandled(true);
    }
}
