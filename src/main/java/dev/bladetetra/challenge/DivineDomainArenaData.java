package dev.bladetetra.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime-built, bounded 神域 arena. All gameplay coordinates live here. */
public final class DivineDomainArenaData {
    public static final int FLOOR_Y = 63;
    public static final int MAIN_ISLAND_RADIUS = 21;
    public static final int COMBAT_RADIUS = 13;
    public static final int OUTER_RADIUS = 47;
    public static final int HARD_BOUNDARY_RADIUS = 51;

    // The player arrives from the south and looks through the ritual fire toward
    // the intact central torii. The SlashBlade stand sits directly under the gate.
    public static final int ENTRY_X = 0;
    public static final int ENTRY_Z = 18;
    public static final int RACK_X = 0;
    public static final int RACK_Z = -1;
    public static final int FIRE_X = 0;
    public static final int FIRE_Z = 5;
    public static final int ALTAR_X = 0;
    public static final int ALTAR_Z = 0;

    private static final int[][] ISLANDS = {
            {-31, -7, 8, 1}, {-24, -30, 7, -1}, {4, -37, 9, 2}, {31, -25, 7, 0},
            {38, 5, 8, -2}, {25, 31, 8, 1}, {-5, 39, 7, -1}, {-34, 26, 8, 2}
    };

    public static void build(ServerLevel level, int originX, int originZ) {
        if (isBuilt(level, originX, originZ)) {
            return;
        }
        // PR v1 used bedrock as the marker. Clean only the old functional props
        // and causeway before layering the authored v2 shrine on top; the realm's
        // generator is all air, so a giant clear-volume pass is unnecessary.
        if (level.getBlockState(marker(originX, originZ)).is(Blocks.BEDROCK)) {
            clearLegacyV1Artifacts(level, originX, originZ);
        }
        buildMainIsland(level, originX, originZ);
        buildEntryTongue(level, originX, originZ);
        buildCentralTorii(level, originX, originZ);
        buildRackDais(level, originX, originZ);
        buildRitualFire(level, originX, originZ);
        buildBrokenRing(level, originX, originZ);
        buildBoundaryTeeth(level, originX, originZ);
        level.setBlock(marker(originX, originZ), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2 | 16);
    }

    public static boolean isBuilt(ServerLevel level, int originX, int originZ) {
        return level.getBlockState(marker(originX, originZ)).is(Blocks.CRYING_OBSIDIAN);
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
        // The combat ring is deliberately inside the guaranteed-solid inner
        // island. Outer floating fragments are scenery, never melee path traps.
        double radius = 11.0D + Math.floorMod(index, 3) * 0.75D;
        return new BlockPos(originX + (int) Math.round(Math.cos(angle) * radius),
                FLOOR_Y + 1,
                originZ + (int) Math.round(Math.sin(angle) * radius));
    }

    public static boolean contains(double x, double z, int originX, int originZ) {
        double dx = x - (originX + 0.5D);
        double dz = z - (originZ + 0.5D);
        return dx * dx + dz * dz <= HARD_BOUNDARY_RADIUS * HARD_BOUNDARY_RADIUS;
    }

    static boolean isGuaranteedCombatFloor(int x, int z) {
        return x * x + z * z <= COMBAT_RADIUS * COMBAT_RADIUS;
    }

    private static BlockPos marker(int originX, int originZ) {
        return new BlockPos(originX, -62, originZ);
    }

