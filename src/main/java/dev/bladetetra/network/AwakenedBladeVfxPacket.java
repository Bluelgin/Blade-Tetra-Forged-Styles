package dev.bladetetra.network;

import dev.bladetetra.client.AwakenedBladeVfxClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-confirmed awakened-blade visual cue. Never carries damage or client authority. */
public record AwakenedBladeVfxPacket(int soul, int type,
        double x, double y, double z, double endX, double endY, double endZ,
        int variant, SakuraCue sakura) {
    public record SakuraCue(java.util.UUID owner, String bladeId, int slot,
            net.minecraft.resources.ResourceLocation dimension, int stage,
            long sequence, long actionAt, int targetId) {
        public boolean matchesHeld(java.util.UUID player, String currentBladeId,
                int observedSlot, boolean localOwner) {
            // Observers receive MAINHAND equipment, not another player's selected
            // hotbar index. Only the local owner can compare a real selected slot.
            return valid() && owner.equals(player) && bladeId.equals(currentBladeId)
                    && (!localOwner || slot == observedSlot);
        }
        public boolean valid() {
            return owner != null && bladeId != null && !bladeId.isBlank() && bladeId.length() <= 128
                    && slot >= 0 && slot < 9 && dimension != null && stage >= 1 && stage <= 8 && sequence > 0
                    && actionAt >= 0 && targetId >= -1;
        }
    }
    public AwakenedBladeVfxPacket(int soul, int type, double x, double y, double z,
            double endX, double endY, double endZ, int variant) {
        this(soul, type, x, y, z, endX, endY, endZ, variant, null);
    }
    public static final int SAKURA_SWING = 5;
    public static final int SAKURA_HIT = 6;
    public static final int SAKURA_DISPERSE = 7, SAKURA_CONDENSE = 8;
    public boolean valid() {
        return soul >= 1 && soul <= 4 && type >= 0 && type <= SAKURA_CONDENSE
                && finitePosition(x, y, z) && finitePosition(endX, endY, endZ)
                && (type < SAKURA_SWING ? sakura == null
                    : soul == 3 && sakura != null && sakura.valid()
                        && (type <= SAKURA_HIT ? sakura.stage() <= 6 : sakura.stage() == type));
    }
    private static boolean finitePosition(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000 && Math.abs(z) <= 30_000_000 && Math.abs(y) <= 4096;
    }
    public static final int AWAKEN = 0;
    public static final int MARK = 1;
    public static final int RELEASE = 2;
    public static final int STEP = 3;
    public static final int STANCE = 4;

    public static void encode(AwakenedBladeVfxPacket value, FriendlyByteBuf buf) {
        buf.writeByte(value.soul);
        buf.writeByte(value.type);
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
        buf.writeDouble(value.endX);
        buf.writeDouble(value.endY);
        buf.writeDouble(value.endZ);
        buf.writeVarInt(value.variant);
        buf.writeBoolean(value.sakura != null);
        if (value.sakura != null) {
            var cue = value.sakura;
            buf.writeUUID(cue.owner()); buf.writeUtf(cue.bladeId(), 128); buf.writeByte(cue.slot());
            buf.writeResourceLocation(cue.dimension()); buf.writeByte(cue.stage());
            buf.writeLong(cue.sequence()); buf.writeLong(cue.actionAt()); buf.writeVarInt(cue.targetId());
        }
    }

    public static AwakenedBladeVfxPacket decode(FriendlyByteBuf buf) {
        int soul = buf.readUnsignedByte(), type = buf.readUnsignedByte();
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        double ex = buf.readDouble(), ey = buf.readDouble(), ez = buf.readDouble();
        int variant = buf.readVarInt();
        SakuraCue cue = buf.readBoolean() ? new SakuraCue(buf.readUUID(), buf.readUtf(128), buf.readUnsignedByte(),
                buf.readResourceLocation(), buf.readUnsignedByte(), buf.readLong(),
                buf.readLong(), buf.readVarInt()) : null;
        return new AwakenedBladeVfxPacket(soul, type, x, y, z, ex, ey, ez, variant, cue);
    }

    public static void handle(AwakenedBladeVfxPacket value,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        if (!value.valid()) { ctx.setPacketHandled(true); return; }
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AwakenedBladeVfxClient.spawn(value)));
        ctx.setPacketHandled(true);
    }
}
