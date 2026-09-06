package dev.bladetetra.forging;

import dev.bladetetra.network.LegacyImprintOpenPacket;
import dev.bladetetra.network.LegacyImprintResultPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAssemblyPacketTest {
    @Test void independentAssemblyControlsSurviveBothPackets() {
        var fitting = new LegacyCalibrationProfile.PartTransform(1.05F, .01F, -.03F, 4, 1.3F, .8F);
        var blade = new LegacyCalibrationProfile.PartTransform(1, 0, 0, 0, 1.1F, .9F);
        var profile = new LegacyCalibrationProfile(.77F, .025F, .90F, .1F,
                fitting, fitting, fitting, blade, true, 123);
        var open = new LegacyImprintOpenPacket("slashblade/yamato", 5, true, profile);
        var result = new LegacyImprintResultPacket(80, .77F, .025F, .90F, .1F,
                fitting, fitting, fitting, blade, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LegacyImprintOpenPacket.encode(open, buffer);
            assertEquals(open, LegacyImprintOpenPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
            buffer.clear();
            LegacyImprintResultPacket.encode(result, buffer);
            assertEquals(result, LegacyImprintResultPacket.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
