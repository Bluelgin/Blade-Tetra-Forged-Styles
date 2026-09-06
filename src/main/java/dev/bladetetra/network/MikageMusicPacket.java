package dev.bladetetra.network;

import dev.bladetetra.client.MikageMusicClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MikageMusicPacket(long challengeId, boolean playing) {
    public static MikageMusicPacket start(long challengeId) {
        return new MikageMusicPacket(challengeId, true);
    }

    public static MikageMusicPacket stop() {
        return new MikageMusicPacket(0L, false);
    }

    public static void encode(MikageMusicPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.challengeId);
        buffer.writeBoolean(packet.playing);
    }

    public static MikageMusicPacket decode(FriendlyByteBuf buffer) {
        return new MikageMusicPacket(buffer.readVarLong(), buffer.readBoolean());
    }

    public static void handle(
            MikageMusicPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    if (packet.playing) {
                        MikageMusicClient.start(packet.challengeId);
                    } else {
                        MikageMusicClient.fadeOut();
                    }
                }));
        context.setPacketHandled(true);
    }
}
