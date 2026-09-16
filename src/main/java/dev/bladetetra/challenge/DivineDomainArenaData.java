package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime-built, bounded 神域 arena. All gameplay coordinates live here. */
public final class DivineDomainArenaData {
    public static final int FLOOR_Y = 63;
    public static final int PLATFORM_RADIUS = 15;
    public static final int OUTER_RADIUS = 48;
    public static final int HARD_BOUNDARY_RADIUS = 53;

    public static final int ENTRY_X = 0;
    public static final int ENTRY_Z = 13;
    public static final int RACK_X = -7;
    public static final int RACK_Z = 0;
    public static final int FIRE_X = 7;
    public static final int FIRE_Z = 0;
    public static final int ALTAR_X = 0;
    public static final int ALTAR_Z = 0;

    private static final int[][] ISLANDS = {
            {-31, -8, 8}, {-22, -29, 7}, {5, -35, 9}, {30, -24, 7},
            {36, 5, 8}, {23, 29, 8}, {-4, 37, 8}, {-32, 25, 7}
    };

    public static void build(ServerLevel level, int originX, int originZ) {
        if (isBuilt(level, originX, originZ)) {
            return;
        }
        clearVolume(level, originX, originZ);
        buildCentralPlatform(level, originX, originZ);
        buildBrokenRing(level, originX, originZ);
        buildRack(level, originX, originZ);
        buildRitualFire(level, originX, originZ);
        buildBoundaryTeeth(level, originX, originZ);
        level.setBlock(marker(originX, originZ), Blocks.BEDROCK.defaultBlockState(), 2 | 16);
    }

    public static boolean isBuilt(ServerLevel level, int originX, int originZ) {
        return level.getBlockState(marker(originX, originZ)).is(Blocks.BEDROCK);
    }

    public static BlockPos entry(int originX, int originZ) {
        return new BlockPos(originX + ENTRY_X, FLOOR_Y + 1, originZ + ENTRY_Z);
    }

    public static BlockPos rack(int originX, int originZ) {
        return new BlockPos(originX + RACK_X, FLOOR_Y + 1, originZ + RACK_Z);
    }

    public static BlockPos fire(int originX, int originZ) {
        return new BlockPos(originX + FIRE_X, FLOOR_Y + 1, originZ + FIRE_Z);
    }

    public static BlockPos altar(int originX, int originZ) {
        return new BlockPos(originX + ALTAR_X, FLOOR_Y + 1, originZ + ALTAR_Z);
    }

    public static BlockPos spawnPoint(int originX, int originZ, int index, int directions) {
        int count = Math.max(2, directions);
        double angle = Math.PI * 2.0D * Math.floorMod(index, count) / count;
        double radius = 31.0D + (index % 3) * 3.0D;
        return new BlockPos(originX + (int) Math.round(Math.cos(angle) * radius),
                FLOOR_Y + 1,
                originZ + (int) Math.round(Math.sin(angle) * radius));
    }

    public static boolean contains(double x, double z, int originX, int originZ) {
        double dx = x - (originX + 0.5D);
        double dz = z - (originZ + 0.5D);
        return dx * dx + dz * dz <= HARD_BOUNDARY_RADIUS * HARD_BOUNDARY_RADIUS;
    }

    private static BlockPos marker(int originX, int originZ) {
        return new BlockPos(originX, -62, originZ);
    }

    private static void clearVolume(ServerLevel level, int ox, int oz) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HARD_BOUNDARY_RADIUS - 2; x <= HARD_BOUNDARY_RADIUS + 2; x++) {
            for (int z = -HARD_BOUNDARY_RADIUS - 2; z <= HARD_BOUNDARY_RADIUS + 2; z++) {
                if (x * x + z * z > (HARD_BOUNDARY_RADIUS + 2) * (HARD_BOUNDARY_RADIUS + 2)) {
                    continue;
                }
                for (int y = FLOOR_Y - 8; y <= FLOOR_Y + 18; y++) {
                    level.setBlock(new BlockPos(ox + x, y, oz + z), air, 2 | 16);
                }
            }
        }
    }

    private static void buildCentralPlatform(ServerLevel level, int ox, int oz) {
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > PLATFORM_RADIUS + ((x * 31 + z * 17) & 3) * 0.35D) {
                    continue;
                }
                level.setBlock(new BlockPos(ox + x, FLOOR_Y - 2, oz + z),
                        Blocks.BLACKSTONE.defaultBlockState(), 2 | 16);
                level.setBlock(new BlockPos(ox + x, FLOOR_Y - 1, oz + z),
                        d < 5.0D ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.BASALT.defaultBlockState(), 2 | 16);
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z),
                        ((x + z) & 5) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z),
                        Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void buildBrokenRing(ServerLevel level, int ox, int oz) {
        for (int[] island : ISLANDS) {
            int cx = island[0];
            int cz = island[1];
            int radius = island[2];
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double wobble = ((x * 13 + z * 29 + cx) & 7) * 0.13D;
                    if (x * x + z * z > (radius - wobble) * (radius - wobble)) {
                        continue;
                    }
                    int top = FLOOR_Y + (((x + z + cx) & 7) == 0 ? 1 : 0);
                    int depth = 2 + Math.floorMod(x * 7 + z * 11, 4);
                    for (int y = top - depth; y <= top; y++) {
                        BlockState state = y == top
                                ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : (y == top - depth ? Blocks.MAGMA_BLOCK.defaultBlockState()
                                        : Blocks.BLACKSTONE.defaultBlockState());
                        level.setBlock(new BlockPos(ox + cx + x, y, oz + cz + z), state, 2 | 16);
                    }
                }
            }
        }
        // Narrow broken causeways visually connect the ritual floor without making a safe ring.
        for (int i = 17; i <= 27; i++) {
            if (i == 21 || i == 24) continue;
            level.setBlock(new BlockPos(ox, FLOOR_Y, oz - i), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
            level.setBlock(new BlockPos(ox, FLOOR_Y, oz + i), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
        }
    }

    private static void buildRack(ServerLevel level, int ox, int oz) {
        int x = ox + RACK_X;
        int z = oz + RACK_Z;
        level.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(x - 1, FLOOR_Y + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(x + 1, FLOOR_Y + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(x - 1, FLOOR_Y + 2, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(x + 1, FLOOR_Y + 2, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(x, FLOOR_Y + 2, z), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2 | 16);
    }

    private static void buildRitualFire(ServerLevel level, int ox, int oz) {
        int x = ox + FIRE_X;
        int z = oz + FIRE_Z;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, FLOOR_Y, z + dz),
                        (Math.abs(dx) + Math.abs(dz) == 0)
                                ? Blocks.SOUL_SOIL.defaultBlockState()
                                : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
            }
        }
        level.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.SOUL_FIRE.defaultBlockState(), 2 | 16);
    }

    private static void buildBoundaryTeeth(ServerLevel level, int ox, int oz) {
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0D * i / 32.0D;
            int x = (int) Math.round(Math.cos(angle) * OUTER_RADIUS);
            int z = (int) Math.round(Math.sin(angle) * OUTER_RADIUS);
            int height = 2 + (i % 4);
            for (int y = 0; y < height; y++) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y - 2 + y, oz + z),
                        i % 3 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : Blocks.BASALT.defaultBlockState(), 2 | 16);
            }
        }
    }

    private DivineDomainArenaData() {
    }
}
