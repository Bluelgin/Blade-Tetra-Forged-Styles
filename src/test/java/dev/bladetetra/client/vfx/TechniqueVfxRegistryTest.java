package dev.bladetetra.client.vfx;

import dev.bladetetra.network.ModularTechniqueVfxPacket;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueVfxRegistryTest {
    private static final ResourceLocation TEST_ID =
            new ResourceLocation("blade_tetra", "test/technique_vfx");

    @AfterEach
    void clearRegistry() {
        TechniqueVfxRegistry.clearForTests();
    }

    @Test
    void dispatchesRegisteredHandlerAndRegistrationCanBeClosed() {
        AtomicInteger calls = new AtomicInteger();
        TechniqueVfxRegistry.Registration registration =
                TechniqueVfxRegistry.register(TEST_ID, packet -> calls.incrementAndGet());

        assertTrue(TechniqueVfxRegistry.dispatch(packet(TEST_ID)));
        assertEquals(1, calls.get());
        assertEquals(1, TechniqueVfxRegistry.registeredCount());

        registration.close();
        assertFalse(TechniqueVfxRegistry.dispatch(packet(TEST_ID)));
        assertEquals(0, TechniqueVfxRegistry.registeredCount());
    }

    @Test
    void rejectsDuplicateEffectIds() {
        TechniqueVfxRegistry.register(TEST_ID, packet -> { });
        assertThrows(IllegalStateException.class,
                () -> TechniqueVfxRegistry.register(TEST_ID, packet -> { }));
    }

    @Test
    void unknownOptionalEffectIsIgnoredInsteadOfCrashing() {
        assertFalse(TechniqueVfxRegistry.dispatch(packet(TEST_ID)));
    }

    private static ModularTechniqueVfxPacket packet(ResourceLocation effectId) {
        return new ModularTechniqueVfxPacket(
                effectId,
                0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D,
                0.0F, 1.0F,
                -1, -1,
                8, 42);
    }
}
