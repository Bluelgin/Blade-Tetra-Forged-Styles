package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** World/server-owned gameplay controls with current balance as defaults. */
public final class GameplayConfig {
    public static final ForgeConfigSpec.BooleanValue ENABLE_SPEED_EFFICIENCY;
    public static final ForgeConfigSpec.DoubleValue SPEED_EFFICIENCY_REFERENCE;
    public static final ForgeConfigSpec.DoubleValue SPEED_EFFICIENCY_MAX_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue SPEED_EFFICIENCY_SWORD_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue SPEED_EFFICIENCY_SA_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue SPEED_EFFICIENCY_SUPER_REDUCTION;
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EASTER_EGG_UNLOCKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AWAKENED_V2;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AWAKENED_RANK_COSTS;
    public static final ForgeConfigSpec.IntValue AWAKENED_SAKURA_BURN_POINTS_PER_SECOND;
    public static final ForgeConfigSpec.IntValue KYOUKA_START_COST, KYOUKA_DRAIN, KYOUKA_ACTION_COST;
    public static final ForgeConfigSpec.IntValue KYOUKA_MAX_SESSIONS, KYOUKA_WORLD_SESSIONS;
    public static final ForgeConfigSpec.DoubleValue KYOUKA_RANGE;

    public static final ForgeConfigSpec.IntValue SAKURA_SA_MAX_TICKS, SAKURA_CONCERTO_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SAKURA_CONCERTO_FRACTION, SAKURA_CONCERTO_CAP, SAKURA_SA_STAGE_DAMAGE, SAKURA_RECALL_DAMAGE_SCALE;
    public static final ForgeConfigSpec.DoubleValue SMITHY_CLUE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FORGING_SCROLL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FORGING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SALTY_FISH_SCROLL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SALTY_FISH_BLADE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue TECHNIQUE_SCROLL_CHANCE;
    public static final ForgeConfigSpec.BooleanValue GIVE_STARTER_MANUAL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTRACT_BLADE_INTEGRATION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEVELOPER_TOOLS;
    public static final ForgeConfigSpec.DoubleValue MIRROR_SLASH_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue MIRROR_SLASH_DAMAGE_CAP;
    public static final ForgeConfigSpec.IntValue MIRROR_SLASH_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue MIRROR_SLASH_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue MIRROR_REFLECTION_WINDOW_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIRROR_BREAK_SA_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue MIRROR_BREAK_RECORDED_DAMAGE_RATIO;
    public static final ForgeConfigSpec.DoubleValue MIRROR_BREAK_DAMAGE_CAP;
    public static final ForgeConfigSpec.IntValue MIRROR_BREAK_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue MIRROR_BREAK_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_REQUIRED_CONTRIBUTION;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_THRESHOLD_HIT_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_THRESHOLD_MIN_FRACTION;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_THRESHOLD_MAX_FRACTION;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_EXECUTION_DAMAGE_FRACTION;
    public static final ForgeConfigSpec.IntValue AKATSUKI_EXECUTION_PULSE_TICKS;
    public static final ForgeConfigSpec.IntValue AKATSUKI_EXECUTION_MAINTAIN_TICKS;
    public static final ForgeConfigSpec.IntValue AKATSUKI_EXECUTION_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.IntValue AKATSUKI_EXECUTION_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_EXECUTION_MINIMUM_TARGET_HEALTH;

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

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_SEALING;
    public static final ForgeConfigSpec.BooleanValue BLADE_SEALING_OPERATORS_ONLY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("bladeSealing");
        ENABLE_BLADE_SEALING = builder.comment("Allow Blade Studio to seal weapons on this world.")
                .define("enabled", true);
        BLADE_SEALING_OPERATORS_ONLY = builder.comment("Require operator level 2 to seal weapons on dedicated servers.")
                .define("operatorsOnly", true);
        builder.pop();

        builder.push("discoveries");
        ENABLE_EASTER_EGG_UNLOCKS = builder
                .comment(
                        "Allow new blade legacy and easter-egg progress/unlocks.",
                        "Existing unlocked blade tags are never deleted when disabled.")
                .define("enableEasterEggUnlocks", true);
        ENABLE_AWAKENED_V2 = builder
                .comment("Use True Name Release combat on awakened blades.",
                        "When false, keeps original awakened combat behavior; unlocks remain saved.")
                .define("enableAwakenedV2", true);

