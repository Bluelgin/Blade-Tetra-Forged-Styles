package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Offline portable data, never permissions. Inspect is not authorization to mint a scroll. */
public final class CustomStyleExchange {
    public static final int SHARE_VERSION = 1;
    public static final int MAX_CODE_LENGTH = 4096;
    private static final int MAX_PAYLOAD_BYTES = 2048;
    private static final int COMPRESSED = 1, REQUIREMENTS = 2;

    // Wire IDs are these explicit positions, NOT game registry IDs. Never reorder,
    // remove or reuse an entry. Append new entries only (at most 128 per version).
    private static final List<ResourceLocation> ACTIONS = List.of(
            nativeId("combo_a1"), nativeId("combo_a2"), nativeId("combo_a3"),
            nativeId("combo_a4"), nativeId("combo_a4_ex"), nativeId("combo_a5ex"),
            nativeId("combo_b1"), nativeId("combo_b2"), nativeId("combo_b3"),
            nativeId("combo_b4"), nativeId("combo_b5"), nativeId("combo_b6"), nativeId("combo_b7"),
            nativeId("combo_c"), nativeId("upperslash"), nativeId("rapid_slash"), nativeId("rapid_slash_quick"),
            ownId("iaido_draw"), ownId("iaido_follow"), ownId("iaido_return"), ownId("iaido_finish"),
            ownId("dangaku_sweep"), ownId("dangaku_cleave"), ownId("dangaku_charged_sweep"));

    public enum Status { OK, INVALID_FORMAT, UNSUPPORTED_VERSION, INVALID_CONTENT, UNAVAILABLE_ACTION }
    public record Requirement(String modId, String displayName) {}
    public record ImportResult(Status status, CustomStyleSpec style, List<Requirement> requirements) {
        public ImportResult(Status status, CustomStyleSpec style) { this(status, style, List.of()); }
        public boolean accepted() { return status == Status.OK && style != null; }
    }

    public static String export(CustomStyleSpec style) { return encode(style, List.of()); }

    /** Only external provider IDs need hints; built-in providers are implied by the dictionary. */
    public static String exportWithDependencies(CustomStyleSpec style) {
        List<String> mods = new ArrayList<>();
        for (ResourceLocation action : style.actions()) {
            var provider = CustomStyleDependencyResolver.provider(action);
            if (provider != null && !provider.modId().equals("slashblade")
                    && !provider.modId().equals("blade_tetra") && !mods.contains(provider.modId()))
                mods.add(provider.modId());
        }
        return encode(style, mods);
    }

