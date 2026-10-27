package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakenedBladeVfxPacketTest {
    @Test void encodesAllFourSoulEventsAndNegativeYaw() {
        for (int soul = 1; soul <= 4; soul++) {
            for (int type = 0; type <= 4; type++) {
                var expected = new AwakenedBladeVfxPacket(soul, type,
                        1.5, -3.25, 60.0, 7.0, 8.0, 9.0, -90);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AwakenedBladeVfxPacket.encode(expected, buf);
                    assertEquals(expected, AwakenedBladeVfxPacket.decode(buf));
                } finally {
                    buf.release();
                }
            }
        }
    }
    @Test void sakuraAuthorityRoundTripsIdentityStageSequenceTargetAndTime() {
        for(int stage=1;stage<=6;stage++) for(int type=5;type<=6;type++) {
            var cue=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "tetra-id",
                    new net.minecraft.resources.ResourceLocation("minecraft","overworld"),stage,23,1000,89);
            var packet=new AwakenedBladeVfxPacket(3,type,1,2,3,4,5,6,0,cue);
            assertTrue(packet.valid());
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try { AwakenedBladeVfxPacket.encode(packet,buf); assertEquals(packet,AwakenedBladeVfxPacket.decode(buf)); }
            finally {buf.release();}
        }
    }
    @Test void invalidOrUnboundSakuraAndNonFiniteCoordinatesAreRejected() {
        assertFalse(new AwakenedBladeVfxPacket(3,5,0,0,0,1,1,1,0).valid());
        assertFalse(new AwakenedBladeVfxPacket(3,1,Double.NaN,0,0,1,1,1,0).valid());
        assertFalse(new AwakenedBladeVfxPacket(3,1,0,0,0,Double.POSITIVE_INFINITY,1,1,0).valid());
        for(int stage:new int[]{0,7,255}) {
            var cue=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "id",
                    new net.minecraft.resources.ResourceLocation("minecraft","overworld"),stage,1,0,-1);
            assertFalse(cue.valid());
        }
        var invalid=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "",
                new net.minecraft.resources.ResourceLocation("minecraft","overworld"),1,0,-1,-2);
        assertFalse(invalid.valid());
    }
}
