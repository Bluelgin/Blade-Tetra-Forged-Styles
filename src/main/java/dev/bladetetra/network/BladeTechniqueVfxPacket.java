package dev.bladetetra.network;

import dev.bladetetra.client.BladeTechniqueVfxClient;
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
    public static final int PURSUIT_RETURN = 0;
    public static final int MIRROR_DUEL = 1;
    public static final int SEAL_LINK = 2;
    public static final int SEAL_BREAK = 3;
    public static final int SEAL_SUCCESS = 4;
    public static final int SEAL_FAILURE = 5;
    public static final int COUNTER_CLASH = 6;
    public static final int BOUNDARY_STRIKE = 7;
    public static final int TORII_CAGE = 8;
    public static final int CAGE_PULSE = 9;
    public static final int CAGE_SUCCESS = 10;
    public static final int CAGE_FAILURE = 11;
    public static final int BOUNDARY_FLASH = 12;
    public static final int TORII_SWEEP = 13;
    public static final int PURSUIT_LOCK = 14;
    public static final int PURSUIT_SWORD = 15;
    public static final int MOON_ECHO_FIELD = 16;
    public static final int MOON_ECHO_FALSE = 17;
    public static final int MOON_ECHO_TRUE = 18;
    public static final int MOON_ECHO_FAILURE = 19;
    public static final int SWORD_WHEEL = 20;
    public static final int SWORD_WHEEL_BREAK = 21;
    public static final int MIRROR_DUEL_FAILURE = 22;
    public static final int SCISSOR_FEINT = 23;
    public static final int SCISSOR_GUARD = 24;
    public static final int SCISSOR_FAILURE = 25;
    public static final int SCISSOR_BREAK = 26;
    public static final int BOUNDARY_FLASH_RELEASE = 27;
    public static final int BOUNDARY_WALL = 28;
    public static final int BOUNDARY_WALL_BREAK = 29;
    public static final int BOUNDARY_FLASH_IMPACT = 30;
    public static final int BOUNDARY_CHARGE = 31;
    public static final int BOUNDARY_WALL_GAP = 32;
    public static final int BOUNDARY_WALL_GAP_WARNING = 33;
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
                () -> () -> BladeTechniqueVfxClient.spawn(packet)));
        context.setPacketHandled(true);
    }
}
