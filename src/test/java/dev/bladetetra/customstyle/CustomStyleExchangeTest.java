package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.Deflater;
import static org.junit.jupiter.api.Assertions.*;

class CustomStyleExchangeTest {
    private static final String NAME = "\u98de\u71d5\u5251\u8c31";
    private static final List<ResourceLocation> SIX = List.of(id("combo_a1"), id("combo_b1"), id("combo_a2"),
            id("combo_b2"), id("combo_a3"), id("combo_c"));

    @Test void preservesChineseNameOrderRepeatsAndHasNoPrefix() {
        var spec = new CustomStyleSpec(NAME, List.of(id("upperslash"), id("upperslash"), id("rapid_slash")));
        String code = CustomStyleExchange.export(spec);
        assertTrue(code.matches("[A-Za-z0-9_-]+"));
        assertEquals(28, code.length());
        assertEquals(spec, CustomStyleExchange.inspect(code).style());
        assertEquals(code, CustomStyleExchange.export(CustomStyleExchange.inspect(code).style()));
        assertEquals(code, CustomStyleExchange.exportWithDependencies(spec));
    }
    @Test void sixMovesFit32CharactersAndEveryBuiltinWireIdIsPinned() {
        assertEquals(32, CustomStyleExchange.export(new CustomStyleSpec(NAME, SIX)).length());
        // Golden vector generated independently using Python binascii.crc_hqx.
        assertEquals("EAzpo57nh5XliZHosLEGAAAGAQcCDclA", CustomStyleExchange.export(new CustomStyleSpec(NAME, SIX)));
        assertEquals(27, CustomStyleExchange.export(new CustomStyleSpec(NAME, SIX.subList(0, 2))).length());
        List<String> paths = List.of("combo_a1", "combo_a2", "combo_a3", "combo_a4", "combo_a4_ex", "combo_a5ex",
                "combo_b1", "combo_b2", "combo_b3", "combo_b4", "combo_b5", "combo_b6", "combo_b7", "combo_c",
                "upperslash", "rapid_slash", "rapid_slash_quick", "iaido_draw", "iaido_follow", "iaido_return",
                "iaido_finish", "dangaku_sweep", "dangaku_cleave", "dangaku_charged_sweep");
        for (int i = 0; i < paths.size(); i++) {
            var expected = new CustomStyleSpec("X", List.of(new ResourceLocation(i < 17 ? "slashblade" : "blade_tetra", paths.get(i))));
            assertEquals(expected, CustomStyleExchange.inspect(wire(0x10, new byte[]{1, 'X', 1, 0, (byte)i})).style());
            assertEquals(wire(0x10, new byte[]{1, 'X', 1, 0, (byte)i}), CustomStyleExchange.export(expected));
        }
    }
    @Test void externalIdsAndProviderHintsRoundTripWithoutRegistryOrPermissions() {
        var external = new ResourceLocation("different_namespace", "ordinary_cut");
        CustomStyleDependencyResolver.registerProvider(external,
                new CustomStyleDependencyResolver.Provider("actual_mod", "Local display only", ""));
        var spec = new CustomStyleSpec(NAME, List.of(external, id("combo_a1"), external));
        String plain = CustomStyleExchange.export(spec), enriched = CustomStyleExchange.exportWithDependencies(spec);
        assertEquals(spec, CustomStyleExchange.inspect(plain).style());
        assertEquals(spec, CustomStyleExchange.inspect(enriched).style());
        assertEquals(List.of(), CustomStyleExchange.inspect(plain).requirements());
        assertEquals(List.of(new CustomStyleExchange.Requirement("actual_mod", "")),
                CustomStyleExchange.inspect(enriched).requirements());
    }
    @Test void choosesCompressionOnlyWhenShorter() {
        String shortCode = CustomStyleExchange.export(new CustomStyleSpec(NAME, SIX));
        assertEquals(0, Base64.getUrlDecoder().decode(shortCode)[0] & 1);
        var external = new ResourceLocation("some_long_namespace", "ordinary_ordinary_ordinary_cut");
        var spec = new CustomStyleSpec("\u5251".repeat(48), List.of(external, external, external, external, external, external));
        String code = CustomStyleExchange.export(spec);
        assertEquals(1, Base64.getUrlDecoder().decode(code)[0] & 1);
        assertEquals(spec, CustomStyleExchange.inspect(code).style());
    }
    @Test void truncationEditsPaddingAndOldJsonCodesAreRejected() {
        String code = CustomStyleExchange.export(new CustomStyleSpec(NAME, SIX));
        for (int i = 0; i < code.length(); i++) {
            String edited = code.substring(0, i) + (code.charAt(i) == 'A' ? 'B' : 'A') + code.substring(i + 1);
            assertFalse(CustomStyleExchange.inspect(edited).accepted());
            assertFalse(CustomStyleExchange.inspect(code.substring(0, i)).accepted());
        }
        for (String bad : List.of("BTSTYLE1:eyJuYW1lIjoiWCJ9", code + "=", code + " ", "%%%%", "x".repeat(5000)))
            assertFalse(CustomStyleExchange.inspect(bad).accepted());
        assertFalse(CustomStyleExchange.inspect(null).accepted());
    }
    @Test void futureVersionIsDistinctAndUnknownFlagsAndTokensFailClosed() {
        assertEquals(CustomStyleExchange.Status.UNSUPPORTED_VERSION,
                CustomStyleExchange.inspect(wire(0x20, new byte[]{1,'X',1,0,0})).status());
        for (int flag : new int[]{0x14,0x18}) assertFalse(CustomStyleExchange.inspect(wire(flag,new byte[]{1,'X',1,0,0})).accepted());
        for (int token : new int[]{24,127,128,255})
            assertFalse(CustomStyleExchange.inspect(wire(0x10,new byte[]{1,'X',1,0,(byte)token})).accepted());
    }
    @Test void invalidUtf8ControlsCountsLengthsAndTrailingBytesAreRejected() {
        for (byte[] payload : List.of(new byte[]{2,(byte)0xc3,0x28,1,0,0}, new byte[]{1,10,1,0,0},
                new byte[]{1,'X',0,0}, new byte[]{1,'X',7,0,0,0,0,0,0,0,0}, new byte[]{1,'X',1,2},
                new byte[]{127,'X',1,0,0}, new byte[]{(byte)0x81,0,'X',1,0,0},new byte[]{1,'X',1,0,0,99}))
            assertFalse(CustomStyleExchange.inspect(wire(0x10,payload)).accepted());
        assertThrows(IllegalArgumentException.class, () -> CustomStyleExchange.export(new CustomStyleSpec("X\nY",SIX)));
        assertThrows(IllegalArgumentException.class, () -> CustomStyleExchange.export(new CustomStyleSpec("\ud800",SIX)));
    }
    @Test void compressedBombTruncationAndTrailingCompressedBytesAreRejected() {
        byte[] bomb = new byte[2049]; Arrays.fill(bomb,(byte)'x');
        assertFalse(CustomStyleExchange.inspect(wire(0x11,deflate(bomb))).accepted());
        byte[] valid = deflate(new byte[]{1,'X',1,0,0});
        assertTrue(CustomStyleExchange.inspect(wire(0x11,valid)).accepted());
        assertFalse(CustomStyleExchange.inspect(wire(0x11,Arrays.copyOf(valid,valid.length-1))).accepted());
        assertFalse(CustomStyleExchange.inspect(wire(0x11,Arrays.copyOf(valid,valid.length+1))).accepted());
    }
    @Test void requirementIdsAndExternalDictionaryReferencesAreValidated() {
        for (String mod : List.of("https://bad", "X", "")) {
            byte[] value = mod.getBytes(StandardCharsets.UTF_8);
            var out = new ByteArrayOutputStream(); out.writeBytes(new byte[]{1,'X',1,0,0,1,(byte)value.length}); out.writeBytes(value);
            assertFalse(CustomStyleExchange.inspect(wire(0x12,out.toByteArray())).accepted());
        }
        // Unknown/unsafe actions are parseable for previews, never implicit permissions.
        byte[] action = "addon:slash_art".getBytes(StandardCharsets.UTF_8);
        var out = new ByteArrayOutputStream(); out.writeBytes(new byte[]{1,'X',1,1,(byte)action.length});
        out.writeBytes(action); out.write(128);
        assertEquals(new ResourceLocation("addon:slash_art"),CustomStyleExchange.inspect(wire(0x10,out.toByteArray())).style().actions().get(0));
        byte[] invalid = out.toByteArray(); invalid[invalid.length-1] = (byte)129;
        assertFalse(CustomStyleExchange.inspect(wire(0x10,invalid)).accepted());
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("slashblade",path); }
    private static String wire(int header, byte[] payload) {
        var out = new ByteArrayOutputStream(); out.write(header); out.writeBytes(payload);
        int crc=0xffff;
        for(byte b:out.toByteArray()) { crc^=(b&255)<<8; for(int i=0;i<8;i++) crc=((crc<<1)^((crc&0x8000)!=0?0x1021:0))&0xffff; }
        out.write(crc>>>8);out.write(crc);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }
    private static byte[] deflate(byte[] data) {
        var compressor=new Deflater(9,true);
        try {
            compressor.setInput(data);compressor.finish();var out=new ByteArrayOutputStream();byte[] buffer=new byte[256];
            while(!compressor.finished()) out.write(buffer,0,compressor.deflate(buffer));return out.toByteArray();
        } finally { compressor.end(); }
    }
}
