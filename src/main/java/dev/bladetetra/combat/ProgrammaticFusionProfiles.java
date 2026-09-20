package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyImprintKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves named-blade abilities into conservative, composable semantics. */
public final class ProgrammaticFusionProfiles {
    private static final Map<ResourceLocation, ProgrammaticFusionProfile> EXPLICIT =
            new ConcurrentHashMap<>();

    static {
        register(new ResourceLocation("slashblade", "piercing"),
                new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DASH,
                        ProgrammaticFusionProfile.Response.FOCUSED_DRIVE, 0.90D));
        register(new ResourceLocation("slashblade", "sakura_end"),
                new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.ARC,
                        ProgrammaticFusionProfile.Response.CROSS_DRIVE, 0.85D));
    }

    /**
     * Extension seam for compatibility modules. Registration describes an ability;
     * it does not define any ordered A+B pair and therefore does not reintroduce a
     * quadratic fusion catalog.
     */
    public static void register(ResourceLocation ability,
            ProgrammaticFusionProfile profile) {
        if (ability == null || profile == null) {
            throw new IllegalArgumentException("Programmatic fusion profile requires id and profile");
        }
        EXPLICIT.put(ability, profile);
    }

    static ProgrammaticFusionProfile resolve(LegacyImprintKind kind) {
        if (kind == null) {
            return ProgrammaticFusionProfile.SAFE;
        }
        ResourceLocation semantic = kind.slashArt();
        if (semantic == null && !kind.specialEffects().isEmpty()) {
            semantic = kind.specialEffects().get(0);
        }
        if (semantic == null) {
            semantic = kind.name();
        }
        return resolve(semantic);
    }

    static ProgrammaticFusionProfile resolve(ResourceLocation ability) {
        if (ability == null) {
            return ProgrammaticFusionProfile.SAFE;
        }
        ProgrammaticFusionProfile explicit = EXPLICIT.get(ability);
        if (explicit != null) {
            return explicit;
        }

        String path = ability.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "pierc", "thrust", "stab", "drive")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DASH,
                    ProgrammaticFusionProfile.Response.FOCUSED_DRIVE, 0.80D);
        }
        if (containsAny(path, "sakura", "circle", "round", "moon", "wheel")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.ARC,
                    ProgrammaticFusionProfile.Response.CROSS_DRIVE, 0.76D);
        }
        if (containsAny(path, "rain", "judg", "summon", "storm", "barrage")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.FAN_DRIVE, 0.72D);
        }
        if (containsAny(path, "cross", "twin", "double", "dual")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.CROSS_DRIVE, 0.70D);
        }
        return ProgrammaticFusionProfile.SAFE;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private ProgrammaticFusionProfiles() {
    }
}
