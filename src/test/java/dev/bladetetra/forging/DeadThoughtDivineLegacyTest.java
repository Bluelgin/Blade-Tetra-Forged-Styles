package dev.bladetetra.forging;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadThoughtDivineLegacyTest {
    @Test
    void divineBindingConvergesOnExistingDeadThoughtIdentity() {
        // Use an unregistered bare Item: plain JUnit runs do not bootstrap vanilla's
        // BuiltInRegistries, and this test only needs ItemStack NBT semantics.
        ItemStack stack = new ItemStack(new Item(new Item.Properties()));
        assertFalse(DeadThoughtDivineLegacy.isBound(stack));

        DeadThoughtDivineLegacy.bind(stack);

        assertTrue(DeadThoughtDivineLegacy.isBound(stack));
        assertEquals(LegacyFusion.NIHILUL_SAYA_CRIMSON_CHERRY_HILT,
                LegacyFusion.active(stack));
    }
}
