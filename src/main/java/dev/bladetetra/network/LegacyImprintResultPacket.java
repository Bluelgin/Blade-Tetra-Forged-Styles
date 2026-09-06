package dev.bladetetra.network;

import dev.bladetetra.forging.LegacyCalibrationProfile;
import dev.bladetetra.forging.LegacyCalibrationProfile.PartTransform;
import dev.bladetetra.forging.LegacyImprinting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record LegacyImprintResultPacket(int accuracy, float tsubaCenter,
        float tsubaRadius, float tsukaCenter, float tsukaRadius,
        PartTransform tsubaTransform, PartTransform tsukaTransform,
        PartTransform sayaTransform, PartTransform bladeTransform, boolean flipped) {
    public static void encode(LegacyImprintResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(Math.max(0, Math.min(100, packet.accuracy)));
        buffer.writeFloat(packet.tsubaCenter);
        buffer.writeFloat(packet.tsubaRadius);
        buffer.writeFloat(packet.tsukaCenter);
        buffer.writeFloat(packet.tsukaRadius);
        writeTransform(buffer, packet.tsubaTransform);
        writeTransform(buffer, packet.tsukaTransform);
        writeTransform(buffer, packet.sayaTransform);
        writeTransform(buffer, packet.bladeTransform);
        buffer.writeBoolean(packet.flipped);
    }

    public static LegacyImprintResultPacket decode(FriendlyByteBuf buffer) {
        return new LegacyImprintResultPacket(buffer.readUnsignedByte(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                readTransform(buffer), readTransform(buffer), readTransform(buffer), readTransform(buffer),
                buffer.readBoolean());
    }

    private static void writeTransform(FriendlyByteBuf buffer, PartTransform value) {
        PartTransform safe = value == null ? LegacyCalibrationProfile.IDENTITY : value.normalized();
        buffer.writeFloat(safe.scale());
        buffer.writeFloat(safe.offsetX());
        buffer.writeFloat(safe.offsetY());
        buffer.writeFloat(safe.rotation());
        buffer.writeFloat(safe.length());
        buffer.writeFloat(safe.width());
    }

    private static PartTransform readTransform(FriendlyByteBuf buffer) {
        return new PartTransform(buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()).normalized();
    }

    public static void handle(LegacyImprintResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> LegacyImprinting.finish(sender, packet.accuracy,
                    new LegacyCalibrationProfile(packet.tsubaCenter, packet.tsubaRadius,
                            packet.tsukaCenter, packet.tsukaRadius,
                            packet.tsubaTransform, packet.tsukaTransform,
                            packet.sayaTransform, packet.bladeTransform, packet.flipped, 0L).normalized()));
        }
        context.setPacketHandled(true);
    }
}
