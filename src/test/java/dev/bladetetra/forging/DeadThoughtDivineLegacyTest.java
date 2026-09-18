package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeadThoughtDivineLegacyTest {
    @Test
    void divineBindingConvergesOnExistingDeadThoughtIdentity() {
        assertNull(DeadThoughtDivineLegacy.identity(false));
        assertEquals(LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT,
                DeadThoughtDivineLegacy.identity(true));
    }
}
