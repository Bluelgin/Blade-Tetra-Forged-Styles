package dev.bladetetra.challenge;

/**
 * Difficulty derived from the frozen SlashBlade kill-count snapshot carried by
 * the ritual puppet. Scaling deliberately changes encounter structure instead
 * of relying on a single health multiplier.
 */
public enum DivineDomainTier {
    ECHO("残响", 0L, 2, 3, 0.00D, 2, 0),
    GRUDGE("怨聚", 51L, 3, 4, 0.10D, 3, 0),
    HUNDRED_GHOSTS("百鬼", 201L, 4, 5, 0.18D, 4, 100),
    ASURA("修罗", 501L, 5, 6, 0.28D, 6, 72),
    AVICI("无间", 1001L, 6, 7, 0.38D, 8, 52);

    private final String displayName;
    private final long minimumKills;
    private final int waves;
    private final int baseConcurrent;
    private final double eliteChance;
    private final int spawnDirections;
    private final int pressureIntervalTicks;

    DivineDomainTier(String displayName, long minimumKills, int waves,
            int baseConcurrent, double eliteChance, int spawnDirections,
            int pressureIntervalTicks) {
        this.displayName = displayName;
        this.minimumKills = minimumKills;
        this.waves = waves;
        this.baseConcurrent = baseConcurrent;
        this.eliteChance = eliteChance;
        this.spawnDirections = spawnDirections;
        this.pressureIntervalTicks = pressureIntervalTicks;
    }

    public static DivineDomainTier fromKills(long kills) {
        long safe = Math.max(0L, kills);
        DivineDomainTier selected = ECHO;
        for (DivineDomainTier tier : values()) {
            if (safe >= tier.minimumKills) {
                selected = tier;
            }
        }
        return selected;
    }

    public String displayName() {
        return displayName;
    }

    public long minimumKills() {
        return minimumKills;
    }

    public int waves() {
        return waves;
    }

    public int concurrentForWave(int wave) {
        return baseConcurrent + Math.max(0, wave - 1) / 2;
    }

    public double eliteChance() {
        return eliteChance;
    }

    public int spawnDirections() {
        return spawnDirections;
    }

    public int pressureIntervalTicks() {
        return pressureIntervalTicks;
    }

    public boolean grantsDeadThoughtSeal() {
        return this == ASURA || this == AVICI;
    }
}
