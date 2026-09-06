package dev.bladetetra.network;

import dev.bladetetra.client.BladeCombatVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BladeCombatVfxPacket(int type, double x, double y, double z,
        float yaw, float intensity, int focusEntityId) {
    public static final int PARRY = 0;
    public static final int PERFECT_GUARD = 1;
    public static final int STAGGER = 2;
    public static final int PHASE_SHIFT = 3;
    public static final int MIKAGE_DEFEAT = 4;

    public static void encode(BladeCombatVfxPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(packet.type);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeFloat(packet.yaw);
        buffer.writeFloat(packet.intensity);
        buffer.writeVarInt(packet.focusEntityId + 1);
    }

    public static BladeCombatVfxPacket decode(FriendlyByteBuf buffer) {
        return new BladeCombatVfxPacket(buffer.readUnsignedByte(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(), buffer.readVarInt() - 1);
    }

    public static void handle(BladeCombatVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> BladeCombatVfxClient.spawn(packet)));
        context.setPacketHandled(true);
    }
}
