package dev.bladetetra.challenge;

import dev.bladetetra.BladeTetra;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/** Frozen timing and geometry for the existing moveset; replacement skills own their definitions. */
final class MikageLegacyTiming {
    static final int TORII_SWEEP_TOTAL_TICKS = 154;
    static final int TORII_SWEEP_FIRST_IMPACT_AGE = 34;
    static final int TORII_SWEEP_SECOND_IMPACT_AGE = 72;
    static final int TORII_SWEEP_FAKE_IMPACT_AGE = 108;
    static final int TORII_SWEEP_FINAL_IMPACT_AGE = 136;
    static final int TORII_SWEEP_STABLE_GUARD_TICKS = 6;
    static final int TORII_SWEEP_RECOVERY_TICKS = 18;
    static final int TORII_CAGE_TOTAL_TICKS = 110;
    static final int TORII_CAGE_WARNING_TICKS = 25;
    static final int TORII_CAGE_RECOVERY_TICKS = 10;
    static final double TORII_CAGE_RADIUS = 4.6D;
    static final int PURSUIT_RAIN_WARNING_TICKS = 20;
    static final int PURSUIT_RAIN_FINAL_TICKS = 22;
    static final int PURSUIT_RAIN_FINAL_LAUNCH_TICK = 8;
    static final int MIRROR_DUEL_TOTAL_TICKS = 30;
    static final int MIRROR_DUEL_DASH_START = 16;
    static final int BOUNDARY_SEAL_TOTAL_TICKS = 140;
    static final int MOON_ECHO_TOTAL_TICKS = 72;
    static final int BOUNDARY_FLASH_TOTAL_TICKS = 230;
    static final int BOUNDARY_FLASH_ASCEND_TICKS = 32;
    static final int BOUNDARY_FLASH_LOCK_AGE = 180;
    static final int BOUNDARY_FLASH_IMPACT_AGE = 206;
    static final int BOUNDARY_FLASH_DESCEND_AGE = 216;
    static final int BOUNDARY_WALL_VISUAL_TICKS = 1_000_000;
    static final int BOUNDARY_WALL_MAX_COUNT = 6;
    static final int BOUNDARY_GAP_OPEN_TICKS = 60;
    static final int BOUNDARY_GAP_WARNING_TICKS = 20;
    static final double BOUNDARY_GAP_HALF_WIDTH = 1.65D;
    static final int BOUNDARY_FLAME_DAMAGE_INTERVAL = 4;
    static final float BOUNDARY_FLAME_HEALTH_FRACTION = 0.085F;
    static final double BOUNDARY_FLAME_HALF_WIDTH = 1.05D;
    static final double BOUNDARY_FLAME_HEIGHT = 4.25D;
    static final double BOUNDARY_FLASH_LENGTH = 53.0D;
    static final ResourceKey<DamageType> BOUNDARY_FLAME_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(BladeTetra.MOD_ID, "boundary_flame"));

    private MikageLegacyTiming() {}
}
