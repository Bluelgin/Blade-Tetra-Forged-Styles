package dev.bladetetra.network;

import dev.bladetetra.client.kyouka.KyoukaMirrorRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C only. Appearance is included once on entering the target's observer range. */
public record KyoukaMirrorPacket(UUID session, UUID player, UUID weapon, UUID target, int targetId,
        ResourceLocation dimension, ResourceLocation combo, long started, long action,
        float yaw, net.minecraft.world.phys.Vec3 anchor, boolean stop, ItemStack appearance) {
    public static void encode(KyoukaMirrorPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.session); b.writeUUID(p.player); b.writeUUID(p.weapon); b.writeUUID(p.target);
        b.writeVarInt(p.targetId); b.writeResourceLocation(p.dimension); b.writeResourceLocation(p.combo);
        b.writeLong(p.started); b.writeLong(p.action); b.writeFloat(p.yaw); b.writeDouble(p.anchor.x); b.writeDouble(p.anchor.y); b.writeDouble(p.anchor.z); b.writeBoolean(p.stop); b.writeItem(p.appearance);
    }
    public static KyoukaMirrorPacket decode(FriendlyByteBuf b) {
        return new KyoukaMirrorPacket(b.readUUID(), b.readUUID(), b.readUUID(), b.readUUID(), b.readVarInt(),
                b.readResourceLocation(), b.readResourceLocation(), b.readLong(), b.readLong(), b.readFloat(),
                new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readBoolean(), b.readItem());
    }
    public boolean valid() { return started >= 0 && action >= 0 && targetId >= 0 && Float.isFinite(yaw) && Double.isFinite(anchor.x) && Double.isFinite(anchor.y) && Double.isFinite(anchor.z); }
    public static void handle(KyoukaMirrorPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> { if (p.valid()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> KyoukaMirrorRenderer.accept(p)); });
        context.setPacketHandled(true);
    }
}
