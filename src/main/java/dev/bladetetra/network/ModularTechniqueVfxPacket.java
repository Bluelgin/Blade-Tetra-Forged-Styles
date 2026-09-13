package dev.bladetetra.network;

import dev.bladetetra.client.vfx.TechniqueVfxRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Resource-id based VFX envelope for new technique families.
 *
 * <p>The original {@link BladeTechniqueVfxPacket} is intentionally kept as a
 * stable compatibility path for the existing core techniques. New addon and
 * future technique visuals should use this packet so adding a visual does not
 * require another integer constant or another branch in the legacy monolithic
 * renderer.</p>
 *
 * <p>This packet is visual-only. Damage, targeting and timing remain owned by
 * the server-side combat implementation.</p>
 */
public record ModularTechniqueVfxPacket(
        ResourceLocation effectId,
        double startX, double startY, double startZ,
        double endX, double endY, double endZ,
        float yaw, float intensity,
        int sourceEntityId, int targetEntityId,
        int duration, int seed) {

    public ModularTechniqueVfxPacket {
        if (effectId == null) {
            throw new IllegalArgumentException("effectId must not be null");
        }
        duration = Math.max(1, duration);
    }

    public static void encode(
            ModularTechniqueVfxPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.effectId());
        buffer.writeDouble(packet.startX());
        buffer.writeDouble(packet.startY());
        buffer.writeDouble(packet.startZ());
        buffer.writeDouble(packet.endX());
        buffer.writeDouble(packet.endY());
        buffer.writeDouble(packet.endZ());
        buffer.writeFloat(packet.yaw());
        buffer.writeFloat(packet.intensity());
        buffer.writeVarInt(packet.sourceEntityId() + 1);
        buffer.writeVarInt(packet.targetEntityId() + 1);
        buffer.writeVarInt(packet.duration());
        buffer.writeInt(packet.seed());
    }

    public static ModularTechniqueVfxPacket decode(FriendlyByteBuf buffer) {
        return new ModularTechniqueVfxPacket(
                buffer.readResourceLocation(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt() - 1, buffer.readVarInt() - 1,
                buffer.readVarInt(), buffer.readInt());
    }

    public static void handle(
            ModularTechniqueVfxPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> TechniqueVfxRegistry.dispatch(packet)));
        context.setPacketHandled(true);
    }
}
