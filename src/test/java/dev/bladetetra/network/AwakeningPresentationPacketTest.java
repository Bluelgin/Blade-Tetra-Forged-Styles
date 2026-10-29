package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwakeningPresentationPacketTest {
    @Test void allAwakenedSoulsRoundTripAndNoGameplayPayload() {
        for (int soul = 1; soul <= 4; soul++) {
            var sent = new AwakeningPresentationPacket(soul);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AwakeningPresentationPacket.encode(sent, buf);
                assertEquals(sent, AwakeningPresentationPacket.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally {
                buf.release();
            }
        }
    }
}
