package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** World/server-owned gameplay controls with current balance as defaults. */
public final class GameplayConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EASTER_EGG_UNLOCKS;
    public static final ForgeConfigSpec.DoubleValue SMITHY_CLUE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTRACT_BLADE_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEVELOPER_TOOLS;
    public static final ForgeConfigSpec.DoubleValue MIRROR_SLASH_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue MIRROR_SLASH_DAMAGE_CAP;
    public static final ForgeConfigSpec.IntValue MIRROR_SLASH_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue MIRROR_SLASH_COOLDOWN_TICKS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PLAYER_LIGHTNING_ATTRACTION;
    public static final ForgeConfigSpec.IntValue ATTRACTION_ROLL_DENOMINATOR;
    public static final ForgeConfigSpec.DoubleValue LIGHTNING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_OFFENSIVE_LIGHTNING;
    public static final ForgeConfigSpec.DoubleValue OFFENSIVE_CHANCE_PER_POINT;
    public static final ForgeConfigSpec.DoubleValue OFFENSIVE_CHANCE_CAP;
    public static final ForgeConfigSpec.DoubleValue RAIKIRI_OFFENSIVE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue OFFENSIVE_DAMAGE_BASE;
    public static final ForgeConfigSpec.DoubleValue OFFENSIVE_DAMAGE_PER_POINT;
    public static final ForgeConfigSpec.DoubleValue OFFENSIVE_DAMAGE_CAP;
    public static final ForgeConfigSpec.IntValue OFFENSIVE_COOLDOWN_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("discoveries");
        ENABLE_EASTER_EGG_UNLOCKS = builder
                .comment(
                        "Allow new blade legacy and easter-egg progress/unlocks.",
                        "Existing unlocked blade tags are never deleted when disabled.")
                .define("enableEasterEggUnlocks", true);
        SMITHY_CLUE_CHANCE = builder
                .comment(
                        "Chance that a village weaponsmith chest receives one smithing clue.",
                        "0 disables clue injection; 1 guarantees one clue.")
                .defineInRange("smithyClueChance", 0.32D, 0.0D, 1.0D);
        ENABLE_CONTRACT_BLADE_INTEGRATION = builder
                .comment(
                        "Enable optional Akatsuki spirit binding when Contract Blade Core is installed.",
                        "Disabling this does not delete spirits already created by Contract Blade Core.")
                .define("enableContractBladeIntegration", true);
        ENABLE_DEVELOPER_TOOLS = builder
                .comment(
                        "Allow command-obtained developer tools in a packaged game.",
                        "Tools still require the exact player name Dev and have no recipe or creative-tab entry.",
                        "Local Forge development runs allow Dev automatically even when this is false.")
                .define("enableDeveloperTools", false);
        MIRROR_SLASH_DAMAGE_RATIO = builder
                .comment("Kyouka reflected-cut damage as a fraction of the triggering Iaido hit.")
                .defineInRange("mirrorSlashDamageRatio", 0.35D, 0.0D, 2.0D);
        MIRROR_SLASH_DAMAGE_CAP = builder
                .comment("Maximum damage dealt by one delayed reflected cut.")
                .defineInRange("mirrorSlashDamageCap", 12.0D, 0.0D, 1000.0D);
        MIRROR_SLASH_DELAY_TICKS = builder
                .comment("Delay before Kyouka's reflected cut; 20 ticks are one second.")
                .defineInRange("mirrorSlashDelayTicks", 10, 1, 100);
        MIRROR_SLASH_COOLDOWN_TICKS = builder
                .comment("Per-blade cooldown between reflected cuts.")
                .defineInRange("mirrorSlashCooldownTicks", 24, 1, 1200);
        builder.pop();

        builder.push("conductivity");
        ENABLE_PLAYER_LIGHTNING_ATTRACTION = builder
                .comment("Allow conductive held blades to attract real lightning in open storms.")
                .define("enablePlayerLightningAttraction", true);
        ATTRACTION_ROLL_DENOMINATOR = builder
                .comment(
                        "Attraction is rolled once per second with chance conductivity / this value.",
                        "Default 1200 means one point averages 20 minutes of exposed thunder time.")
                .defineInRange("attractionRollDenominator", 1200, 20, 72000);
        LIGHTNING_DAMAGE_MULTIPLIER = builder
                .comment("Incoming lightning damage multiplier while holding a conductive blade.")
                .defineInRange("lightningDamageMultiplier", 0.65D, 0.0D, 1.0D);
        ENABLE_OFFENSIVE_LIGHTNING = builder
                .comment("Allow conductive blade hits to call controlled lightning during storms.")
                .define("enableOffensiveLightning", true);
        OFFENSIVE_CHANCE_PER_POINT = builder
                .comment("Offensive lightning chance added by each conductivity point.")
                .defineInRange("offensiveChancePerPoint", 0.02D, 0.0D, 1.0D);
        OFFENSIVE_CHANCE_CAP = builder
                .comment("Maximum offensive lightning chance for ordinary conductive blades.")
                .defineInRange("offensiveChanceCap", 0.08D, 0.0D, 1.0D);
        RAIKIRI_OFFENSIVE_CHANCE = builder
                .comment("Fixed offensive lightning chance for an unlocked Raikiri.")
                .defineInRange("raikiriOffensiveChance", 0.12D, 0.0D, 1.0D);
        OFFENSIVE_DAMAGE_BASE = builder
                .comment("Base damage of a controlled offensive lightning strike.")
                .defineInRange("offensiveDamageBase", 4.0D, 0.0D, 100.0D);
        OFFENSIVE_DAMAGE_PER_POINT = builder
                .comment("Additional offensive lightning damage per conductivity point.")
                .defineInRange("offensiveDamagePerPoint", 0.5D, 0.0D, 100.0D);
        OFFENSIVE_DAMAGE_CAP = builder
                .comment("Maximum damage of a controlled offensive lightning strike.")
                .defineInRange("offensiveDamageCap", 7.0D, 0.0D, 100.0D);
        OFFENSIVE_COOLDOWN_TICKS = builder
                .comment("Per-blade offensive lightning cooldown in ticks; 20 ticks are one second.")
                .defineInRange("offensiveCooldownTicks", 140, 0, 12000);
        builder.pop();

        SPEC = builder.build();
    }

    private GameplayConfig() {
    }
}
