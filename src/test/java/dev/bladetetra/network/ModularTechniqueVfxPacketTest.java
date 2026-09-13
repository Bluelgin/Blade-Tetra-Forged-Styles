package dev.bladetetra.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModularTechniqueVfxPacketTest {
    private static final ResourceLocation EFFECT =
            new ResourceLocation("blade_tetra", "test/modular_vfx");

    @Test
    void clampsVisualLifetimeToAReasonableClientBound() {
        assertEquals(1, packet(0).duration());
        assertEquals(ModularTechniqueVfxPacket.MAX_DURATION_TICKS,
                packet(Integer.MAX_VALUE).duration());
    }

    private static ModularTechniqueVfxPacket packet(int duration) {
        return new ModularTechniqueVfxPacket(EFFECT,
                0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D,
                0.0F, 1.0F,
                -1, -1, duration, 42);
    }
}