    private static String encode(CustomStyleSpec style, List<String> requirements) {
        if (style.name().chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid name");
        var out = new ByteArrayOutputStream();
        writeText(out, style.name());
        out.write(style.actions().size());
        List<ResourceLocation> external = style.actions().stream().filter(id -> !ACTIONS.contains(id)).distinct().toList();
        out.write(external.size());
        for (ResourceLocation id : external) {
            if (id.toString().length() > 128) throw new IllegalArgumentException("Action ID too long");
            writeText(out, id.toString());
        }
        for (ResourceLocation id : style.actions()) {
            int index = ACTIONS.indexOf(id);
            out.write(index >= 0 ? index : 128 + external.indexOf(id));
        }
        if (!requirements.isEmpty()) {
            out.write(requirements.size());
            requirements.forEach(mod -> writeText(out, mod));
        }
        byte[] payload = out.toByteArray();
        if (payload.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("Share payload too large");
        byte[] compressed = deflate(payload);
        int flags = requirements.isEmpty() ? 0 : REQUIREMENTS;
        if (compressed.length < payload.length) { payload = compressed; flags |= COMPRESSED; }
        var packet = new ByteArrayOutputStream();
        packet.write((SHARE_VERSION << 4) | flags);
        packet.writeBytes(payload);
        int crc = checksum(packet.toByteArray());
        packet.write(crc >>> 8); packet.write(crc);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(packet.toByteArray());
    }

    public static ImportResult importValidated(String code) {
        ImportResult parsed = inspect(code);
        if (!parsed.accepted()) return parsed;
        return CustomStyleDependencyResolver.inspect(code).allowed()
                ? parsed : new ImportResult(Status.UNAVAILABLE_ACTION, null);
    }

    /** Registry-independent preview. Final import must still call importValidated. */
    public static ImportResult inspect(String code) {
        if (code == null || code.length() < 8 || code.length() > MAX_CODE_LENGTH
                || !code.matches("[A-Za-z0-9_-]+")) return reject(Status.INVALID_FORMAT);
        try {
            byte[] packet = Base64.getUrlDecoder().decode(code);
            // Reject noncanonical padding bits as well as accidental edits.
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(packet).equals(code))
                return reject(Status.INVALID_CONTENT);
            int end = packet.length - 2;
            byte[] envelope = Arrays.copyOf(packet, end);
            if (checksum(envelope) != ((packet[end] & 255) << 8 | (packet[end + 1] & 255)))
                return reject(Status.INVALID_CONTENT);
            int header = packet[0] & 255;
            if ((header >>> 4) != SHARE_VERSION) return reject(Status.UNSUPPORTED_VERSION);
            if ((header & 12) != 0) return reject(Status.INVALID_CONTENT);
            byte[] payload = Arrays.copyOfRange(packet, 1, end);
            if ((header & COMPRESSED) != 0) payload = inflate(payload);
            if (payload.length > MAX_PAYLOAD_BYTES) return reject(Status.INVALID_CONTENT);
            var in = new Reader(payload);
            String name = in.text(CustomStyleSpec.MAX_NAME_LENGTH * 3);
            if (name.isBlank() || name.length() > CustomStyleSpec.MAX_NAME_LENGTH
                    || name.chars().anyMatch(Character::isISOControl)) return reject(Status.INVALID_CONTENT);
            int count = in.u8(), externalCount = in.u8();
            if (count < 1 || count > CustomStyleSpec.MAX_ACTIONS || externalCount > count)
                return reject(Status.INVALID_CONTENT);
            List<ResourceLocation> external = new ArrayList<>();
            for (int i = 0; i < externalCount; i++) {
                String raw = in.text(128);
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null || !id.toString().equals(raw) || external.contains(id))
                    return reject(Status.INVALID_CONTENT);
                external.add(id);
            }
            List<ResourceLocation> actions = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int token = in.u8();
                if (token < 128) {
                    if (token >= ACTIONS.size()) return reject(Status.INVALID_CONTENT);
                    actions.add(ACTIONS.get(token));
                } else {
                    if (token - 128 >= externalCount) return reject(Status.INVALID_CONTENT);
                    actions.add(external.get(token - 128));
                }
            }
            if (!actions.containsAll(external)) return reject(Status.INVALID_CONTENT);
            List<Requirement> requirements = new ArrayList<>();
            if ((header & REQUIREMENTS) != 0) {
                int size = in.u8();
                if (size < 1 || size > 12) return reject(Status.INVALID_CONTENT);
                for (int i = 0; i < size; i++) {
                    String mod = in.text(64);
                    if (!mod.matches("[a-z][a-z0-9_]{1,63}")
                            || requirements.stream().anyMatch(it -> it.modId().equals(mod))) return reject(Status.INVALID_CONTENT);
                    requirements.add(new Requirement(mod, ""));
                }
            }
            if (in.remaining() != 0) return reject(Status.INVALID_CONTENT);
            return new ImportResult(Status.OK, new CustomStyleSpec(name, actions), List.copyOf(requirements));
        } catch (IllegalArgumentException | CharacterCodingException | DataFormatException exception) {
            return reject(Status.INVALID_CONTENT);
        }
    }

    // CRC-16/CCITT-FALSE (poly 0x1021, init 0xffff). Integrity, not authentication.
    private static int checksum(byte[] data) {
        int crc = 0xffff;
        for (byte b : data) {
            crc ^= (b & 255) << 8;
            for (int bit = 0; bit < 8; bit++) crc = ((crc << 1) ^ ((crc & 0x8000) != 0 ? 0x1021 : 0)) & 0xffff;
        }
        return crc;
    }
    private static void writeText(ByteArrayOutputStream out, String value) {
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            bytes = new byte[encoded.remaining()]; encoded.get(bytes);
        } catch (CharacterCodingException exception) { throw new IllegalArgumentException("Invalid UTF-16", exception); }
        int length = bytes.length;
        while (length > 127) { out.write((length & 127) | 128); length >>>= 7; }
        out.write(length); out.writeBytes(bytes);
    }
    private static byte[] deflate(byte[] payload) {
        var compressor = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            compressor.setInput(payload); compressor.finish();
            var out = new ByteArrayOutputStream(); byte[] buffer = new byte[256];
            while (!compressor.finished()) out.write(buffer, 0, compressor.deflate(buffer));
            return out.toByteArray();
        } finally { compressor.end(); }
    }
    private static byte[] inflate(byte[] payload) throws DataFormatException {
        var inflater = new Inflater(true);
        try {
            inflater.setInput(payload);
            var out = new ByteArrayOutputStream(); byte[] buffer = new byte[256];
            while (!inflater.finished()) {
                int size = inflater.inflate(buffer);
                if (out.size() + size > MAX_PAYLOAD_BYTES || (size == 0 && !inflater.finished()))
                    throw new DataFormatException("Truncated or oversized share payload");
                out.write(buffer, 0, size);
            }
            if (inflater.getRemaining() != 0) throw new DataFormatException("Trailing compressed bytes");
            return out.toByteArray();
        } finally { inflater.end(); }
    }
    private static final class Reader {
        private final ByteBuffer buffer;
        Reader(byte[] bytes) { buffer = ByteBuffer.wrap(bytes); }
        int remaining() { return buffer.remaining(); }
        int u8() {
            if (!buffer.hasRemaining()) throw new IllegalArgumentException("Truncated code");
            return buffer.get() & 255;
        }
        String text(int maxBytes) throws CharacterCodingException {
            int length = u8();
            if (length >= 128) {
                int next = u8();
                if (next == 0 || next >= 128) throw new IllegalArgumentException("Invalid length");
                length = (length & 127) | (next << 7);
            }
            if (length > maxBytes || length > remaining()) throw new IllegalArgumentException("Oversized string");
            ByteBuffer bytes = buffer.slice(); bytes.limit(length); buffer.position(buffer.position() + length);
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(bytes).toString();
        }
    }
    private static ResourceLocation nativeId(String path) { return new ResourceLocation("slashblade", path); }
    private static ResourceLocation ownId(String path) { return new ResourceLocation("blade_tetra", path); }
    private static ImportResult reject(Status status) { return new ImportResult(status, null); }
    private CustomStyleExchange() {}
}
