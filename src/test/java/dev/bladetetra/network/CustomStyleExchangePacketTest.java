package dev.bladetetra.network;

import dev.bladetetra.customstyle.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomStyleExchangePacketTest {
    @Test void serverPreviewPreservesOrderProviderAndMissingDependencies() {
        var action = new CustomStyleDependencyResolver.ActionInfo(new ResourceLocation("alias_namespace:cut"),
                "real_mod", "Example Provider", "", "[1.2,2)", CustomStyleDependencyResolver.Status.MISSING_MOD);
        var preview = new CustomStyleDependencyResolver.Preview(CustomStyleExchange.Status.OK,
                new CustomStyleSpec("重复招式", List.of(action.action(), action.action())),
                List.of(action, action), List.of("real_mod"));
        var packet = new CustomStyleExchangePacket.Reply(31, CustomStyleExchangePacket.Mode.PREVIEW, "", "", preview);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CustomStyleExchangePacket.Reply.encode(packet, buffer);
            var decoded = CustomStyleExchangePacket.Reply.decode(buffer);
            assertEquals(packet, decoded);
            assertFalse(decoded.preview().allowed());
        } finally { buffer.release(); }
    }
    @Test void requestCodeIsBoundedAndRevisionIsPreserved() {
        var packet = new CustomStyleExchangePacket.Request(2, CustomStyleExchangePacket.Mode.IMPORT, -927, "example_compact_code");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CustomStyleExchangePacket.Request.encode(packet, buffer);
            assertEquals(packet, CustomStyleExchangePacket.Request.decode(buffer));
            assertThrows(RuntimeException.class, () -> CustomStyleExchangePacket.Request.encode(
                    new CustomStyleExchangePacket.Request(2, packet.mode(), 0, "x".repeat(4097)), buffer));
        } finally { buffer.release(); }
    }
}
