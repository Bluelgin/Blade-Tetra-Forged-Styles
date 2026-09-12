package dev.bladetetra.item;

import dev.bladetetra.forging.LegacyFusionGuide;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NamedBladeRecordItemTest {
    @Test
    void generatedPagesMatchCatalogAndContainValidComponents() {
        var fusions = List.of(
                entry("first"), entry("second"), entry("third"));
        var pages = NamedBladeRecordPages.build(fusions);

        assertEquals(6, pages.size());
        for (int index = 0; index < pages.size(); index++) {
            assertNotNull(Component.Serializer.fromJson(pages.getString(index)),
                    "page " + index + " must contain a valid serialized component");
        }
    }

    private static LegacyFusionGuide.Entry entry(String id) {
        return new LegacyFusionGuide.Entry(id, Component.literal(id + " saya"),
                Component.literal(id + " hilt"), Component.literal(id + " ability"));
    }
}
