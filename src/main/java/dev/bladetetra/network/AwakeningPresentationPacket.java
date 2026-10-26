package dev.bladetetra.network;

import dev.bladetetra.client.AwakeningIntroClient;
import dev.bladetetra.easteregg.AwakeningPresentationTimeline;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Owner-only, S2C confirmed awakening. Never authorizes an unlock/damage;
 * client can only display the introductory title.
 */
public record AwakeningPresentationPacket(int soul) {
    public static void encode(AwakeningPresentationPacket value, FriendlyByteBuf buf) {
        buf.writeByte(value.soul);
    }

    public static AwakeningPresentationPacket decode(FriendlyByteBuf buf) {
        return new AwakeningPresentationPacket(buf.readUnsignedByte());
    }

    public static void handle(AwakeningPresentationPacket packet,
            Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (!AwakeningPresentationTimeline.validSoul(packet.soul())) return;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AwakeningIntroClient.begin(packet.soul()));
        });
        ctx.setPacketHandled(true);
    }
}