        builder.comment("Legacy section name retained for saved configurations; costs below are retired in Kyouka 4.").push("kyouka3");
        KYOUKA_START_COST = builder.comment("Deprecated: ignored by Kyouka 4.").defineInRange("startCost", 60, 0, 1800);
        KYOUKA_DRAIN = builder.comment("Deprecated: ignored by Kyouka 4.").defineInRange("drainPerSecond", 12, 0, 600);
        KYOUKA_ACTION_COST = builder.comment("Deprecated: ignored by Kyouka 4.").defineInRange("actionCost", 18, 0, 600);
        KYOUKA_RANGE = builder.defineInRange("range", 32.0, 4.0, 64.0);
        KYOUKA_MAX_SESSIONS = builder.defineInRange("serverSessions", 64, 1, 256);
        KYOUKA_WORLD_SESSIONS = builder.defineInRange("worldSessions", 32, 1, 128);
        builder.pop();
        ENABLE_AWAKENED_RANK_COSTS = builder
                .comment("Spend native SlashBlade Rank during Senbonzakura infinite dance SA and Raikiri thunder armor.",
                        "Kyouka 4 and native Raikiri attacks never use extra Rank payments.")
                .define("enableAwakenedRankCosts", true);
        builder
                .comment("Deprecated V2 replay price; ignored by Three Mirrors.")
                .defineInRange("awakenedRankCostKyouka", 300, 0, 1800);
        builder
                .comment("Deprecated compatibility key: ignored by Senbonzakura 3.0; SA uses its dedicated drain setting.")
                .defineInRange("awakenedRankCostSakuraArray", 300, 0, 1800);
        AWAKENED_SAKURA_BURN_POINTS_PER_SECOND = builder
                .comment("Base native Rank points spent per second of Senbonzakura infinite dance SA.",
                        "A pays 100%, S 88%, SS 80%, SSS 67%. Ordinary attacks do not burn Rank.")
                .defineInRange("awakenedSakuraFlowDrainPerSecond", 120, 1, 600);
        SAKURA_SA_MAX_TICKS = builder.comment("Maximum infinite dance SA duration; finishes the current native action.")
                .defineInRange("sakuraSaMaxTicks", 1200, 40, 6000);
        SAKURA_CONCERTO_INTERVAL = builder.comment("Minimum server ticks between ordinary shard concerto launches.")
                .defineInRange("sakuraConcertoInterval", 8, 6, 40);
        SAKURA_CONCERTO_FRACTION = builder.defineInRange("sakuraConcertoDamageFraction", .25, 0., .5);
        SAKURA_CONCERTO_CAP = builder.defineInRange("sakuraConcertoDamageCap", 6., 0., 20.);
        SAKURA_RECALL_DAMAGE_SCALE = builder.comment("Trial multiplier for charged Sakura recall (base attack x 4/6.5/10/14). No fixed damage ceiling.")
                .defineInRange("sakuraRecallDamageScale", 1., .1, 10.);
        SAKURA_SA_STAGE_DAMAGE = builder.comment("Total additional damage budget per SA stage, split across its pulses.")
                .defineInRange("sakuraSaStageDamage", 8., 0., 32.);
        builder
                .comment("Deprecated compatibility key: ignored. Native rapid movement and its lightning extension have no extra Rank cost.")
                .defineInRange("awakenedRankCostRaikiriStep", 75, 0, 1800);
        dev.bladetetra.easteregg.RaikiriConfig.define(builder);
        builder
                .comment("Deprecated compatibility key: ignored by Raikiri V2/3. Native SA has no extra finisher cost.")
                .defineInRange("awakenedRankCostRaikiriFinisher", 240, 0, 1800);
        builder
                .comment("Deprecated compatibility key: ignored by integrated awakened combat.")
                .defineInRange("awakenedRankCostDualSurcharge", 120, 0, 1800);
        SMITHY_CLUE_CHANCE = builder
                .comment(
                        "Chance that a village weaponsmith chest receives one smithing clue.",
                        "0 disables clue injection; 1 guarantees one clue.")
                .defineInRange("smithyClueChance", 0.32D, 0.0D, 1.0D);
        FORGING_SCROLL_CHANCE = builder
                .comment(
                        "Chance that a village weaponsmith chest receives one Blade Tetra forging scroll.",
                        "0 disables scroll injection; 1 guarantees one scroll per matching chest.")
                .defineInRange("forgingScrollChance", 0.55D, 0.0D, 1.0D);
        SALTY_FISH_SCROLL_CHANCE = builder
                .comment("Bonus scroll chance per vanilla fishing table roll, independent of Luck of the Sea.")
                .defineInRange("saltyFishScrollChance", 0.01D, 0.0D, 1.0D);
        SALTY_FISH_BLADE_CHANCE = builder
                .comment("Bonus assembled blade chance per vanilla fishing table roll.")
                .defineInRange("saltyFishBladeChance", 0.001D, 0.0D, 1.0D);
        TECHNIQUE_SCROLL_CHANCE = builder
                .comment(
                        "Chance that a matching structure chest receives its Blade Tetra technique scroll.",
                        "0 disables technique-scroll injection; 1 guarantees one scroll per matching chest.")
                .defineInRange("techniqueScrollChance", 0.35D, 0.0D, 1.0D);
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
                .comment("Kyouka reflected-cut damage as a fraction of the previous recorded Iaido hit.")
                .defineInRange("mirrorSlashDamageRatio", 0.40D, 0.0D, 2.0D);
        MIRROR_SLASH_DAMAGE_CAP = builder
                .comment("Maximum damage dealt by one delayed reflected cut.")
                .defineInRange("mirrorSlashDamageCap", 12.0D, 0.0D, 1000.0D);
        MIRROR_SLASH_DELAY_TICKS = builder
                .comment("Delay before Kyouka's reflected cut; 20 ticks are one second.")
                .defineInRange("mirrorSlashDelayTicks", 10, 1, 100);
        MIRROR_SLASH_COOLDOWN_TICKS = builder
                .comment("Per-blade cooldown between reflected cuts.")
                .defineInRange("mirrorSlashCooldownTicks", 8, 1, 1200);
        MIRROR_REFLECTION_WINDOW_TICKS = builder
                .comment("How long one real Iaido cut remains recorded for Kyouka; 20 ticks are one second.")
                .defineInRange("mirrorReflectionWindowTicks", 60, 5, 1200);
        MIRROR_BREAK_SA_DAMAGE_RATIO = builder
                .comment("Water Moon Break damage inherited from the real, post-mitigation Slash Art hit.")
                .defineInRange("mirrorBreakSlashArtDamageRatio", 0.35D, 0.0D, 2.0D);
        MIRROR_BREAK_RECORDED_DAMAGE_RATIO = builder
                .comment("Water Moon Break damage inherited from the recorded Iaido hit.")
                .defineInRange("mirrorBreakRecordedDamageRatio", 0.35D, 0.0D, 2.0D);
        MIRROR_BREAK_DAMAGE_CAP = builder
                .comment("Maximum total damage dealt by both Water Moon Break cuts.")
                .defineInRange("mirrorBreakDamageCap", 18.0D, 0.0D, 1000.0D);
        MIRROR_BREAK_DELAY_TICKS = builder
                .comment("Delay before Water Moon Break begins after a successful Slash Art.")
                .defineInRange("mirrorBreakDelayTicks", 14, 1, 100);
        MIRROR_BREAK_COOLDOWN_TICKS = builder
                .comment("Per-blade cooldown between Water Moon Break activations.")
                .defineInRange("mirrorBreakCooldownTicks", 100, 1, 12000);
        AKATSUKI_REQUIRED_CONTRIBUTION = builder
                .comment("Fraction of a target's maximum health Akatsuki must personally damage before Final Moon can begin.")
                .defineInRange("akatsukiRequiredContribution", 0.60D, 0.0D, 1.0D);
        AKATSUKI_THRESHOLD_HIT_MULTIPLIER = builder
                .comment("Recent effective hits represented by Akatsuki's dynamic execution threshold.")
                .defineInRange("akatsukiThresholdHitMultiplier", 4.0D, 1.0D, 20.0D);
        AKATSUKI_THRESHOLD_MIN_FRACTION = builder
                .comment("Minimum Final Moon threshold as a fraction of target maximum health.")
                .defineInRange("akatsukiThresholdMinimumFraction", 0.06D, 0.0D, 0.50D);
        AKATSUKI_THRESHOLD_MAX_FRACTION = builder
                .comment("Maximum Final Moon threshold as a fraction of target maximum health.")
                .defineInRange("akatsukiThresholdMaximumFraction", 0.12D, 0.0D, 0.50D);
        AKATSUKI_EXECUTION_DAMAGE_FRACTION = builder
                .comment("Damage of each Final Moon cut as a fraction of Akatsuki's recent effective hit damage.")
                .defineInRange("akatsukiExecutionCutDamageFraction", 0.12D, 0.01D, 1.0D);
        AKATSUKI_EXECUTION_PULSE_TICKS = builder
                .comment("Ticks between real Final Moon cuts. Visual cuts are reconstructed more densely on clients.")
                .defineInRange("akatsukiExecutionPulseTicks", 2, 1, 20);
        AKATSUKI_EXECUTION_MAINTAIN_TICKS = builder
                .comment("Maximum time between the player's own Akatsuki hits before Final Moon begins to fail.")
                .defineInRange("akatsukiExecutionMaintainTicks", 40, 5, 200);
        AKATSUKI_EXECUTION_TIMEOUT_TICKS = builder
                .comment("Absolute compatibility fuse for one Final Moon execution.")
                .defineInRange("akatsukiExecutionTimeoutTicks", 160, 20, 1200);
        AKATSUKI_EXECUTION_COOLDOWN_TICKS = builder
                .comment("Per-blade cooldown after Final Moon begins or the blood threads first execute a target.")
                .defineInRange("akatsukiExecutionCooldownTicks", 500, 0, 12000);
        AKATSUKI_EXECUTION_MINIMUM_TARGET_HEALTH = builder
                .comment("Minimum target maximum health for the full Final Moon execution; smaller enemies keep ordinary combat flow.")
                .defineInRange("akatsukiExecutionMinimumTargetHealth", 40.0D, 1.0D, 1000000.0D);
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