    private static void buildMainIsland(ServerLevel level, int ox, int oz) {
        int range = MAIN_ISLAND_RADIUS + 3;
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (!isMainIslandCell(x, z)) {
                    continue;
                }
                double distance = Math.sqrt(x * x + z * z);
                int depth = 3 + Math.max(0, (int) ((MAIN_ISLAND_RADIUS - distance) / 3.0D));
                depth += Math.floorMod(x * 13 + z * 7, 2);
                for (int y = FLOOR_Y - depth; y < FLOOR_Y; y++) {
                    BlockState state = y == FLOOR_Y - depth
                            ? Blocks.BASALT.defaultBlockState()
                            : Blocks.BLACKSTONE.defaultBlockState();
                    level.setBlock(new BlockPos(ox + x, y, oz + z), state, 2 | 16);
                }
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z),
                        mainSurface(x, z, distance), 2 | 16);
            }
        }

        // A darker intact ritual nucleus keeps the ceremony legible inside the
        // fractured outer silhouette.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 7; z++) {
                if (x * x + z * z > 49) continue;
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z),
                        ((Math.abs(x) + Math.abs(z)) % 5 == 0)
                                ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static boolean isMainIslandCell(int x, int z) {
        if (Math.abs(x) <= 2 && z >= 12 && z <= ENTRY_Z + 2) {
            return true;
        }
        double d = Math.sqrt(x * x + z * z);
        double edgeNoise = (Math.floorMod(x * 31 + z * 17, 9) - 4) * 0.32D;
        if (d > MAIN_ISLAND_RADIUS + edgeNoise) {
            return false;
        }
        if (d <= 14.0D) {
            return true;
        }
        // Three missing outer wedges make the main arena read as a single island
        // that has physically split apart instead of a clean circular platform.
        double angle = Math.atan2(z, x);
        return !inAngularCrack(angle, Math.toRadians(-145), 0.18D)
                && !inAngularCrack(angle, Math.toRadians(-22), 0.14D)
                && !inAngularCrack(angle, Math.toRadians(72), 0.16D);
    }

    private static boolean inAngularCrack(double angle, double center, double halfWidth) {
        double delta = Math.atan2(Math.sin(angle - center), Math.cos(angle - center));
        return Math.abs(delta) < halfWidth;
    }

    private static BlockState mainSurface(int x, int z, double distance) {
        if (isFractureScar(x, z, distance)) {
            return Math.floorMod(x * 11 + z * 5, 4) == 0
                    ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }
        return Math.floorMod(x * 19 + z * 23, 8) <= 1
                ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private static boolean isFractureScar(int x, int z, double distance) {
        if (distance < 7.0D) return false;
        boolean first = x >= 3 && Math.abs(z - x / 3.0D) < 0.72D;
        boolean second = z <= -7 && Math.abs(x + z / 4.0D) < 0.68D;
        boolean third = x <= -8 && Math.abs(z + x / 2.7D) < 0.64D;
        return first || second || third;
    }

    private static void buildEntryTongue(ServerLevel level, int ox, int oz) {
        for (int z = 13; z <= ENTRY_Z + 2; z++) {
            int half = z >= ENTRY_Z ? 1 : 2;
            for (int x = -half; x <= half; x++) {
                if ((z == 15 && x == -2) || (z == 17 && x == 2)) continue;
                int depth = 2 + Math.floorMod(x + z, 3);
                for (int y = FLOOR_Y - depth; y < FLOOR_Y; y++) {
                    level.setBlock(new BlockPos(ox + x, y, oz + z),
                            Blocks.BLACKSTONE.defaultBlockState(), 2 | 16);
                }
                level.setBlock(new BlockPos(ox + x, FLOOR_Y, oz + z),
                        z % 3 == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void buildCentralTorii(ServerLevel level, int ox, int oz) {
        int z = oz - 1;
        // Footings and tapered vermilion pillars.
        for (int side : new int[]{-4, 4}) {
            for (int dx = -1; dx <= 1; dx++) {
                level.setBlock(new BlockPos(ox + side + dx, FLOOR_Y, z),
                        Blocks.NETHER_BRICKS.defaultBlockState(), 2 | 16);
            }
            for (int y = 1; y <= 6; y++) {
                level.setBlock(new BlockPos(ox + side, FLOOR_Y + y, z),
                        Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
                if (y <= 2) {
                    level.setBlock(new BlockPos(ox + side + (side < 0 ? -1 : 1), FLOOR_Y + y, z),
                            Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
                }
            }
        }
        // Two-layer crossbeam with exaggerated overhang so the gate dominates
        // the otherwise low, flat ritual floor.
        for (int x = -6; x <= 6; x++) {
            level.setBlock(new BlockPos(ox + x, FLOOR_Y + 6, z),
                    Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
            if (Math.abs(x) <= 5) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y + 5, z),
                        Blocks.NETHER_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
        level.setBlock(new BlockPos(ox - 7, FLOOR_Y + 6, z),
                Blocks.NETHER_BRICKS.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(ox + 7, FLOOR_Y + 6, z),
                Blocks.NETHER_BRICKS.defaultBlockState(), 2 | 16);
        level.setBlock(new BlockPos(ox, FLOOR_Y + 5, z),
                Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
    }

    private static void buildRackDais(ServerLevel level, int ox, int oz) {
        int x = ox + RACK_X;
        int z = oz + RACK_Z;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, FLOOR_Y, z + dz),
                        Math.abs(dx) == 2
                                ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void buildRitualFire(ServerLevel level, int ox, int oz) {
        int x = ox + FIRE_X;
        int z = oz + FIRE_Z;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (manhattan > 3) continue;
                level.setBlock(new BlockPos(x + dx, FLOOR_Y, z + dz),
                        manhattan == 0 ? Blocks.SOUL_SOIL.defaultBlockState()
                                : manhattan <= 1 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.NETHER_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
        level.setBlock(new BlockPos(x, FLOOR_Y + 1, z), Blocks.SOUL_FIRE.defaultBlockState(), 2 | 16);
    }

    private static void buildBrokenRing(ServerLevel level, int ox, int oz) {
        for (int i = 0; i < ISLANDS.length; i++) {
            int[] island = ISLANDS[i];
            buildFloatingFragment(level, ox + island[0], oz + island[1], island[2], island[3], i);
        }
        // A few incomplete bridge shards imply that the fragments once formed a
        // larger shrine without giving mobs a usable path to the scenery islands.
        buildBridgeShard(level, ox, oz, 0, -1, 24, 29);
        buildBridgeShard(level, ox, oz, 1, 0, 23, 27);
        buildBridgeShard(level, ox, oz, -1, 0, 24, 28);

        // Broken subsidiary torii make the outer space read as temple ruins rather
        // than random blackstone asteroids.
        buildRuinedTorii(level, ox - 31, oz - 7, true, 1);
        buildRuinedTorii(level, ox + 31, oz - 25, false, -1);
        buildRuinedTorii(level, ox + 25, oz + 31, true, -1);
    }

    private static void buildFloatingFragment(ServerLevel level, int cx, int cz,
            int radius, int yOffset, int salt) {
        int top = FLOOR_Y + yOffset;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double wobble = (Math.floorMod(x * 13 + z * 29 + salt * 17, 9) - 4) * 0.18D;
                double d = Math.sqrt(x * x + z * z);
                if (d > radius + wobble) continue;
                int depth = 2 + Math.max(0, (int) ((radius - d) / 2.0D))
                        + Math.floorMod(x * 7 + z * 11 + salt, 3);
                for (int y = top - depth; y < top; y++) {
                    level.setBlock(new BlockPos(cx + x, y, cz + z),
                            y == top - depth ? Blocks.BASALT.defaultBlockState()
                                    : Blocks.BLACKSTONE.defaultBlockState(), 2 | 16);
                }
                BlockState surface = Math.floorMod(x * 17 + z * 31 + salt, 7) == 0
                        ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                        : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                level.setBlock(new BlockPos(cx + x, top, cz + z), surface, 2 | 16);
            }
        }
    }

    private static void buildBridgeShard(ServerLevel level, int ox, int oz,
            int dx, int dz, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (i == start + 2) continue;
            int x = ox + dx * i;
            int z = oz + dz * i;
            level.setBlock(new BlockPos(x, FLOOR_Y + (i % 3 == 0 ? 1 : 0), z),
                    i % 2 == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                            : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2 | 16);
        }
    }

    private static void buildRuinedTorii(ServerLevel level, int cx, int cz,
            boolean alongX, int rise) {
        int top = FLOOR_Y + rise;
        for (int side : new int[]{-3, 3}) {
            int pillarX = cx + (alongX ? side : 0);
            int pillarZ = cz + (alongX ? 0 : side);
            int height = side < 0 ? 4 : 2;
            for (int y = 1; y <= height; y++) {
                level.setBlock(new BlockPos(pillarX, top + y, pillarZ),
                        Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
            }
        }
        for (int i = -4; i <= 1; i++) {
            int x = cx + (alongX ? i : 0);
            int z = cz + (alongX ? 0 : i);
            level.setBlock(new BlockPos(x, top + 4, z),
                    i == 1 ? Blocks.NETHER_BRICKS.defaultBlockState()
                            : Blocks.RED_NETHER_BRICKS.defaultBlockState(), 2 | 16);
        }
    }

    private static void buildBoundaryTeeth(ServerLevel level, int ox, int oz) {
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2.0D * i / 28.0D;
            int x = (int) Math.round(Math.cos(angle) * OUTER_RADIUS);
            int z = (int) Math.round(Math.sin(angle) * OUTER_RADIUS);
            int height = 3 + (i % 5);
            for (int y = 0; y < height; y++) {
                level.setBlock(new BlockPos(ox + x, FLOOR_Y - 3 + y, oz + z),
                        i % 4 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : Blocks.BASALT.defaultBlockState(), 2 | 16);
            }
        }
    }

    private static void clearLegacyV1Artifacts(ServerLevel level, int ox, int oz) {
        for (int x = -9; x <= 9; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                    level.setBlock(new BlockPos(ox + x, y, oz + z),
                            Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        for (int z = 17; z <= 27; z++) {
            level.setBlock(new BlockPos(ox, FLOOR_Y, oz + z), Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(new BlockPos(ox, FLOOR_Y, oz - z), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private DivineDomainArenaData() {
    }
}
