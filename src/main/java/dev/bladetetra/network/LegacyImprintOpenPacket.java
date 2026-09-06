package dev.bladetetra.network;

import dev.bladetetra.client.LegacyImprintScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record LegacyImprintOpenPacket(String kind, int booster, boolean protectedBlade,
        dev.bladetetra.forging.LegacyCalibrationProfile profile) {
    public static void encode(LegacyImprintOpenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.kind, 256);
        buffer.writeByte(packet.booster);
        buffer.writeBoolean(packet.protectedBlade);
        buffer.writeNbt(packet.profile.normalized().write());
    }

    public static LegacyImprintOpenPacket decode(FriendlyByteBuf buffer) {
        return new LegacyImprintOpenPacket(buffer.readUtf(256),
                buffer.readUnsignedByte(), buffer.readBoolean(),
                dev.bladetetra.forging.LegacyCalibrationProfile.read(buffer.readNbt()));
    }

    public static void handle(LegacyImprintOpenPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> LegacyImprintScreen.open(packet)));
        context.setPacketHandled(true);
    }
}
