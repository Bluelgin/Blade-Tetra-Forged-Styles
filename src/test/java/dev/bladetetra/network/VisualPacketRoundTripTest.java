package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisualPacketRoundTripTest {
    @Test
    void modularTechniqueVfxRoundTripsAllFieldsAndEntitySentinels() {
        ModularTechniqueVfxPacket expected = new ModularTechniqueVfxPacket(
                new ResourceLocation("blade_tetra", "test/round_trip"),
                1.25D, -3.5D, 9.75D,
                -8.0D, 6.5D, 2.25D,
                143.5F, 0.72F,
                -1, 418, 77, 0x13579BDF);
        assertEquals(expected, roundTrip(expected,
                ModularTechniqueVfxPacket::encode,
                ModularTechniqueVfxPacket::decode));
    }

    @Test
    void bladeCombatVfxRoundTrips() {
        BladeCombatVfxPacket expected = new BladeCombatVfxPacket(
                BladeCombatVfxPacket.PERFECT_GUARD,
                4.5D, 65.0D, -12.25D,
                -37.5F, 1.15F, -1);
        assertEquals(expected, roundTrip(expected,
                BladeCombatVfxPacket::encode,
                BladeCombatVfxPacket::decode));
    }

    @Test
    void kyoukaVfxRoundTrips() {
        KyoukaVfxPacket expected = new KyoukaVfxPacket(
                KyoukaVfxPacket.BREAK_SHATTER,
                -10.5D, 70.25D, 31.75D,
                12, -1, 1.35F, 42, -918273);
        assertEquals(expected, roundTrip(expected,
                KyoukaVfxPacket::encode,
                KyoukaVfxPacket::decode));
    }

    @Test
    void iaidoImpactRoundTrips() {
        IaidoImpactPacket expected = new IaidoImpactPacket(3);
        assertEquals(expected, roundTrip(expected,
                IaidoImpactPacket::encode,
                IaidoImpactPacket::decode));
    }

    @Test
    void activeMikageBoundaryRoundTrips() {
        MikageBoundaryPacket expected = MikageBoundaryPacket.show(
                91L, 100.25D, 211.75D, -67.5D, 47.5D, 64.03D);
        assertEquals(expected, roundTrip(expected,
                MikageBoundaryPacket::encode,
                MikageBoundaryPacket::decode));
    }

    @Test
    void hiddenMikageBoundaryUsesCanonicalEmptyPacket() {
        MikageBoundaryPacket expected = MikageBoundaryPacket.hide();
        assertEquals(expected, roundTrip(expected,
                MikageBoundaryPacket::encode,
                MikageBoundaryPacket::decode));
    }

    private static <T> T roundTrip(T packet, Encoder<T> encoder, Decoder<T> decoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.encode(packet, buffer);
            return decoder.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(T packet, FriendlyByteBuf buffer);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(FriendlyByteBuf buffer);
    }
}
