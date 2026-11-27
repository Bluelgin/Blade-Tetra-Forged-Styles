package dev.bladetetra.network;

import dev.bladetetra.client.BladeTechniqueVfxClient;
import dev.bladetetra.client.BoundaryForgingVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A deterministic description of a longer blade technique effect. Damage and
 * timing remain server-owned; clients only reconstruct the visual timeline.
 */
public record BladeTechniqueVfxPacket(int type,
        double startX, double startY, double startZ,
        double endX, double endY, double endZ,
        float yaw, float intensity,
        int sourceEntityId, int targetEntityId,
        int duration, int seed) {
    public static final int BOUNDARY_STRIKE = 7;
    public static final int BOUNDARY_SUPPRESSION_FLAME = 34;
    public static final int AKATSUKI_FINAL_MOON = 35;
    public static final int AKATSUKI_FINAL_MOON_END = 36;
    public static final int TWIN_FOX_MOONHUNT = 37;
    public static final int TWIN_FOX_MOONHUNT_IMPACT = 38;
    public static final int TWIN_FOX_PURSUIT_MARK = 39;
    public static final int TWIN_FOX_PURSUIT_CROSS = 40;
    public static final int TWIN_PHASE_YASHA = 41;
    public static final int TWIN_PHASE_KIKOUKU = 42;

    public static void encode(BladeTechniqueVfxPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.type);
        buffer.writeDouble(packet.startX);
        buffer.writeDouble(packet.startY);
        buffer.writeDouble(packet.startZ);
        buffer.writeDouble(packet.endX);
        buffer.writeDouble(packet.endY);
        buffer.writeDouble(packet.endZ);
        buffer.writeFloat(packet.yaw);
        buffer.writeFloat(packet.intensity);
        buffer.writeVarInt(packet.sourceEntityId + 1);
        buffer.writeVarInt(packet.targetEntityId + 1);
        buffer.writeVarInt(packet.duration);
        buffer.writeInt(packet.seed);
    }

    public static BladeTechniqueVfxPacket decode(FriendlyByteBuf buffer) {
        return new BladeTechniqueVfxPacket(buffer.readUnsignedByte(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt() - 1, buffer.readVarInt() - 1,
                buffer.readVarInt(), buffer.readInt());
    }

    public static void handle(BladeTechniqueVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    if (packet.type() == BOUNDARY_STRIKE
                            || packet.type() == BOUNDARY_SUPPRESSION_FLAME) {
                        BoundaryForgingVfxClient.spawn(packet);
                    } else {
                        BladeTechniqueVfxClient.spawn(packet);
                    }
                }));
        context.setPacketHandled(true);
    }
}
