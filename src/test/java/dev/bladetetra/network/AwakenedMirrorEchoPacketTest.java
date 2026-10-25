package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AwakenedMirrorEchoPacketTest {
    @Test void captureAndReplayRoundTripWithoutMovingServerGameState() {
        for (int action = 0; action <= 1; action++) {
            AwakenedMirrorEchoPacket packet = new AwakenedMirrorEchoPacket(
                    action, 246, Integer.MAX_VALUE - 2, -24.25D, 81.125D,
                    231.5D, -135.75F);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AwakenedMirrorEchoPacket.encode(packet, buffer);
                assertEquals(packet, AwakenedMirrorEchoPacket.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }
}
