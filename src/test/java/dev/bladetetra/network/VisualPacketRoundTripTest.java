package dev.bladetetra.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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

    @Test
    void mikageMusicStartAndStopRoundTrip() {
        MikageMusicPacket start = MikageMusicPacket.start(7_654_321L);
        assertEquals(start, roundTrip(start,
                MikageMusicPacket::encode,
                MikageMusicPacket::decode));

        MikageMusicPacket stop = MikageMusicPacket.stop();
        assertEquals(stop, roundTrip(stop,
                MikageMusicPacket::encode,
                MikageMusicPacket::decode));
    }

    @Test
    void activeMikageHudRoundTripsAllVisibleState() {
        MikageHudPacket expected = new MikageHudPacket(
                true,
                44L,
                UUID.fromString("12345678-1234-5678-9abc-def012345678"),
                3,
                true,
                17,
                63,
                140);
        assertEquals(expected, roundTrip(expected,
                MikageHudPacket::encode,
                MikageHudPacket::decode));
    }

    @Test
    void hiddenMikageHudUsesCanonicalEmptyPacket() {
        MikageHudPacket expected = MikageHudPacket.hide();
        assertEquals(expected, roundTrip(expected,
                MikageHudPacket::encode,
                MikageHudPacket::decode));
    }

    @Test
    void mikageDialogueRoundTripsTimingAndVoiceFields() {
        MikageDialoguePacket expected = new MikageDialoguePacket(
                "dialogue.blade_tetra.mikage.test",
                "blade_tetra:mikage/test_voice",
                87,
                13);
        assertEquals(expected, roundTrip(expected,
                MikageDialoguePacket::encode,
                MikageDialoguePacket::decode));
    }

    @Test
    void visitorTopicsEncodeKeepsDivineLoreCompatibilityEntry() {
        MikageVisitorDialoguePacket original = new MikageVisitorDialoguePacket(
                "topics",
                "dialogue.blade_tetra.mikage.visitor.topics",
                "",
                "neutral",
                List.of(new MikageVisitorDialoguePacket.Option(
                        "history", "dialogue.blade_tetra.mikage.visitor.history")));
        MikageVisitorDialoguePacket expected = new MikageVisitorDialoguePacket(
                original.nodeId(),
                original.textKey(),
                original.voiceEvent(),
                original.expression(),
                List.of(
                        new MikageVisitorDialoguePacket.Option(
                                "history", "dialogue.blade_tetra.mikage.visitor.history"),
                        new MikageVisitorDialoguePacket.Option("divine_lore", "关于神域")));
        assertEquals(expected, roundTrip(original,
                MikageVisitorDialoguePacket::encode,
                MikageVisitorDialoguePacket::decode));
    }

    @Test
    void voidScatteringVfxRoundTripsSentinelAndImpactCoordinates() {
        VoidScatteringVfxPacket expected = new VoidScatteringVfxPacket(
                VoidScatteringVfxPacket.RESIDUAL_COUNTER,
                -1,
                6,
                160,
                0x2468ACE,
                -12.25F,
                71.5F,
                8.75F);
        assertEquals(expected, roundTrip(expected,
                VoidScatteringVfxPacket::encode,
                VoidScatteringVfxPacket::decode));
    }

    @Test
    void divineSupportStateRoundTrips() {
        DivineSupportStatePacket expected = new DivineSupportStatePacket(
                8080L,
                true,
                4,
                95,
                37,
                true,
                false);
        assertEquals(expected, roundTrip(expected,
                DivineSupportStatePacket::encode,
                DivineSupportStatePacket::decode));
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
