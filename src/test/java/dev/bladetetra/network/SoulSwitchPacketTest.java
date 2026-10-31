package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SoulSwitchPacketTest {
    @Test void requestsPreserveExactSlotBladeAndPreviousSelection() {
        for (int slot = 0; slot < 9; slot++) for (int soul = 1; soul <= 4; soul++) {
            var p = new SoulSwitchPacket(slot, UUID.randomUUID(), soul);
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SoulSwitchPacket.encode(p, buf);
                assertEquals(p, SoulSwitchPacket.decode(buf)); assertTrue(p.valid());
            } finally { buf.release(); }
        }
    }
    @Test void repliesKeepDenialSeparateFromSuccessfulSelection() {
        for (int result = 0; result <= 3; result++) {
            var p = new SoulSwitchReply(4, UUID.randomUUID(), new ResourceLocation("minecraft", "overworld"), 3, result);
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SoulSwitchReply.encode(p, buf);
                assertEquals(p, SoulSwitchReply.decode(buf)); assertTrue(p.valid());
            } finally { buf.release(); }
        }
    }
    @Test void outOfRangeWireFieldsAreRejected() {
        var id = UUID.randomUUID();
        for (int soul : new int[] {0, 5, 255}) assertFalse(new SoulSwitchPacket(0, id, soul).valid());
        for (int slot : new int[] {-1, 9, 255}) assertFalse(new SoulSwitchPacket(slot, id, 1).valid());
        assertFalse(new SoulSwitchReply(0, id, new ResourceLocation("minecraft", "overworld"), 1, 4).valid());
    }
}
