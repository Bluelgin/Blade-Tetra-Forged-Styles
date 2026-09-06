package dev.bladetetra.challenge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.bladetetra.BladeTetra;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Bundled, cropped copy of the player-built room beyond the visitor gate. */
final class EasterRoomData {
    private static final int MAGIC = 0x42544133;

    record Entry(short x, short y, short z, BlockState state) {
    }

    record BlockEntityEntry(short x, short y, short z, CompoundTag tag) {
    }

    private static List<Entry> entries;
    private static List<BlockEntityEntry> blockEntities;
    private static List<CompoundTag> entities;

    static synchronized List<Entry> entries() {
        ensureLoaded();
        return entries;
    }

    static synchronized List<BlockEntityEntry> blockEntities() {
        ensureLoaded();
        return blockEntities;
    }

    static synchronized List<CompoundTag> entities() {
        ensureLoaded();
        return entities;
    }

    private static void ensureLoaded() {
        if (entries != null) {
            return;
        }
        ResourceLocation resource = new ResourceLocation(
                BladeTetra.MOD_ID, "challenge/easter_room.bta.gz");
        try (InputStream raw = EasterRoomData.class.getClassLoader().getResourceAsStream(
                    "data/" + resource.getNamespace() + "/" + resource.getPath());
             DataInputStream input = new DataInputStream(new BufferedInputStream(
                     new GZIPInputStream(require(raw, resource))))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid BTA2 easter-room header");
            }
            int paletteSize = input.readUnsignedShort();
            int blockCount = input.readInt();
            BlockState[] palette = new BlockState[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = parseState(readUtf8(input, input.readUnsignedShort()));
            }
            List<Entry> loadedEntries = new ArrayList<>(blockCount);
            for (int i = 0; i < blockCount; i++) {
                short x = input.readShort();
                short y = input.readShort();
                short z = input.readShort();
                int paletteIndex = input.readUnsignedShort();
                if (paletteIndex >= palette.length) {
                    throw new IOException("Invalid easter-room palette index " + paletteIndex);
                }
                loadedEntries.add(new Entry(x, y, z, palette[paletteIndex]));
            }
            int blockEntityCount = input.readInt();
            List<BlockEntityEntry> loadedBlockEntities =
                    new ArrayList<>(blockEntityCount);
            for (int i = 0; i < blockEntityCount; i++) {
                short x = input.readShort();
                short y = input.readShort();
                short z = input.readShort();
                String snbt = readUtf8(input, input.readInt());
                loadedBlockEntities.add(new BlockEntityEntry(
                        x, y, z, TagParser.parseTag(snbt)));
            }
            int entityCount = input.readInt();
            List<CompoundTag> loadedEntities = new ArrayList<>(entityCount);
            for (int i = 0; i < entityCount; i++) {
                loadedEntities.add(TagParser.parseTag(
                        readUtf8(input, input.readInt())));
            }
            entries = List.copyOf(loadedEntries);
            blockEntities = List.copyOf(loadedBlockEntities);
            entities = List.copyOf(loadedEntities);
        } catch (IOException | CommandSyntaxException exception) {
            throw new IllegalStateException("Unable to load easter room", exception);
        }
    }

    private static String readUtf8(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > 16 * 1024 * 1024) {
            throw new IOException("Invalid easter-room string length " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Unexpected end of easter-room data");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static InputStream require(InputStream stream, ResourceLocation location)
            throws IOException {
        if (stream == null) {
            throw new IOException("Missing " + location);
        }
        return stream;
    }

    private static BlockState parseState(String serialized) {
        int bracket = serialized.indexOf('[');
        String blockName = bracket < 0 ? serialized : serialized.substring(0, bracket);
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
        if (block == null) {
            throw new IllegalArgumentException("Unknown easter-room block " + blockName);
        }
        BlockState state = block.defaultBlockState();
        if (bracket < 0) {
            return state;
        }
        String properties = serialized.substring(bracket + 1, serialized.length() - 1);
        for (String pair : properties.split(",")) {
            String[] split = pair.split("=", 2);
            Property<?> property = block.getStateDefinition().getProperty(split[0]);
            if (property != null) {
                state = setValue(state, property, split[1]);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setValue(
            BlockState state, Property<T> property, String value) {
        return property.getValue(value)
                .map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private EasterRoomData() {
    }
}
