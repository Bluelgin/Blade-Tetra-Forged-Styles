package dev.bladetetra.forging;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A data-driven named blade; stable IDs retain old fox calibration saves. */
public record LegacyImprintKind(String id, ResourceLocation name,
        ResourceLocation model, ResourceLocation texture, String material,
        LegacyCalibrationProfile defaultProfile, double baseAttack,
        int maxDamage, ResourceLocation slashArt,
        List<ResourceLocation> specialEffects) {
    public LegacyImprintKind {
        specialEffects = specialEffects == null ? List.of() : List.copyOf(specialEffects);
        defaultProfile = defaultProfile == null
                ? LegacyCalibrationProfile.DEFAULT : defaultProfile.normalized();
    }

    public static final LegacyImprintKind BLACK_FOX = fox("black");
    public static final LegacyImprintKind WHITE_FOX = fox("white");

    private static LegacyImprintKind fox(String color) {
        return new LegacyImprintKind("fox_" + color,
                new ResourceLocation("slashblade", "fox_" + color),
                new ResourceLocation("slashblade", "model/named/sange/sange.obj"),
                new ResourceLocation("slashblade", "model/named/sange/" + color + ".png"),
                "slashblade:proudsoul_crystal", LegacyCalibrationProfile.DEFAULT,
                5.0D, 70,
                color.equals("black") ? new ResourceLocation("slashblade", "piercing") : null,
                List.of());
    }

    /**
     * Optional client presentation profile. Gameplay and persistence must use the
     * record's {@link #defaultProfile()} accessor instead, which is pure metadata.
     */
    public LegacyCalibrationProfile visualProfile() {
        return LegacyImprintProfileResolver.resolve(this).profile();
    }

    /**
     * Transitional source-compatible alias for the pre-split API. New gameplay code
     * should use {@link #defaultProfile()}; client presentation code uses
     * {@link #visualProfile()}.
     */
    @Deprecated
    public LegacyCalibrationProfile rawDefaultProfile() {
        return defaultProfile;
    }

    /** Whether this client can safely partition and render the provider model. */
    public boolean visualUsable() {
        return LegacyImprintProfileResolver.resolve(this).visualUsable();
    }

    public String visualFailureReason() {
        return LegacyImprintProfileResolver.resolve(this).reason();
    }

    public String translationKey() {
        return "item." + name.getNamespace() + "." + name.getPath();
    }

    public String schematic(String part) {
        return "slashblade/legacy_auto/" + id + "/" + part;
    }

    public String improvement() {
        return "blade_tetra/legacy_auto/" + id;
    }

    public boolean supportsOrthodoxInheritance() {
        return slashArt != null || !specialEffects.isEmpty();
    }

    public static LegacyImprintKind fromTranslationKey(String key) {
        if (key == null) return null;
        return NamedLegacyCatalog.values().stream().filter(value ->
                key.equals(value.translationKey()) || key.equals(value.name.toString())
                        || key.equals(value.name.getNamespace() + "." + value.name.getPath()))
                .findFirst().orElse(null);
    }
}
