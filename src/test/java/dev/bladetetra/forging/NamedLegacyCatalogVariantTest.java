package dev.bladetetra.forging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedLegacyCatalogVariantTest {
    @Test
    void yamatoIntactAndBrokenDefinitionsCollapseToIntactVariant() {
        JsonObject intact = JsonParser.parseString("""
                {
                  "name": "slashblade:yamato",
                  "properties": {
                    "attack_base": 7.0,
                    "sword_type": ["bewitched"]
                  },
                  "render": {
                    "carry_type": "default",
                    "model": "slashblade:model/named/yamato.obj",
                    "texture": "slashblade:model/named/yamato.png"
                  }
                }
                """).getAsJsonObject();
        JsonObject broken = JsonParser.parseString("""
                {
                  "name": "slashblade:yamato",
                  "properties": {
                    "attack_base": 7.0,
                    "sword_type": ["broken", "sealed"]
                  },
                  "render": {
                    "carry_type": "default",
                    "model": "slashblade:model/named/yamato.obj",
                    "texture": "slashblade:model/named/yamato.png"
                  }
                }
                """).getAsJsonObject();

        assertTrue(NamedLegacyCatalog.sameRenderIdentity(intact, broken));
        assertEquals(0, NamedLegacyCatalog.degradedStatePenalty(intact));
        assertEquals(140, NamedLegacyCatalog.degradedStatePenalty(broken));
        assertSame(intact, NamedLegacyCatalog.selectEquivalentRenderVariant(intact, broken));
        assertSame(intact, NamedLegacyCatalog.selectEquivalentRenderVariant(broken, intact));
    }

    @Test
    void duplicateNameWithDifferentRenderIdentityRemainsAConflict() {
        JsonObject first = JsonParser.parseString("""
                {
                  "name": "example:blade",
                  "render": {
                    "model": "example:model/a.obj",
                    "texture": "example:model/a.png"
                  }
                }
                """).getAsJsonObject();
        JsonObject second = JsonParser.parseString("""
                {
                  "name": "example:blade",
                  "render": {
                    "model": "example:model/b.obj",
                    "texture": "example:model/a.png"
                  }
                }
                """).getAsJsonObject();

        assertNull(NamedLegacyCatalog.selectEquivalentRenderVariant(first, second));
    }

    @Test
    void equalRenderVariantsChooseDeterministicallyRegardlessOfScanOrder() {
        JsonObject first = JsonParser.parseString("""
                {
                  "name": "example:blade",
                  "properties": {"attack_base": 5.0},
                  "render": {
                    "model": "example:model/shared.obj",
                    "texture": "example:model/shared.png"
                  }
                }
                """).getAsJsonObject();
        JsonObject second = JsonParser.parseString("""
                {
                  "name": "example:blade",
                  "properties": {"attack_base": 6.0},
                  "render": {
                    "model": "example:model/shared.obj",
                    "texture": "example:model/shared.png"
                  }
                }
                """).getAsJsonObject();

        JsonObject forward = NamedLegacyCatalog.selectEquivalentRenderVariant(first, second);
        JsonObject reverse = NamedLegacyCatalog.selectEquivalentRenderVariant(second, first);
        assertSame(forward, reverse);
    }
}
