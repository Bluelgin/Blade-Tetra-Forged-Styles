package dev.bladetetra.challenge;

import net.minecraft.network.chat.Component;

/**
 * Difficulty derived from the frozen SlashBlade kill-count snapshot carried by
 * the ritual puppet. Scaling deliberately changes encounter structure instead
 * of relying on a single health multiplier.
 */
public enum DivineDomainTier {
    ECHO("tier.blade_tetra.divine.echo", 0L, 2, 3, 0.00D, 2, 0),
    GRUDGE("tier.blade_tetra.divine.grudge", 51L, 3, 4, 0.10D, 3, 0),
    HUNDRED_GHOSTS("tier.blade_tetra.divine.hundred_ghosts", 201L, 4, 5, 0.18D, 4, 100),
    ASURA("tier.blade_tetra.divine.asura", 501L, 5, 8, 0.33D, 8, 72),
    AVICI("tier.blade_tetra.divine.avici", 1001L, 6, 10, 0.40D, 8, 52);

    private final String displayNameKey;
    private final long minimumKills;
    private final int waves;
    private final int baseConcurrent;
    private final double eliteChance;
    private final int spawnDirections;
    private final int pressureIntervalTicks;

    DivineDomainTier(String displayNameKey, long minimumKills, int waves,
            int baseConcurrent, double eliteChance, int spawnDirections,
            int pressureIntervalTicks) {
        this.displayNameKey = displayNameKey;
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

    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    public long minimumKills() {
        return minimumKills;
    }

    public int waves() {
        return waves;
    }

    public int concurrentForWave(int wave) {
        return Math.min(this == ASURA ? 10 : this == AVICI ? 12 : 10,
                baseConcurrent + Math.max(0, wave - 1) / 2);
    }

    public int hostileCap() { return this == AVICI ? 20 : this == ASURA ? 16 : 12; }
    public int reinforcementBudget() { return this == AVICI ? 12 : this == ASURA ? 8 : this == HUNDRED_GHOSTS ? 4 : 0; }
    public boolean hasSupport() { return ordinal() >= HUNDRED_GHOSTS.ordinal(); }
    public boolean hasAdvancedRoles() { return ordinal() >= ASURA.ordinal(); }

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
