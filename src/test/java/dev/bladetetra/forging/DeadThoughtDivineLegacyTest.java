package dev.bladetetra.forging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeadThoughtDivineLegacyTest {
    @Test
    void divineBindingConvergesOnExistingDeadThoughtIdentity() {
        assertNull(DeadThoughtDivineLegacy.identity(false));
        assertEquals(LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT,
                DeadThoughtDivineLegacy.identity(true));
    }

    @Test void removalOnlyDeletesTheInjectedMarker() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(DeadThoughtDivineLegacy.BOUND_TAG, true);
        tag.putString("slashblade/blade", "katana_blade");
        tag.putString("other_mod_data", "preserved");
        var expected = tag.copy();
        expected.remove(DeadThoughtDivineLegacy.BOUND_TAG);
        assertTrue(DeadThoughtDivineLegacy.unbindTag(tag));
        assertEquals(expected, tag);
        assertFalse(DeadThoughtDivineLegacy.unbindTag(tag));
    }

    @Test void absentOrFalseMarkersAreNotRemovalTargets() {
        assertFalse(DeadThoughtDivineLegacy.unbindTag(null));
        assertFalse(DeadThoughtDivineLegacy.unbindTag(new net.minecraft.nbt.CompoundTag()));
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(DeadThoughtDivineLegacy.BOUND_TAG, false);
        var original = tag.copy();
        assertFalse(DeadThoughtDivineLegacy.unbindTag(tag));
        assertEquals(original, tag);
    }
}
