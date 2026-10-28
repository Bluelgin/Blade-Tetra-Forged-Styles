package dev.bladetetra.easteregg;

import net.minecraftforge.common.ForgeConfigSpec;

/** All field tuning belongs to the existing server GameplayConfig spec. */
public final class RaikiriConfig {
    public static ForgeConfigSpec.IntValue TRAIL_SAMPLES, DELAY_START, DELAY_END, TRAILS,
            SWORDS, LIFETIME, PERIOD, TARGETS, ANCHORS, MAX_OBSERVE_TICKS;
    public static ForgeConfigSpec.DoubleValue TRAIL_BUDGET, TRAIL_TARGET_CAP, STRIKE_RADIUS,
            SWORD_RANGE, ROUND_BUDGET, LIFE_BUDGET, RESONANCE_RADIUS, RESONANCE_MULTIPLIER, MAX_SEGMENT, MAX_PATH, SAMPLE_SPACING;
    public static void define(ForgeConfigSpec.Builder b) {
        b.push("raikiri3");
        MAX_OBSERVE_TICKS=b.defineInRange("maxObservationTicks",40,8,40);
        MAX_SEGMENT=b.defineInRange("maxObservedSegment",2.5D,0.5D,4D);
        MAX_PATH=b.defineInRange("maxObservedPath",12D,2D,32D);
        SAMPLE_SPACING=b.defineInRange("trailSampleSpacing",0.35D,0.05D,2D);
        TRAIL_SAMPLES=b.defineInRange("trailSamples",4,1,4);
        DELAY_START=b.defineInRange("delayStart",6,6,10);
        DELAY_END=b.defineInRange("delayEnd",10,6,10);
        TRAILS=b.defineInRange("globalTrailLimit",128,1,256);
        TRAIL_BUDGET=b.defineInRange("trailDamageBudget",6D,0D,32D);
        TRAIL_TARGET_CAP=b.defineInRange("trailTargetCap",3D,0D,16D);
        STRIKE_RADIUS=b.defineInRange("strikeRadius",1.25D,0.25D,2D);
        SWORDS=b.defineInRange("swordsPerPlayer",3,1,3);
        ANCHORS=b.defineInRange("globalSwordLimit",256,1,512);
        LIFETIME=b.defineInRange("swordLifetime",120,15,200);
        PERIOD=b.defineInRange("dischargePeriod",15,10,40);
        TARGETS=b.defineInRange("targetsPerRound",2,1,2);
        SWORD_RANGE=b.defineInRange("swordRange",4D,1D,4D);
        ROUND_BUDGET=b.defineInRange("roundDamageBudget",3D,0D,16D);
        LIFE_BUDGET=b.defineInRange("lifetimeDamageBudget",12D,0D,64D);
        RESONANCE_RADIUS=b.defineInRange("resonanceRadius",2D,0.25D,3D);
        RESONANCE_MULTIPLIER=b.defineInRange("resonanceMultiplier",1.5D,1D,2D);
        b.pop();
    }
    private RaikiriConfig() {}
}
