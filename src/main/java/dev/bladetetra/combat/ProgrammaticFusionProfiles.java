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
        // SlashBlade: Resharped 1.20.1 native SA dictionary. These are semantic
        // descriptions, not pair recipes, so N native arts still produce N*(N-1)
        // ordered mixed combinations without a quadratic catalog.
        nativeArt("judgement_cut",
                ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 1.00D);
        nativeArt("sakura_end",
                ProgrammaticFusionProfile.Entry.SAKURA_END,
                ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.90D);
        nativeArt("void_slash",
                ProgrammaticFusionProfile.Entry.VOID_SLASH,
                ProgrammaticFusionProfile.Response.VOID_TRIDENT, 0.95D);
        nativeArt("circle_slash",
                ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                ProgrammaticFusionProfile.Response.CIRCLE_RING, 0.86D);
        nativeArt("drive_vertical",
                ProgrammaticFusionProfile.Entry.DRIVE_VERTICAL,
                ProgrammaticFusionProfile.Response.VERTICAL_DRIVE, 0.82D);
        nativeArt("drive_horizontal",
                ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.82D);
        nativeArt("wave_edge",
                ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.84D);
        nativeArt("piercing",
                ProgrammaticFusionProfile.Entry.PIERCING,
                ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 0.90D);
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

        // Third-party fallback stays intentionally conservative. Compatibility
        // modules can register an exact profile without defining pair-specific code.
        String path = ability.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "pierc", "thrust", "stab")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 0.78D);
        }
        if (containsAny(path, "sakura", "circle", "round", "moon", "wheel")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.74D);
        }
        if (containsAny(path, "judg", "dimension", "space", "teleport")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0.72D);
        }
        if (containsAny(path, "void", "nihil", "abyss")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.VOID_TRIDENT, 0.72D);
        }
        if (containsAny(path, "rain", "summon", "storm", "barrage", "wave")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.70D);
        }
        if (containsAny(path, "cross", "twin", "double", "dual")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.70D);
        }
        if (containsAny(path, "drive", "beam", "projectile")) {
            return new ProgrammaticFusionProfile(ProgrammaticFusionProfile.Entry.DIRECT,
                    ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.68D);
        }
        return ProgrammaticFusionProfile.SAFE;
    }

    private static void nativeArt(String path, ProgrammaticFusionProfile.Entry entry,
            ProgrammaticFusionProfile.Response response, double intensity) {
        register(new ResourceLocation("slashblade", path),
                new ProgrammaticFusionProfile(entry, response, intensity));
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
