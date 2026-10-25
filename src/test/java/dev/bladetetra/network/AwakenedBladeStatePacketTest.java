package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AwakenedBladeStatePacketTest {
    @Test void meterAndStanceRoundTripWithoutLoss() {
        UUID weapon = UUID.fromString("aa8d151c-42af-4e11-8a1a-777777777777");
        for (int soul = 1; soul <= 4; soul++) {
            var expected = new AwakenedBladeStatePacket(weapon, soul,
                    soul, 6, soul == 3);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                AwakenedBladeStatePacket.encode(expected, buf);
                assertEquals(expected, AwakenedBladeStatePacket.decode(buf));
            } finally {
                buf.release();
            }
        }
    }
}
