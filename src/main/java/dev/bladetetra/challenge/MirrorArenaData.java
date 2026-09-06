package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class MirrorArenaData {
    /**
     * Gives the imported arena a restrained shrine palette without changing
     * the user's source world. All matching block-state properties are applied
     * again after substitution, preserving axes, stair shapes and waterlogging.
     */
    private static final Map<String, String> SHRINE_PALETTE = Map.ofEntries(
            Map.entry("minecraft:stripped_acacia_wood", "minecraft:stripped_mangrove_wood"),
            Map.entry("minecraft:acacia_planks", "minecraft:mangrove_planks"),
            Map.entry("minecraft:acacia_slab", "minecraft:mangrove_slab"),
            Map.entry("minecraft:acacia_stairs", "minecraft:mangrove_stairs"),
            Map.entry("minecraft:acacia_trapdoor", "minecraft:mangrove_trapdoor"),
            Map.entry("minecraft:acacia_fence", "minecraft:mangrove_fence"),
            Map.entry("minecraft:spruce_planks", "minecraft:dark_oak_planks"),
            Map.entry("minecraft:granite", "minecraft:andesite"),
            Map.entry("minecraft:polished_granite", "minecraft:polished_andesite"),
            Map.entry("minecraft:red_sandstone_wall", "minecraft:red_nether_brick_wall"),
            Map.entry("minecraft:brick_slab", "minecraft:red_nether_brick_slab"));

    record Entry(short x, short y, short z, BlockState state) {
    }

    private static List<Entry> entries;
    private static List<Entry> buildOrder;

    static synchronized List<Entry> entries() {
        if (entries != null) {
            return entries;
        }
        ResourceLocation resource = new ResourceLocation(
                BladeTetra.MOD_ID, "challenge/mikage_garden.bta.gz");
        try (InputStream raw = MirrorArenaData.class.getClassLoader().getResourceAsStream(
                    "data/" + resource.getNamespace() + "/" + resource.getPath());
             DataInputStream input = new DataInputStream(new BufferedInputStream(
                     new GZIPInputStream(require(raw, resource))))) {
            if (input.readInt() != 0x42544131) {
                throw new IOException("Invalid BTA arena header");
            }
            int paletteSize = input.readUnsignedShort();
            int blockCount = input.readInt();
            BlockState[] palette = new BlockState[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = parseState(input.readUTF());
            }
            List<Entry> loaded = new ArrayList<>(blockCount);
            for (int i = 0; i < blockCount; i++) {
                short x = input.readShort();
                short y = input.readShort();
                short z = input.readShort();
                int paletteIndex = input.readUnsignedShort();
                loaded.add(new Entry(x, y, z, palette[paletteIndex]));
            }
            entries = List.copyOf(loaded);
            return entries;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Mikage garden", exception);
        }
    }

    /** Places the arrival torii and nearby path before the distant scenery. */
    static synchronized List<Entry> buildOrder() {
        if (buildOrder == null) {
            List<Entry> ordered = new ArrayList<>(entries());
            ordered.sort(Comparator.comparingInt(MirrorArenaData::priority));
            buildOrder = List.copyOf(ordered);
        }
        return buildOrder;
    }

    private static int priority(Entry entry) {
        if (entry.x() <= 40 && Math.abs(entry.z()) <= 35) {
            return 0;
        }
        if (entry.x() <= 60 && Math.abs(entry.z()) <= 45) {
            return 1;
        }
        return 2;
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
        blockName = SHRINE_PALETTE.getOrDefault(blockName, blockName);
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
        if (block == null) {
            throw new IllegalArgumentException("Unknown arena block " + blockName);
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
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private MirrorArenaData() {
    }
}
