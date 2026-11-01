package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SakuraRecallPacketTest {
    private static final UUID OWNER=UUID.randomUUID();
    private static SakuraRecallPacket packet(SakuraRecallPacket.Kind kind,int tier,long at,Vec3 anchor,float height) {
        return new SakuraRecallPacket(OWNER,"held-tetra-id",5,new ResourceLocation("minecraft","overworld"),
                73,100,at,kind,tier,42,anchor,height,68);
    }
    @Test void phaseMessagesPreserveWeaponBindingSessionTimeTargetAndCeilingHeight() {
        for(var kind:SakuraRecallPacket.Kind.values()) {
            var expected=packet(kind,3,168,new Vec3(1,2,3),1.25F);
            assertTrue(expected.valid());
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try {SakuraRecallPacket.encode(expected,b);assertEquals(expected,SakuraRecallPacket.decode(b));}
            finally {b.release();}
        }
    }
    @Test void rejectsInvalidPhasesTiersClocksAndUnboundedGeometry() {
        var start=SakuraRecallPacket.Kind.START;
        assertFalse(packet(null,0,100,Vec3.ZERO,3).valid());
        assertFalse(packet(start,4,100,Vec3.ZERO,3).valid());
        assertFalse(packet(start,-1,100,Vec3.ZERO,3).valid());
        assertFalse(packet(start,0,99,Vec3.ZERO,3).valid());
        assertFalse(packet(start,0,281,Vec3.ZERO,3).valid());
        assertFalse(packet(start,0,100,new Vec3(Double.NaN,1,0),3).valid());
        assertFalse(packet(start,0,100,Vec3.ZERO,Float.POSITIVE_INFINITY).valid());
        assertFalse(packet(start,0,100,Vec3.ZERO,20).valid());
    }
}
