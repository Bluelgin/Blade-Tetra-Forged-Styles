package dev.bladetetra.network;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AwakeningPresentationPacketTest {
    private AwakeningPresentationPacket packet(int slot,int soul,String blade) {
        return new AwakeningPresentationPacket(UUID.randomUUID(),UUID.randomUUID(),blade,
                new ResourceLocation("minecraft","overworld"),12345,slot,soul);
    }
    @Test void fourSoulsRoundTripWithBoundIdentityAndNoItemPayload() {
        for(int soul=1;soul<=4;soul++) {
            var sent=packet(4,soul,"existing-tetra-id");
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try {
                assertTrue(sent.valid()); AwakeningPresentationPacket.encode(sent,b);
                assertTrue(b.readableBytes()<256);
                assertEquals(sent,AwakeningPresentationPacket.decode(b));
                assertEquals(0,b.readableBytes());
            } finally {b.release();}
        }
    }
    @Test void rejectsInvalidIdentitySlotSoulAndOversizedStrings() {
        assertFalse(packet(9,1,"id").valid()); assertFalse(packet(-1,1,"id").valid());
        assertFalse(packet(0,0,"id").valid()); assertFalse(packet(0,5,"id").valid());
        assertFalse(packet(0,1,"").valid()); assertFalse(packet(0,1,"x".repeat(129)).valid());
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(IndexOutOfBoundsException.class,()->AwakeningPresentationPacket.decode(b));
            assertThrows(io.netty.handler.codec.EncoderException.class,
                    ()->AwakeningPresentationPacket.encode(packet(0,1,"x".repeat(129)),b));
        } finally {b.release();}
    }
}
