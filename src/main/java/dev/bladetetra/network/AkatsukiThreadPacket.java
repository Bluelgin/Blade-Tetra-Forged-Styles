package dev.bladetetra.network;

import dev.bladetetra.client.AkatsukiThreadVisual;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Bounded server snapshots; visuals never select victims or authorize damage. */
public record AkatsukiThreadPacket(UUID owner,UUID binding,ResourceLocation dimension,long sequence,long age,
        Phase phase,float fuel,List<Target> targets) {
    public enum Phase { BURN, BIND, SNAP, MIST, RETURN, END, DEFLECT }
    public record Target(int id,Vec3 center,float width,float height) {}
    public boolean valid() {
        return owner!=null&&binding!=null&&dimension!=null&&sequence>0&&age>=0&&age<=2400&&phase!=null
                &&Float.isFinite(fuel)&&fuel>=0&&fuel<=1&&targets!=null&&targets.size()<=4&&targets.stream().allMatch(t->
                t.id>=0&&t.center!=null&&Double.isFinite(t.center.x)&&Double.isFinite(t.center.y)&&Double.isFinite(t.center.z)
                &&Math.abs(t.center.x)<=30000000&&Math.abs(t.center.z)<=30000000&&Math.abs(t.center.y)<=4096
                &&Float.isFinite(t.width)&&t.width>0&&t.width<=16&&Float.isFinite(t.height)&&t.height>0&&t.height<=32);
    }
    public static void encode(AkatsukiThreadPacket p,FriendlyByteBuf b) {
        b.writeUUID(p.owner);b.writeUUID(p.binding);b.writeResourceLocation(p.dimension);b.writeLong(p.sequence);
        b.writeVarLong(p.age);b.writeEnum(p.phase);b.writeFloat(p.fuel);b.writeByte(p.targets.size());
        for(var t:p.targets){b.writeVarInt(t.id);b.writeDouble(t.center.x);b.writeDouble(t.center.y);b.writeDouble(t.center.z);b.writeFloat(t.width);b.writeFloat(t.height);}
    }
    public static AkatsukiThreadPacket decode(FriendlyByteBuf b) {
        UUID owner=b.readUUID(),binding=b.readUUID();var dimension=b.readResourceLocation();long seq=b.readLong(),age=b.readVarLong();
        var phase=b.readEnum(Phase.class);float fuel=b.readFloat();int count=b.readUnsignedByte();
        if(count>4)throw new IllegalArgumentException("Too many blood-thread targets");
        var targets=new ArrayList<Target>(count);
        for(int i=0;i<count;i++)targets.add(new Target(b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat()));
        return new AkatsukiThreadPacket(owner,binding,dimension,seq,age,phase,fuel,List.copyOf(targets));
    }
    public static void handle(AkatsukiThreadPacket p,Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();if(p.valid())ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->AkatsukiThreadVisual.accept(p)));
        ctx.setPacketHandled(true);
    }
}
