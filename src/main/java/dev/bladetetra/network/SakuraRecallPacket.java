package dev.bladetetra.network;

import dev.bladetetra.client.sakura.SakuraCombatVisualController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** One bound server sequence. Stage updates never create another shard pool or damage. */
public record SakuraRecallPacket(UUID owner,String bladeId,int slot,ResourceLocation dimension,
        long sequence,long started,long at,Kind kind,int tier,int target,Vec3 anchor,float height,int releaseTicks) {
    public enum Kind { START, STAGE, RELEASE, HIT, BLOOM, CANCEL }
    public boolean valid() {
        return owner!=null&&bladeId!=null&&!bladeId.isBlank()&&bladeId.length()<=128&&slot>=0&&slot<9
                &&dimension!=null&&sequence>0&&started>=0&&at>=started&&at-started<=180
                &&kind!=null&&tier>=0&&tier<=3&&target>=0&&anchor!=null
                &&Double.isFinite(anchor.x)&&Double.isFinite(anchor.y)&&Double.isFinite(anchor.z)
                &&Math.abs(anchor.x)<=30_000_000&&Math.abs(anchor.z)<=30_000_000&&Math.abs(anchor.y)<=4096
                &&Float.isFinite(height)&&height>=.2F&&height<=16&&releaseTicks>=32&&releaseTicks<=128;
    }
    public static void encode(SakuraRecallPacket p,FriendlyByteBuf b) {
        b.writeUUID(p.owner);b.writeUtf(p.bladeId,128);b.writeByte(p.slot);b.writeResourceLocation(p.dimension);
        b.writeLong(p.sequence);b.writeLong(p.started);b.writeLong(p.at);b.writeEnum(p.kind);
        b.writeByte(p.tier);b.writeVarInt(p.target);b.writeDouble(p.anchor.x);b.writeDouble(p.anchor.y);b.writeDouble(p.anchor.z);b.writeFloat(p.height);b.writeVarInt(p.releaseTicks);
    }
    public static SakuraRecallPacket decode(FriendlyByteBuf b) {
        return new SakuraRecallPacket(b.readUUID(),b.readUtf(128),b.readUnsignedByte(),b.readResourceLocation(),
                b.readLong(),b.readLong(),b.readLong(),b.readEnum(Kind.class),b.readUnsignedByte(),b.readVarInt(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt());
    }
    public static void handle(SakuraRecallPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();
        if(p.valid())ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SakuraCombatVisualController.acceptRecall(p)));
        ctx.setPacketHandled(true);
    }
}
