package dev.bladetetra.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-owned controls for material and soul visual effects. */
public final class ClientVisualConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_EMISSIVE_TEXTURES;
    public static final ForgeConfigSpec.DoubleValue EMISSIVE_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue SOUL_GLOW_INTENSITY;

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
        builder.pop();

        SPEC = builder.build();
    }

    private ClientVisualConfig() {
    }
}
