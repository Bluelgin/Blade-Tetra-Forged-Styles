package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DivineSupportStatePacketTest {
    @Test void readyBurningSpentAndClosedRoundTrip() {
        for (var packet : new DivineSupportStatePacket[]{
                new DivineSupportStatePacket(1, true, 0, true, false),
                new DivineSupportStatePacket(1, true, 120, false, true),
                new DivineSupportStatePacket(1, true, 0, false, false),
                new DivineSupportStatePacket(1, false, 0, false, false)}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DivineSupportStatePacket.encode(packet, buffer);
                assertEquals(packet, DivineSupportStatePacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
}
