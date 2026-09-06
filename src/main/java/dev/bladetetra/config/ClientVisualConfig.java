package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-owned controls for material and soul visual effects. */
public final class ClientVisualConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EMISSIVE_TEXTURES;
    public static final ForgeConfigSpec.DoubleValue EMISSIVE_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue SOUL_GLOW_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_IAIDO_IMPACT_FEEDBACK;
    public static final ForgeConfigSpec.DoubleValue IAIDO_CAMERA_IMPACT_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue IAIDO_FLASH_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MIKAGE_MUSIC;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_MUSIC_VOLUME;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUDIO_PACK_REMINDER;
    public static final ForgeConfigSpec.BooleanValue AUDIO_PACK_REMINDER_DISMISSED;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MIKAGE_BOUNDARY;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BOUNDARY_BRIGHTNESS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MIKAGE_BLOOD_MOON_DOMAIN;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BLOOD_MOON_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AKATSUKI_EXECUTION_TINT;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_EXECUTION_TINT_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MIKAGE_BOSS_BAR;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BOSS_BAR_SCALE;
    public static final ForgeConfigSpec.DoubleValue MIKAGE_BOSS_BAR_OPACITY;
    public static final ForgeConfigSpec.BooleanValue SHOW_MIKAGE_TECHNIQUE_BAR;
    public static final ForgeConfigSpec.BooleanValue REDUCE_MIKAGE_HUD_MOTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_VFX;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_VFX_INTENSITY;
    public static final ForgeConfigSpec.IntValue BLADE_COMBAT_VFX_QUALITY;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_VFX_DISTANCE;
    public static final ForgeConfigSpec.IntValue BLADE_COMBAT_VFX_MAX_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_HIGHLIGHTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_CAMERA_IMPACT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PARRY_CAMERA_IMPACT;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_CAMERA_INTENSITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("visuals");
        ENABLE_EMISSIVE_TEXTURES = builder
                .comment(
                        "Render a full-bright overlay for luminous materials and awakened soul inscriptions.",
                        "Disable this to keep only the ordinary material-aware texture pass.")
                .define("enableEmissiveTextures", true);
        EMISSIVE_INTENSITY = builder
                .comment("Opacity multiplier for material emission; newly rendered combinations use the new value immediately.")
                .defineInRange("emissiveIntensity", 0.85D, 0.0D, 1.0D);
        SOUL_GLOW_INTENSITY = builder
                .comment("Opacity multiplier for awakened soul-inscription veins and mirror lines.")
                .defineInRange("soulGlowIntensity", 0.90D, 0.0D, 1.0D);
        ENABLE_IAIDO_IMPACT_FEEDBACK = builder
                .comment(
                        "Enable the brief camera impulse and impact flash for prepared Iaido hits.",
                        "World particles and sounds are unaffected by this client-only accessibility option.")
                .define("enableIaidoImpactFeedback", true);
        IAIDO_CAMERA_IMPACT_INTENSITY = builder
                .comment("Strength of the prepared-Iaido camera impulse; 0 disables camera movement.")
                .defineInRange("iaidoCameraImpactIntensity", 0.65D, 0.0D, 1.0D);
        IAIDO_FLASH_INTENSITY = builder
                .comment("Opacity of the prepared-Iaido impact flash; 0 disables the flash.")
                .defineInRange("iaidoFlashIntensity", 0.55D, 0.0D, 1.0D);
        builder.pop();

        builder.push("audio");
        ENABLE_MIKAGE_MUSIC = builder
                .comment(
                        "Play Mikage's optional battle track when an audio resource pack provides it.",
                        "The music follows Minecraft's Jukebox/Note Blocks volume slider; missing audio stays silent.")
                .define("enableMikageMusic", true);
        MIKAGE_MUSIC_VOLUME = builder
                .comment("Volume multiplier for Mikage's battle music.")
                .defineInRange("mikageMusicVolume", 0.78D, 0.0D, 1.0D);
        ENABLE_AUDIO_PACK_REMINDER = builder
                .comment(
                        "Check for the optional Forged Echoes music and voice pack after joining a world.",
                        "The reminder is client-only, non-blocking and appears at most once per game session.")
                .define("enableForgedEchoesReminder", true);
        AUDIO_PACK_REMINDER_DISMISSED = builder
                .comment("Set by the in-game 'do not remind me again' action.")
                .define("forgedEchoesReminderDismissed", false);
        builder.pop();

        builder.push("bossEffects");
        ENABLE_MIKAGE_BOUNDARY = builder
                .comment(
                        "Render Mikage's geometric mirror-realm boundary.",
                        "This effect uses client-side geometry and does not spawn particles.")
                .define("enableMikageBoundary", true);
        MIKAGE_BOUNDARY_BRIGHTNESS = builder
                .comment("Brightness multiplier for the boundary seals, torii anchors and mirror walls.")
                .defineInRange("mikageBoundaryBrightness", 1.0D, 0.0D, 1.5D);
        ENABLE_MIKAGE_BLOOD_MOON_DOMAIN = builder
                .comment(
                        "Apply the warm crimson world grading during Mikage's third phase.",
                        "Disable this if a shader pack conflicts with Minecraft post effects; world-space combat effects remain available.")
                .define("enableMikageBloodMoonDomain", true);
        MIKAGE_BLOOD_MOON_INTENSITY = builder
                .comment("Strength of Mikage's third-phase crimson-orange world grading.")
                .defineInRange("mikageBloodMoonIntensity", 0.82D, 0.0D, 1.0D);
        ENABLE_AKATSUKI_EXECUTION_TINT = builder
                .comment(
                        "Apply a local crimson-orange world grade while your awakened Akatsuki executes a target.",
                        "This does not change time, weather, dimensions, or another player's view.")
                .define("enableAkatsukiExecutionTint", true);
        AKATSUKI_EXECUTION_TINT_INTENSITY = builder
                .comment("Strength of Akatsuki's Final Moon world grading.")
                .defineInRange("akatsukiExecutionTintIntensity", 0.72D, 0.0D, 1.0D);
        ENABLE_MIKAGE_BOSS_BAR = builder
                .comment(
                        "Replace only Mikage's vanilla boss bar with the torii-themed HUD.",
                        "Disable this for compatibility with boss-bar overhaul mods.")
                .define("enableMikageBossBar", true);
        MIKAGE_BOSS_BAR_SCALE = builder
                .comment("Scale of Mikage's custom boss bar.")
                .defineInRange("mikageBossBarScale", 1.0D, 0.65D, 1.35D);
        MIKAGE_BOSS_BAR_OPACITY = builder
                .comment("Opacity of Mikage's custom boss bar.")
                .defineInRange("mikageBossBarOpacity", 0.94D, 0.25D, 1.0D);
        SHOW_MIKAGE_TECHNIQUE_BAR = builder
                .comment("Show the cast/progress bar for Mikage's signature techniques.")
                .define("showMikageTechniqueBar", true);
        REDUCE_MIKAGE_HUD_MOTION = builder
                .comment("Disable flowing highlights, impact shake and other HUD motion.")
                .define("reduceMikageHudMotion", false);
        ENABLE_BLADE_COMBAT_VFX = builder
                .comment(
                        "Render shader-light blade clashes, guard breaks and phase transitions.",
                        "These effects use Minecraft's built-in rendering pipeline for broad compatibility.")
                .define("enableBladeCombatVfx", true);
        BLADE_COMBAT_VFX_INTENSITY = builder
                .comment("Brightness and size multiplier for blade combat effects.")
                .defineInRange("bladeCombatVfxIntensity", 1.0D, 0.0D, 1.5D);
        BLADE_COMBAT_VFX_QUALITY = builder
                .comment(
                        "Detail level for tracked blade trails and technique geometry.",
                        "0 = low, 1 = medium, 2 = high. Core telegraphs are always retained.")
                .defineInRange("bladeCombatVfxQuality", 1, 0, 2);
        BLADE_COMBAT_VFX_DISTANCE = builder
                .comment("Maximum distance at which a client creates blade technique effects.")
                .defineInRange("bladeCombatVfxDistance", 128.0D, 24.0D, 192.0D);
        BLADE_COMBAT_VFX_MAX_EFFECTS = builder
                .comment(
                        "Maximum number of simultaneous blade-combat effect timelines.",
                        "When the limit is reached, optional trails are discarded before core telegraphs.")
                .defineInRange("bladeCombatVfxMaxEffects", 96, 24, 256);
        ENABLE_BLADE_COMBAT_HIGHLIGHTS = builder
                .comment("Enable the brightest white confirmation flashes in blade-combat effects.")
                .define("enableBladeCombatHighlights", true);
        ENABLE_BLADE_COMBAT_CAMERA_IMPACT = builder
                .comment(
                        "Enable subtle camera impulses for major technique results.",
                        "First-person impulses are deliberately weaker than third-person impulses.")
                .define("enableBladeCombatCameraImpact", true);
        ENABLE_PARRY_CAMERA_IMPACT = builder
                .comment("Apply a very brief camera impulse when your blade is involved in a parry.")
                .define("enableParryCameraImpact", true);
        BLADE_COMBAT_CAMERA_INTENSITY = builder
                .comment("Strength multiplier for optional blade-combat camera impulses.")
                .defineInRange("bladeCombatCameraIntensity", 1.0D, 0.0D, 1.5D);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientVisualConfig() {
    }
}
