package dev.bladetetra.compat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Server-safe controls for the conservative material compatibility fallback.
 */
public final class AutoMaterialConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("automatic_materials");

        ENABLED = builder
                .comment(
                        "Automatically expose non-empty forge:ingots/* tags as",
                        "conservative Tetra metal materials when no material definition exists.")
                .define("enabled", true);

        BLACKLIST = builder
                .comment(
                        "Material names or full tag ids which must not be generated.",
                        "Examples: mythril, forge:ingots/mythril")
                .defineList(
                        "blacklist",
                        List.of(),
                        value -> value instanceof String);

        builder.pop();
        SPEC = builder.build();
    }

    private AutoMaterialConfig() {
    }
}
