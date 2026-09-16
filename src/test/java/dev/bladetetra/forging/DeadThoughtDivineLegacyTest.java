package dev.bladetetra.forging;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadThoughtDivineLegacyTest {
    @Test
    void divineBindingConvergesOnExistingDeadThoughtIdentity() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(DeadThoughtDivineLegacy.isBound(stack));

        DeadThoughtDivineLegacy.bind(stack);

        assertTrue(DeadThoughtDivineLegacy.isBound(stack));
        assertEquals(LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT,
                LegacyFusion.active(stack));
    }
}
