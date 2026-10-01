package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class MikageVisitorDialoguePacketTest {
    @Test
    void ValidBoundaryCountsRoundTripWithoutTrailingBytes() {
        for (int count : new int[]{0, 1, 10}) {
            var packet = packet(count);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                MikageVisitorDialoguePacket.encode(packet, buffer);
                assertEquals(packet, MikageVisitorDialoguePacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }

    @Test
    void InvalidCountsAreRejectedBeforeAllocatingOrReadingOptions() {
        for (int count : new int[]{-1, 11, Integer.MAX_VALUE}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeUtf("node").writeUtf("text").writeUtf("voice").writeUtf("expression");
                buffer.writeVarInt(count);
                assertThrows(DecoderException.class, () -> MikageVisitorDialoguePacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void EncoderRejectsOversizedOptionsIncludingAutomaticallyAddedTopic() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(EncoderException.class, () -> MikageVisitorDialoguePacket.encode(packet(11), buffer));
            var topic = new MikageVisitorDialoguePacket("topics", "text", "voice", "expression", packet(10).options());
            assertThrows(EncoderException.class, () -> MikageVisitorDialoguePacket.encode(topic, buffer));
        } finally { buffer.release(); }
    }

    private static MikageVisitorDialoguePacket packet(int count) {
        List<MikageVisitorDialoguePacket.Option> options = IntStream.range(0, count)
                .mapToObj(i -> new MikageVisitorDialoguePacket.Option("option" + i, "label" + i)).toList();
        return new MikageVisitorDialoguePacket("node", "text", "voice", "expression", options);
    }
}
