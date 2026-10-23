package dev.bladetetra.customstyle;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomStyleExchangeTest {
    private static final CustomStyleSpec STYLE = new CustomStyleSpec("Flying Swallow", List.of(
            new ResourceLocation("slashblade", "combo_a1"),
            new ResourceLocation("slashblade", "combo_b1"),
            new ResourceLocation("slashblade", "combo_a1")));

    @Test void portableCodePreservesNameOrderAndRepeatedMoves() {
        String code = CustomStyleExchange.export(STYLE);
        assertTrue(code.startsWith(CustomStyleExchange.PREFIX));
        assertEquals(STYLE, CustomStyleExchange.inspect(code).style());
        assertEquals(code, CustomStyleExchange.export(CustomStyleExchange.inspect(code).style()));
    }

    @Test void hostileOrOversizedInputsFailClosed() {
        assertEquals(CustomStyleExchange.Status.INVALID_FORMAT, CustomStyleExchange.inspect("random").status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect("BTSTYLE1:%%%%").status());
        assertEquals(CustomStyleExchange.Status.INVALID_FORMAT,
                CustomStyleExchange.inspect("x".repeat(5000)).status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect(encoded("{\"format\":\"blade_tetra:custom_style\",\"version\":1,\"name\":\"unsafe\",\"actions\":[]}")).status());
    }

    @Test void unsupportedSchemaIsDistinctFromCorruptPayload() {
        assertEquals(CustomStyleExchange.Status.UNSUPPORTED_VERSION, CustomStyleExchange.inspect(
                encoded("{\"format\":\"blade_tetra:custom_style\",\"version\":2,\"name\":\"Future\",\"actions\":[\"slashblade:combo_a1\"]}")).status());
    }

    @Test void invalidNameAndActionTypeRejected() {
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT, CustomStyleExchange.inspect(
                encoded("{\"format\":\"blade_tetra:custom_style\",\"version\":1,\"name\":\"X\\nY\",\"actions\":[\"slashblade:combo_a1\"]}")).status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT, CustomStyleExchange.inspect(
                encoded("{\"format\":\"blade_tetra:custom_style\",\"version\":1,\"name\":\"Valid\",\"actions\":[true]}")).status());
    }

    private static String encoded(String payload) {
        return CustomStyleExchange.PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