        builder.push("speedEfficiency");
        ENABLE_SPEED_EFFICIENCY = builder
                .comment("Convert held modular blade attack speed into preparation/recovery efficiency, not damage or combo speed.")
                .define("enabled", true);
        SPEED_EFFICIENCY_REFERENCE = builder
                .comment("Attack-speed reference in attacks/second. At or below this value timings remain native; twice this value reaches the cap.")
                .defineInRange("referenceAttackSpeed", 1.6D, .1D, 20D);
        SPEED_EFFICIENCY_MAX_REDUCTION = builder
                .comment("Ground-dodge recovery reduction only. Keeps the original key and existing value for compatibility; other actions now have separate caps. Native invulnerability remains unchanged.")
                .defineInRange("maximumTimeReduction", .20D, 0D, .20D);
        SPEED_EFFICIENCY_SWORD_REDUCTION = builder
                .comment("Special summoned-sword preparation reduction; linear attack-speed scaling, native damage and costs unchanged.")
                .defineInRange("swordPreparationReduction", .40D, 0D, .40D);
        SPEED_EFFICIENCY_SA_REDUCTION = builder
                .comment("Normal SA preparation reduction; readiness feedback and release threshold advance together.")
                .defineInRange("normalSaPreparationReduction", 1D / 3D, 0D, 1D / 3D);
        SPEED_EFFICIENCY_SUPER_REDUCTION = builder
                .comment("Super SA preparation reduction; does not shorten the released attack animation.")
                .defineInRange("superSaPreparationReduction", .30D, 0D, .30D);
        builder.pop();

        builder.translation("config.blade_tetra.manual").push("manual");
        GIVE_STARTER_MANUAL = builder
                .comment("Give each player one Bladecraft Manual on login when Patchouli is installed.")
                .translation("config.blade_tetra.manual.giveStarterManual")
                .define("giveStarterManual", true);
        builder.pop();

        builder.translation("config.blade_tetra.forgingTreatises").push("forgingTreatises");
        FORGING_DAMAGE_MULTIPLIER = builder
                .comment("Scales the attack damage bonuses from flat edge, thin edge, hon-sanmai and shihozume. Default: 1.0. Set to 0 to disable these bonuses.",
                        "调整平刃研磨、薄刃研磨、本三枚锻和四方诘锻的攻击力加成。默认 1.0，设为 0 可关闭这四项伤害加成。")
                .translation("config.blade_tetra.forgingTreatises.damageMultiplier")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 10.0D);
        builder.pop();

        CombatBalanceConfig.define(builder);
        SPEC = builder.build();
    }

    private GameplayConfig() {
    }
}
