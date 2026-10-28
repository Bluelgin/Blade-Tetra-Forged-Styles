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
                    assertEquals(expected, AwakenedBladeVfxPacket.decode(buf));
                } finally {
                    buf.release();
                }
            }
        }
    }
}
