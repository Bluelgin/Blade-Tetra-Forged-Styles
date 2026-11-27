package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-owned controls for material and soul visual effects. */
public final class ClientVisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ANIMATE_SOUL_FRAGMENT_TITLES;
    public static final ForgeConfigSpec.BooleanValue ANIMATE_SOUL_FRAGMENT_TEXT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EMISSIVE_TEXTURES;
    public static final ForgeConfigSpec.DoubleValue EMISSIVE_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue SOUL_GLOW_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_IAIDO_IMPACT_FEEDBACK;
    public static final ForgeConfigSpec.DoubleValue IAIDO_CAMERA_IMPACT_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue IAIDO_FLASH_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VOID_SCATTERING_SHADER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEAD_THOUGHT_GRADING;
    public static final ForgeConfigSpec.DoubleValue VOID_SCATTERING_SHADER_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AKATSUKI_EXECUTION_TINT;
    public static final ForgeConfigSpec.DoubleValue AKATSUKI_EXECUTION_TINT_INTENSITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_VFX;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_VFX_INTENSITY;
    public static final ForgeConfigSpec.IntValue BLADE_COMBAT_VFX_QUALITY;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_VFX_DISTANCE;
    public static final ForgeConfigSpec.IntValue BLADE_COMBAT_VFX_MAX_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_HIGHLIGHTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLADE_COMBAT_CAMERA_IMPACT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PARRY_CAMERA_IMPACT;
    public static final ForgeConfigSpec.DoubleValue BLADE_COMBAT_CAMERA_INTENSITY;
    public static final ForgeConfigSpec.IntValue SWORD_AURA_DETAIL;
    public static final ForgeConfigSpec.DoubleValue SWORD_AURA_DECORATION_DISTANCE;
    public static final ForgeConfigSpec.IntValue SWORD_AURA_MAX_DECORATIONS;
    public static final ForgeConfigSpec.BooleanValue SHOW_SOUL_SWITCH_HUD;
    public static final ForgeConfigSpec.BooleanValue AUTO_HIDE_SOUL_SWITCH_HUD;
    public static final ForgeConfigSpec.BooleanValue SHOW_SOUL_SWITCH_KEY;
    public static final ForgeConfigSpec.BooleanValue REDUCE_SOUL_SWITCH_MOTION;
    public static final ForgeConfigSpec.IntValue SOUL_SWITCH_ICON_SIZE;
    public static final ForgeConfigSpec.IntValue SOUL_SWITCH_HUD_X;
    public static final ForgeConfigSpec.IntValue SOUL_SWITCH_HUD_Y;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("visuals");
        ANIMATE_SOUL_FRAGMENT_TITLES = builder.comment("Subtle engraved-title wave and moving glint on soul fragment guides. Body text stays still.")
                .define("animateSoulFragmentTitles", true);
        ANIMATE_SOUL_FRAGMENT_TEXT = builder.comment("Animate only emphasized guide words; controls, numbers and restrictions remain still.")
                .define("animateSoulFragmentText", true);
        SWORD_AURA_DETAIL = builder
                .comment("Lifeng sword aura detail: 0 = main layer only, 1 = balanced, 2 = full.",
                        "Balanced halves the decoration distance and per-frame budget. Damage is unchanged.")
                .translation("config.blade_tetra.visuals.swordAuraDetail")
                .defineInRange("swordAuraDetail", 2, 0, 2);
        SWORD_AURA_DECORATION_DISTANCE = builder
                .comment("Maximum camera distance for optional crossed/heavy aura layers; main projectiles remain visible.")
                .translation("config.blade_tetra.visuals.swordAuraDecorationDistance")
                .defineInRange("swordAuraDecorationDistance", 48.0D, 8.0D, 128.0D);
        SWORD_AURA_MAX_DECORATIONS = builder
                .comment("Maximum optional aura layers per rendered frame; never limits attacks or damage.")
                .translation("config.blade_tetra.visuals.swordAuraMaxDecorations")
                .defineInRange("swordAuraMaxDecorations", 32, 0, 128);
        ENABLE_DEAD_THOUGHT_GRADING = builder
                .comment("Brief, caster-only desaturation for Dead Thought. Off in low VFX quality.",
                        "Disable for shader-pack compatibility; all 3D effects remain available.")
                .define("enableDeadThoughtGrading", true);
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
        ENABLE_VOID_SCATTERING_SHADER = builder
                .comment(
                        "Use the local procedural rift shader for Void Scattering and Residual Bloom.",
                        "Disable this if a shader pack or graphics mod conflicts; the geometry fallback remains available.")
                .define("enableVoidScatteringShader", true);
        VOID_SCATTERING_SHADER_INTENSITY = builder
                .comment("Brightness/opacity multiplier for the local Void Scattering rift shader.")
                .defineInRange("voidScatteringShaderIntensity", 1.0D, 0.0D, 1.5D);
        builder.pop();

        builder.push("bossEffects");
        ENABLE_AKATSUKI_EXECUTION_TINT = builder
                .comment(
                        "Apply a local crimson-orange world grade while your awakened Akatsuki executes a target.",
                        "This does not change time, weather, dimensions, or another player's view.")
                .define("enableAkatsukiExecutionTint", true);
        AKATSUKI_EXECUTION_TINT_INTENSITY = builder
                .comment("Strength of Akatsuki's Final Moon world grading.")
                .defineInRange("akatsukiExecutionTintIntensity", 0.72D, 0.0D, 1.0D);
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

        builder.push("soulSwitchHud");
        SHOW_SOUL_SWITCH_HUD = builder.define("enabled", true);
        AUTO_HIDE_SOUL_SWITCH_HUD = builder.comment("Show soul seals only for confirmed skills or a short soul-switch preview.")
                .define("autoHide", true);
        SHOW_SOUL_SWITCH_KEY = builder.define("showKey", true);
        REDUCE_SOUL_SWITCH_MOTION = builder.define("reduceMotion", false);
        SOUL_SWITCH_ICON_SIZE = builder.defineInRange("iconSize", 22, 16, 32);
        SOUL_SWITCH_HUD_X = builder.comment("Horizontal offset from the bottom-center soul seals.")
                .defineInRange("offsetX", 0, -1000, 1000);
        SOUL_SWITCH_HUD_Y = builder.comment("Positive values move soul seals upward, away from other mod HUDs.")
                .defineInRange("offsetY", 0, -200, 400);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientVisualConfig() {
    }
}
