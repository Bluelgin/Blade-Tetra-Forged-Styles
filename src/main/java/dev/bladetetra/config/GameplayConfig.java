package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** World/server-owned gameplay controls with current balance as defaults. */
public final class GameplayConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EASTER_EGG_UNLOCKS;
    public static final ForgeConfigSpec.DoubleValue SMITHY_CLUE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FORGING_SCROLL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue TECHNIQUE_SCROLL_CHANCE;
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
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BASE_HEALTH;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_PARTY_HEALTH_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_REMINISCENCE_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BASE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_PARTY_DAMAGE_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_REMINISCENCE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_SOFT_CAP_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_SOFT_CAP_OVERFLOW_RATIO;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_SINGLE_HIT_CAP;
    public static final ForgeConfigSpec.IntValue MIRROR_ARENA_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_CAGE_PULSE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_CAGE_GUARD_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_CAGE_FINAL_DAMAGE;
    public static final ForgeConfigSpec.IntValue MIKAGE_CAGE_PERFECT_GUARD_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_CAGE_STAGGER_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_CAGE_STAGGER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_PURSUIT_RAIN_HIT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue MIKAGE_PURSUIT_RAIN_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_PURSUIT_RAIN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_PURSUIT_RAIN_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_PURSUIT_RAIN_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_PURSUIT_RAIN_FINAL_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_PURSUIT_RAIN_STAGGER_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_PURSUIT_RAIN_STAGGER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_STEP_IAIDO_OPENING_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_STEP_IAIDO_OPENING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_DUEL_OPENING_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_DUEL_OPENING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_SEAL_OPENING_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_SEAL_OPENING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_ECHO_OPENING_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_ECHO_OPENING_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_SHADOW_CROSS_THRESHOLD;
    public static final ForgeConfigSpec.IntValue MIKAGE_SHADOW_CROSS_WINDOW_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_ZANSHIN_WARNING_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_ZANSHIN_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue MIKAGE_ADAPTIVE_PLAYER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_ADAPTIVE_DAMAGE_MAX_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_BOUNDARY_ASSIST_LOW_HITS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_REPEATED_SA_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MIRROR_COUNTER_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_JUDGEMENT_CHAIN_WINDOW_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_JUDGEMENT_LOCKOUT_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_JUDGEMENT_RESET_TICKS;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_SECOND_JUDGEMENT_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MIKAGE_HURT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_OPENING_HURT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue MIKAGE_PHASE_PROTECTION_TICKS;
    public static final ForgeConfigSpec.BooleanValue MIKAGE_AUTO_DIFFICULTY_SCALING;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_EQUIPMENT_HEALTH_SCALE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_DEFENSE_DAMAGE_SCALE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_SKILL_SPEED_SCALE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_SCORED_WEAPON_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_SCORED_PLAYER_HEALTH;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_SCORED_ARMOR;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MAX_SCORED_ARMOR_TOUGHNESS;
    public static final ForgeConfigSpec.BooleanValue MIKAGE_ENABLE_PHASE_HEALTH_GATES;

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
        FORGING_SCROLL_CHANCE = builder
                .comment(
                        "Chance that a village weaponsmith chest receives one Blade Tetra forging scroll.",
                        "0 disables scroll injection; 1 guarantees one scroll per matching chest.")
                .defineInRange("forgingScrollChance", 0.55D, 0.0D, 1.0D);
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
                .comment("Per-blade cooldown after Final Moon begins.")
                .defineInRange("akatsukiExecutionCooldownTicks", 500, 0, 12000);
        AKATSUKI_EXECUTION_MINIMUM_TARGET_HEALTH = builder
                .comment("Minimum target maximum health for the full Final Moon execution; smaller enemies keep ordinary combat flow.")
                .defineInRange("akatsukiExecutionMinimumTargetHealth", 40.0D, 1.0D, 1000000.0D);
        builder.pop();

        builder.push("mikageChallenge");
        MIKAGE_BASE_HEALTH = builder
                .comment("Mikage's health for a one-player normal challenge.")
                .defineInRange("baseHealth", 560.0D, 40.0D, 100000.0D);
        MIKAGE_PARTY_HEALTH_PER_PLAYER = builder
                .comment("Additional health fraction for every participant after the first.")
                .defineInRange("partyHealthPerExtraPlayer", 0.65D, 0.0D, 5.0D);
        MIKAGE_REMINISCENCE_HEALTH_MULTIPLIER = builder
                .defineInRange("reminiscenceHealthMultiplier", 1.25D, 1.0D, 10.0D);
        MIKAGE_BASE_DAMAGE = builder
                .defineInRange("baseDamage", 15.0D, 1.0D, 1000.0D);
        MIKAGE_PARTY_DAMAGE_PER_PLAYER = builder
                .comment("Small damage increase per extra player; health remains the main scaling axis.")
                .defineInRange("partyDamagePerExtraPlayer", 0.06D, 0.0D, 1.0D);
        MIKAGE_REMINISCENCE_DAMAGE_MULTIPLIER = builder
                .defineInRange("reminiscenceDamageMultiplier", 1.20D, 1.0D, 10.0D);
        MIKAGE_SOFT_CAP_THRESHOLD = builder
                .comment("Hits above this value are compressed rather than hard-capped.")
                .defineInRange("softCapThreshold", 20.0D, 0.0D, 10000.0D);
        MIKAGE_SOFT_CAP_OVERFLOW_RATIO = builder
                .defineInRange("softCapOverflowRatio", 0.35D, 0.0D, 1.0D);
        MIKAGE_SINGLE_HIT_CAP = builder
                .defineInRange("singleHitCap", 45.0D, 1.0D, 100000.0D);
        MIKAGE_CAGE_PULSE_DAMAGE = builder
                .comment("Each unguarded Eightfold Torii pressure pulse as a fraction of Mikage's damage.")
                .defineInRange("cagePulseDamageMultiplier", 0.28D, 0.0D, 10.0D);
        MIKAGE_CAGE_GUARD_MULTIPLIER = builder
                .comment("Damage retained while the player is successfully holding SlashBlade guard.")
                .defineInRange("cageGuardDamageMultiplier", 0.10D, 0.0D, 1.0D);
        MIKAGE_CAGE_FINAL_DAMAGE = builder
                .comment("Final pressure burst as a fraction of Mikage's damage when guard is not completed.")
                .defineInRange("cageFinalDamageMultiplier", 0.40D, 0.0D, 20.0D);
        MIKAGE_CAGE_PERFECT_GUARD_TICKS = builder
                .comment("Guarded ticks required to break Eightfold Torii and stagger Mikage.")
                .defineInRange("cagePerfectGuardTicks", 50, 1, 100);
        MIKAGE_CAGE_STAGGER_TICKS = builder
                .comment("Shared damage-window duration after a perfect Eightfold Torii guard.")
                .defineInRange("cageStaggerTicks", 60, 10, 400);
        MIKAGE_CAGE_STAGGER_DAMAGE_MULTIPLIER = builder
                .comment("Damage Mikage receives during the stagger window.")
                .defineInRange("cageStaggerDamageMultiplier", 1.50D, 1.0D, 10.0D);
        MIKAGE_PURSUIT_RAIN_HIT_THRESHOLD = builder
                .comment("Distinct effective hits required to call Pursuing Phantom Sword Rain on a player.")
                .defineInRange("pursuitRainHitThreshold", 6, 1, 100);
        MIKAGE_PURSUIT_RAIN_DURATION_TICKS = builder
                .comment("Active duration of Pursuing Phantom Sword Rain; 20 ticks are one second.")
                .defineInRange("pursuitRainDurationTicks", 140, 20, 1200);
        MIKAGE_PURSUIT_RAIN_INTERVAL_TICKS = builder
                .comment("Ticks between locked-position phantom-sword shots.")
                .defineInRange("pursuitRainIntervalTicks", 8, 4, 100);
        MIKAGE_PURSUIT_RAIN_COOLDOWN_TICKS = builder
                .comment("Cooldown after Pursuing Phantom Sword Rain ends.")
                .defineInRange("pursuitRainCooldownTicks", 360, 20, 6000);
        MIKAGE_PURSUIT_RAIN_DAMAGE_MULTIPLIER = builder
                .comment("Damage of each sword-rain impact as a fraction of Mikage's damage.")
                .defineInRange("pursuitRainDamageMultiplier", 0.30D, 0.0D, 10.0D);
        MIKAGE_PURSUIT_RAIN_FINAL_DAMAGE_MULTIPLIER = builder
                .comment("Damage of the unguarded final crimson sword as a fraction of Mikage's damage.")
                .defineInRange("pursuitRainFinalDamageMultiplier", 0.55D, 0.0D, 10.0D);
        MIKAGE_PURSUIT_RAIN_STAGGER_TICKS = builder
                .comment("Shared output-window duration after reflecting the final crimson sword.")
                .defineInRange("pursuitRainStaggerTicks", 60, 10, 400);
        MIKAGE_PURSUIT_RAIN_STAGGER_DAMAGE_MULTIPLIER = builder
                .comment("Damage Mikage receives after the final crimson sword is reflected.")
                .defineInRange("pursuitRainStaggerDamageMultiplier", 1.50D, 1.0D, 10.0D);
        MIKAGE_STEP_IAIDO_OPENING_TICKS = builder
                .comment("Punish window after Step Iaido misses.")
                .defineInRange("stepIaidoOpeningTicks", 15, 1, 200);
        MIKAGE_STEP_IAIDO_OPENING_DAMAGE_MULTIPLIER = builder
                .defineInRange("stepIaidoOpeningDamageMultiplier", 1.20D, 1.0D, 10.0D);
        MIKAGE_DUEL_OPENING_TICKS = builder
                .comment("Shared opening after winning Mirror Reversal by actively striking during the dash.")
                .defineInRange("mirrorDuelOpeningTicks", 40, 1, 400);
        MIKAGE_DUEL_OPENING_DAMAGE_MULTIPLIER = builder
                .defineInRange("mirrorDuelOpeningDamageMultiplier", 1.40D, 1.0D, 10.0D);
        MIKAGE_SEAL_OPENING_TICKS = builder
                .comment("Shared opening after all three Boundary Sever seals are broken.")
                .defineInRange("boundarySealOpeningTicks", 60, 1, 400);
        MIKAGE_SEAL_OPENING_DAMAGE_MULTIPLIER = builder
                .defineInRange("boundarySealOpeningDamageMultiplier", 1.45D, 1.0D, 10.0D);
        MIKAGE_ECHO_OPENING_TICKS = builder
                .comment("Shared opening after finding Moonshadow's true body.")
                .defineInRange("moonEchoOpeningTicks", 40, 1, 400);
        MIKAGE_ECHO_OPENING_DAMAGE_MULTIPLIER = builder
                .defineInRange("moonEchoOpeningDamageMultiplier", 1.35D, 1.0D, 10.0D);
        MIKAGE_SHADOW_CROSS_THRESHOLD = builder
                .comment("Repeated same-direction Shadow-Cross Iaido hits required before Mikage reads the route.")
                .defineInRange("shadowCrossThreshold", 3, 2, 20);
        MIKAGE_SHADOW_CROSS_WINDOW_TICKS = builder
                .comment("Maximum interval between Shadow-Cross Iaido hits that belong to one repeated pattern.")
                .defineInRange("shadowCrossWindowTicks", 120, 20, 1200);
        MIKAGE_ZANSHIN_WARNING_TICKS = builder
                .comment("Warning time before Zanshin: Returning Sakura cuts the predicted exit point.")
                .defineInRange("zanshinWarningTicks", 14, 5, 100);
        MIKAGE_ZANSHIN_DAMAGE_MULTIPLIER = builder
                .comment("Zanshin counter damage as a fraction of Mikage's configured attack damage.")
                .defineInRange("zanshinDamageMultiplier", 0.72D, 0.0D, 10.0D);
        MIKAGE_ADAPTIVE_PLAYER_DAMAGE = builder
                .comment("Learn each participant's actual damage response instead of relying only on armor attributes.")
                .define("adaptivePlayerDamage", true);
        MIKAGE_ADAPTIVE_DAMAGE_MAX_MULTIPLIER = builder
                .comment("Maximum gradual raw-damage correction against unusually effective modded defenses.")
                .defineInRange("adaptiveDamageMaximumMultiplier", 4.0D, 1.0D, 20.0D);
        MIKAGE_BOUNDARY_ASSIST_LOW_HITS = builder
                .comment("Meaningfully connected low-damage hits before Mikage briefly tests boundary/magic damage.")
                .defineInRange("boundaryAssistAfterLowHits", 3, 1, 20);
        MIKAGE_MAX_PLAYER_HEALTH_FRACTION_PER_HIT = builder
                .comment("Absolute safety ceiling for one calibrated hit, before a successful guard reduction.")
                .defineInRange("maximumPlayerHealthFractionPerHit", 0.55D, 0.10D, 1.0D);
        MIKAGE_REPEATED_SA_DAMAGE_MULTIPLIER = builder
                .comment("Damage retained by the second consecutive use of the same locked Slash Art.")
                .defineInRange("repeatedSlashArtDamageMultiplier", 0.70D, 0.0D, 1.0D);
        MIKAGE_MIRROR_COUNTER_DAMAGE_MULTIPLIER = builder
                .comment("Damage retained when Mikage reads and counters the third consecutive identical Slash Art.")
                .defineInRange("mirrorCounterDamageMultiplier", 0.20D, 0.0D, 1.0D);
        MIKAGE_JUDGEMENT_CHAIN_WINDOW_TICKS = builder
                .comment("Maximum interval between Judgement Cuts for Mikage to treat them as a chain.")
                .defineInRange("judgementCutChainWindowTicks", 100, 10, 1200);
        MIKAGE_JUDGEMENT_LOCKOUT_TICKS = builder
                .comment("How long Judgement Cut alone cannot damage Mikage after the third rapid cast.")
                .defineInRange("judgementCutLockoutTicks", 120, 10, 1200);
        MIKAGE_JUDGEMENT_RESET_TICKS = builder
                .comment("Time without another Judgement Cut before Mikage forgets that player's adaptation.")
                .defineInRange("judgementCutResetTicks", 160, 20, 2400);
        MIKAGE_SECOND_JUDGEMENT_DAMAGE_MULTIPLIER = builder
                .comment("Damage retained by the second rapid Judgement Cut. The third is fully negated.")
                .defineInRange("secondJudgementCutDamageMultiplier", 0.50D, 0.0D, 1.0D);
        MIKAGE_HURT_COOLDOWN_TICKS = builder
                .comment("Per-attacker hurt cooldown for Mikage. Limits dense multi-hit attacks without blocking teammates.")
                .defineInRange("hurtCooldownTicks", 6, 0, 40);
        MIKAGE_OPENING_HURT_COOLDOWN_TICKS = builder
                .comment("Shorter per-attacker hurt cooldown during a rewarded stagger/output window.")
                .defineInRange("openingHurtCooldownTicks", 3, 0, 40);
        MIKAGE_PHASE_PROTECTION_TICKS = builder
                .comment("Full protection during phase transitions; queued SlashBlade attacks are cleared.")
                .defineInRange("phaseProtectionTicks", 30, 0, 100);
        MIKAGE_AUTO_DIFFICULTY_SCALING = builder
                .comment("Scale the final trial from the actual participants' attributes, never below the base difficulty.")
                .define("autoDifficultyScaling", true);
        MIKAGE_MAX_EQUIPMENT_HEALTH_SCALE = builder
                .comment("Maximum extra health multiplier contributed by participant offensive power.")
                .defineInRange("maximumEquipmentHealthScale", 1.60D, 1.0D, 10.0D);
        MIKAGE_MAX_DEFENSE_DAMAGE_SCALE = builder
                .comment("Maximum damage multiplier contributed by participant defensive power.")
                .defineInRange("maximumDefenseDamageScale", 1.50D, 1.0D, 10.0D);
        MIKAGE_MAX_SKILL_SPEED_SCALE = builder
                .comment("Maximum skill-frequency multiplier contributed by offensive power.")
                .defineInRange("maximumSkillSpeedScale", 1.25D, 1.0D, 4.0D);
        MIKAGE_MAX_SCORED_WEAPON_DAMAGE = builder
                .comment("Attack values above this are treated as this value; NaN and infinity enter the highest tier safely.")
                .defineInRange("maximumScoredWeaponDamage", 200.0D, 1.0D, 1000000.0D);
        MIKAGE_MAX_SCORED_PLAYER_HEALTH = builder
                .defineInRange("maximumScoredPlayerHealth", 200.0D, 20.0D, 1000000.0D);
        MIKAGE_MAX_SCORED_ARMOR = builder
                .defineInRange("maximumScoredArmor", 80.0D, 0.0D, 1000000.0D);
        MIKAGE_MAX_SCORED_ARMOR_TOUGHNESS = builder
                .defineInRange("maximumScoredArmorToughness", 40.0D, 0.0D, 1000000.0D);
        MIKAGE_ENABLE_PHASE_HEALTH_GATES = builder
                .comment("Prevent a normal DamageSource hit from crossing more than one Mikage phase.")
                .define("enablePhaseHealthGates", true);
        MIRROR_ARENA_BLOCKS_PER_TICK = builder
                .comment("Arena blocks placed per tick while a new challenge is prepared.")
                .defineInRange("arenaBlocksPerTick", 1200, 100, 10000);
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
