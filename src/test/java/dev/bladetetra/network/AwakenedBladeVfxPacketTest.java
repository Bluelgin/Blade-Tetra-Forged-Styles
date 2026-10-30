package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakenedBladeVfxPacketTest {
    @Test void encodesAllFourSoulEventsAndNegativeYaw() {
        for (int soul = 1; soul <= 4; soul++) {
            for (int type = 0; type <= (soul == 4 ? AwakenedBladeVfxPacket.RESONANCE : 4); type++) {
                var expected = new AwakenedBladeVfxPacket(soul, type,
                        1.5, -3.25, 60.0, 7.0, 8.0, 9.0, -90);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    AwakenedBladeVfxPacket.encode(expected, buf);
                    assertTrue(expected.valid());
                    assertEquals(expected, AwakenedBladeVfxPacket.decode(buf));
                } finally {
                    buf.release();
                }
            }
        }
    }
    @Test void sakuraAuthorityRoundTripsIdentityStageSequenceTargetAndTime() {
        for(int stage=1;stage<=6;stage++) for(int type=9;type<=10;type++) {
            var cue=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "tetra-id", 2,
                    new net.minecraft.resources.ResourceLocation("minecraft","overworld"),stage,23,1000,89);
            var packet=new AwakenedBladeVfxPacket(3,type,1,2,3,4,5,6,0,cue);
            assertTrue(packet.valid());
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try { AwakenedBladeVfxPacket.encode(packet,buf); assertEquals(packet,AwakenedBladeVfxPacket.decode(buf)); }
            finally {buf.release();}
        }
    }
    @Test void invalidOrUnboundSakuraAndNonFiniteCoordinatesAreRejected() {
        assertFalse(new AwakenedBladeVfxPacket(3,9,0,0,0,1,1,1,0).valid());
        assertFalse(new AwakenedBladeVfxPacket(3,1,Double.NaN,0,0,1,1,1,0).valid());
        assertFalse(new AwakenedBladeVfxPacket(3,1,0,0,0,Double.POSITIVE_INFINITY,1,1,0).valid());
        for(int stage:new int[]{0,9,255}) {
            var cue=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "id", 0,
                    new net.minecraft.resources.ResourceLocation("minecraft","overworld"),stage,1,0,-1);
            assertFalse(cue.valid());
        }
        var invalid=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(), "", -1,
                new net.minecraft.resources.ResourceLocation("minecraft","overworld"),1,0,-1,-2);
        assertFalse(invalid.valid());
    }
    @Test void observersUseEquipmentIdentityRatherThanAnUnsynchronizedHotbarIndex() {
        var owner=java.util.UUID.randomUUID();
        var cue=new AwakenedBladeVfxPacket.SakuraCue(owner,"actual-tetra-id",6,
                new net.minecraft.resources.ResourceLocation("minecraft","overworld"),3,42,100,-1);
        assertTrue(cue.matchesHeld(owner,"actual-tetra-id",0,false));
        assertFalse(cue.matchesHeld(owner,"actual-tetra-id",0,true));
        assertTrue(cue.matchesHeld(owner,"actual-tetra-id",6,true));
        assertFalse(cue.matchesHeld(java.util.UUID.randomUUID(),"actual-tetra-id",6,false));
        assertFalse(cue.matchesHeld(owner,"different-tetra-id",6,false));
    }
    @Test void thunderTypesCannotEnterSakuraOrOtherSoulHandlers() {
        for (int type = AwakenedBladeVfxPacket.GROUND_SWORD; type <= AwakenedBladeVfxPacket.RESONANCE; type++) {
            assertTrue(new AwakenedBladeVfxPacket(4,type,0,1,0,0,1,0,0).valid());
            for (int soul = 1; soul <= 3; soul++)
                assertFalse(new AwakenedBladeVfxPacket(soul,type,0,1,0,0,1,0,0).valid());
        }
    }
    @Test void stanceCuesRequireTheMatchingActionAndNeverMasqueradeAsBHits() {
        for(int type=7;type<=8;type++) {
            var cue=new AwakenedBladeVfxPacket.SakuraCue(java.util.UUID.randomUUID(),"id",2,
                    new net.minecraft.resources.ResourceLocation("minecraft","overworld"),type,123,100,-1);
            var p=new AwakenedBladeVfxPacket(3,type+4,0,1,0,0,1,0,0,cue);
            assertTrue(p.valid());
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {AwakenedBladeVfxPacket.encode(p,buf);assertEquals(p,AwakenedBladeVfxPacket.decode(buf));}
            finally {buf.release();}
            assertFalse(new AwakenedBladeVfxPacket(3,9,0,1,0,0,1,0,0,cue).valid());
            assertFalse(new AwakenedBladeVfxPacket(3,type==7?12:11,0,1,0,0,1,0,0,cue).valid());
        }
    }
}
