package dev.bladetetra.customstyle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomStyleSpecTest {
    private static CustomStyleSpec sample() {
        return new CustomStyleSpec("Flying Swallow", List.of(
                new ResourceLocation("slashblade", "combo_b1"),
                new ResourceLocation("slashblade", "combo_b1"),
                new ResourceLocation("blade_tetra", "dangaku_sweep")));
    }

    @Test void roundTripNbtPreservesOrderAndRepeatedActions() {
        CustomStyleSpec style = sample();
        assertEquals(style, CustomStyleSpec.decode(style.encode()));
        assertEquals(style.actions().get(0), style.actions().get(1));
    }

    @Test void roundTripThroughTetraScrollDetails() {
        CustomStyleSpec style = sample();
        assertEquals(style, CustomStyleScrolls.decodeDetails(CustomStyleScrolls.encodeDetails(style)));
    }

    @Test void tamperedAndMalformedScrollsAreRejected() {
        String encoded = CustomStyleScrolls.encodeDetails(sample());
        assertNull(CustomStyleScrolls.decodeDetails("BAD:" + encoded));
        assertNull(CustomStyleScrolls.decodeDetails(encoded + "!"));
        assertNull(CustomStyleScrolls.decodeDetails("BTCS1:" + "x".repeat(2100)));
    }

    @Test void invalidNbtNeverConstructsStyle() {
        CompoundTag bad = sample().encode();
        bad.putInt("version", CustomStyleSpec.VERSION + 1);
        assertNull(CustomStyleSpec.decode(bad));
        CompoundTag tooMany = sample().encode();
        ListTag list = new ListTag();
        for (int i = 0; i < CustomStyleSpec.MAX_ACTIONS + 1; i++) {
            list.add(StringTag.valueOf("slashblade:combo_b1"));
        }
        tooMany.put("actions", list);
        assertNull(CustomStyleSpec.decode(tooMany));
    }

    @Test void emptyAndOversizedNamesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new CustomStyleSpec("", sample().actions()));
        assertThrows(IllegalArgumentException.class,
                () -> new CustomStyleSpec("x".repeat(CustomStyleSpec.MAX_NAME_LENGTH + 1), sample().actions()));
    }
}
