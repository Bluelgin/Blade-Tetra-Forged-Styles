package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AkatsukiThreadPacketTest {
    @Test void boundPhasesRoundTripAndRejectUnboundedGeometry() {
        var targets=List.of(new AkatsukiThreadPacket.Target(1,new Vec3(2,3,4),1,2));
        for(var phase:AkatsukiThreadPacket.Phase.values()) {
            var p=new AkatsukiThreadPacket(UUID.randomUUID(),UUID.randomUUID(),new ResourceLocation("minecraft","overworld"),7,30,phase,.5F,targets);
            assertTrue(p.valid());var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try{AkatsukiThreadPacket.encode(p,buffer);assertEquals(p,AkatsukiThreadPacket.decode(buffer));}finally{buffer.release();}
            assertFalse(new AkatsukiThreadPacket(p.owner(),p.binding(),p.dimension(),7,30,phase,Float.NaN,targets).valid());
            assertFalse(new AkatsukiThreadPacket(p.owner(),p.binding(),p.dimension(),7,30,phase,.5F,Collections.nCopies(5,targets.get(0))).valid());
            assertFalse(new AkatsukiThreadPacket(p.owner(),p.binding(),p.dimension(),7,30,phase,.5F,List.of(new AkatsukiThreadPacket.Target(1,new Vec3(Double.NaN,0,0),1,2))).valid());
        }
    }
}
