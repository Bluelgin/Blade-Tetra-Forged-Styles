package dev.bladetetra.forging;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.blocks.scroll.ScrollData;
import se.mickelus.tetra.blocks.scroll.ScrollItem;

import java.util.List;
import java.util.Optional;

/** Native Tetra scroll stacks that unlock Blade Tetra forging knowledge. */
public final class ForgingScrolls {
    public enum Kind {
        EDGE,
        CONSTRUCTION,
        ASSEMBLY,
        WIND_CUT,
        FLYING_SWALLOW,
        FULL_MOON,
        ZANSHIN,
        BLACK_FOX,
        WHITE_FOX
    }

    public static ItemStack create(Kind kind) {
        ItemStack stack = new ItemStack(ScrollItem.instance);
        data(kind).write(stack);
        return stack;
    }

    public static ItemStack create(LegacyImprintKind kind) {
        ItemStack stack = new ItemStack(ScrollItem.instance);
        scroll("blade_tetra.legacy_auto." + kind.id().replace('/', '.'),
                0xE6DDCF, 0x8A2F2F, List.of(9, 2, 11, 5),
                kind.schematic("saya"), kind.schematic("tsuba")).write(stack);
        return stack;
    }

    private static ScrollData data(Kind kind) {
        return switch (kind) {
            case EDGE -> scroll(
                    "blade_tetra.edge_treatise",
                    0xE6DDCF,
                    0x365A73,
                    List.of(1, 5, 8, 2),
                    "slashblade/finishing/hamaguri",
                    "slashblade/finishing/hira",
                    "slashblade/finishing/usuba",
                    "slashblade/finishing/normalize_edge");
            case CONSTRUCTION -> scroll(
                    "blade_tetra.forging_treatise",
                    0xD8C4A4,
                    0x8A2F2F,
                    List.of(9, 4, 12, 6),
                    "slashblade/forging/kobuse",
                    "slashblade/forging/sanmai",
                    "slashblade/forging/shihozume",
                    "slashblade/forging/normalize_construction");
            case ASSEMBLY -> scroll(
                    "blade_tetra.assembly_treatise",
                    0xD8D0B8,
                    0x4D6748,
                    List.of(3, 10, 7, 11),
                    "slashblade/finishing/nakago_fit",
                    "slashblade/finishing/rigid_assembly",
                    "slashblade/finishing/normalize_assembly");
            case WIND_CUT -> scroll(
                    "blade_tetra.wind_cut",
                    0xD9E2D0,
                    0x55745B,
                    List.of(2, 8, 5, 11),
                    "slashblade/technique/wind_cut");
            case FLYING_SWALLOW -> scroll(
                    "blade_tetra.flying_swallow",
                    0xDDE4EC,
                    0x496C8E,
                    List.of(7, 1, 10, 4),
                    "slashblade/technique/flying_swallow");
            case FULL_MOON -> scroll(
                    "blade_tetra.full_moon",
                    0xE8E1CC,
                    0x7D6541,
                    List.of(12, 3, 9, 6),
                    "slashblade/technique/full_moon");
            case ZANSHIN -> scroll(
                    "blade_tetra.zanshin",
                    0xD8D4E2,
                    0x56496F,
                    List.of(4, 11, 2, 9),
                    "slashblade/technique/zanshin");
            case BLACK_FOX -> foxScroll("black", 0xD9D0C4, 0x342C34,
                    List.of(9, 2, 11, 5));
            case WHITE_FOX -> foxScroll("white", 0xEEE7DB, 0xB4494D,
                    List.of(6, 12, 3, 8));
        };
    }

    private static ScrollData foxScroll(String color, int material, int ribbon,
            List<Integer> glyphs) {
        return scroll("blade_tetra.legacy_fox_" + color, material, ribbon, glyphs,
                "slashblade/legacy/fox_" + color + "_saya",
                "slashblade/legacy/fox_" + color + "_tsuba");
    }

    private static ScrollData scroll(
            String key,
            int material,
            int ribbon,
            List<Integer> glyphs,
            String... schematics) {
        List<ResourceLocation> unlocks = java.util.Arrays.stream(schematics)
                .map(path -> new ResourceLocation("tetra", path))
                .toList();
        return new ScrollData(
                key,
                Optional.of(key),
                true,
                material,
                ribbon,
                glyphs,
                unlocks,
                List.of());
    }

    private ForgingScrolls() {
    }
}
