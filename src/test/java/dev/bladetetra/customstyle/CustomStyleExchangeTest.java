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

    @Test void optionalRequirementsRemainDataAndOldCodesRemainUnchanged() {
        String payload = "{\"format\":\"blade_tetra:custom_style\",\"version\":1,\"name\":\"Addon\","
                + "\"actions\":[\"alias_namespace:ordinary_cut\"],\"requirements\":[{\"modId\":\"actual_mod\",\"displayName\":\"Example\"}]}";
        var parsed = CustomStyleExchange.inspect(encoded(payload));
        assertTrue(parsed.accepted());
        assertEquals("actual_mod", parsed.requirements().get(0).modId());
        assertEquals("alias_namespace", parsed.style().actions().get(0).getNamespace());
        assertEquals(List.of(), CustomStyleExchange.inspect(CustomStyleExchange.export(STYLE)).requirements());
        assertTrue(CustomStyleExchange.inspect(encoded(payload.replace("Example", "Untrusted label"))).accepted());
    }

    @Test void fractionalVersionsAndMalformedRequirementTypesAreRejected() {
        String valid = new String(Base64.getUrlDecoder().decode(CustomStyleExchange.export(STYLE)
                .substring(CustomStyleExchange.PREFIX.length())), StandardCharsets.UTF_8);
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect(encoded(valid.replace("\"version\":1", "\"version\":1.5"))).status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect(encoded(valid.substring(0, valid.length()-1) + ",\"requirements\":[true]}")).status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect(encoded(valid.substring(0, valid.length()-1) + ",\"requirements\":[{\"modId\":\"https://bad\"}]}")).status());
    }

    @Test void damagedUtf8AndOversizedDecodedJsonAreRejected() {
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT, CustomStyleExchange.inspect(CustomStyleExchange.PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{(byte)0xc3, 0x28})).status());
        assertEquals(CustomStyleExchange.Status.INVALID_CONTENT,
                CustomStyleExchange.inspect(encoded(" ".repeat(2049))).status());
    }

    private static String encoded(String payload) {
        return CustomStyleExchange.PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
