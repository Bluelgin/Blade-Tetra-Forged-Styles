package dev.bladetetra.network;

import dev.bladetetra.customstyle.CustomStyleSpec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomStyleDraftPacketTest {
    @Test void serverSnapshotPreservesRecordingOrderAndName() {
        var expected = new CustomStyleDraftSyncPacket(false, "Flying Swallow", List.of(
                new ResourceLocation("slashblade", "combo_b1"),
                new ResourceLocation("slashblade", "combo_a1")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CustomStyleDraftSyncPacket.encode(expected, buffer);
            assertEquals(expected, CustomStyleDraftSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void boundedActionPacketRoundTrips() {
        for (var action : CustomStyleDraftActionPacket.Action.values()) {
            var expected = new CustomStyleDraftActionPacket(action, 3, "New Style");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                CustomStyleDraftActionPacket.encode(expected, buffer);
                assertEquals(expected, CustomStyleDraftActionPacket.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test void oversizedSnapshotRejectedBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(false);
            buffer.writeUtf("Name", CustomStyleSpec.MAX_NAME_LENGTH);
            buffer.writeVarInt(10000);
            assertThrows(IllegalArgumentException.class,
                    () -> CustomStyleDraftSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void unknownActionCodeDoesNotResolveToAllowedMutation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(127);
            buffer.writeVarInt(-999);
            buffer.writeUtf("ignored", CustomStyleSpec.MAX_NAME_LENGTH);
            var decoded = CustomStyleDraftActionPacket.decode(buffer);
            assertNull(decoded.action());
        } finally {
            buffer.release();
        }
    }
}
