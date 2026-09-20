package dev.bladetetra.combat;

import dev.bladetetra.forging.LegacyImprintKind;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves named-blade abilities into conservative, composable semantics. Exact
 * presentation delegation is a separate allow-listed layer; this registry always
 * remains usable as the fallback when source execution is unavailable.
 */
public final class ProgrammaticFusionProfiles {
    private static final Map<ResourceLocation, ProgrammaticFusionProfile> EXPLICIT =
            new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> SOURCE_PRESENTATION =
            ConcurrentHashMap.newKeySet();

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

    /** Semantic-only registration seam. */
    public static void register(ResourceLocation ability,
            ProgrammaticFusionProfile profile) {
        if (ability == null || profile == null) {
            throw new IllegalArgumentException(
                    "Programmatic fusion profile requires id and profile");
        }
        EXPLICIT.put(ability, profile);
    }

    /**
     * Registers an audited optional ability whose own SlashArts/ComboState chain is
     * safe to delegate to when the owning add-on is loaded. No optional Java type is
     * referenced here; delegation is resolved through SlashBlade's shared registry.
     */
    public static void registerSourcePresentation(ResourceLocation ability,
            ProgrammaticFusionProfile profile) {
        register(ability, profile);
        SOURCE_PRESENTATION.add(ability);
    }

    static boolean supportsSourcePresentation(ResourceLocation ability) {
        return ability != null && SOURCE_PRESENTATION.contains(ability);
    }

    /** Stable semantic source identity used by both planning and presentation. */
    static ResourceLocation abilityOf(LegacyImprintKind kind) {
        if (kind == null) {
            return null;
        }
        if (kind.slashArt() != null) {
            return kind.slashArt();
        }
        if (!kind.specialEffects().isEmpty()) {
            return kind.specialEffects().get(0);
        }
        return kind.name();
    }

    static ProgrammaticFusionProfile resolve(LegacyImprintKind kind) {
        return resolve(abilityOf(kind));
    }

    static ProgrammaticFusionProfile resolve(ResourceLocation ability) {
        if (ability == null) {
            return ProgrammaticFusionProfile.SAFE;
        }
        ProgrammaticFusionProfile explicit = EXPLICIT.get(ability);
        if (explicit != null) {
            return explicit;
        }

        // Unknown add-ons are resolved lazily only when one of their abilities is
        // actually present on a discovered named blade. Keep this vocabulary
        // deliberately generic and bounded; popular add-ons use exact dictionaries.
        String path = ability.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "pierc", "thrust", "stab", "lunge")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.PIERCING,
                    ProgrammaticFusionProfile.Response.PIERCING_FOCUS, 0.76D);
        }
        if (containsAny(path, "sakura", "bloom", "flower", "cross", "twin",
                "double", "dual")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.SAKURA_END,
                    ProgrammaticFusionProfile.Response.SAKURA_CROSS, 0.72D);
        }
        if (containsAny(path, "circle", "round", "moon", "wheel", "spiral",
                "ring", "matrix")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.CIRCLE_SLASH,
                    ProgrammaticFusionProfile.Response.CIRCLE_RING, 0.72D);
        }
        if (containsAny(path, "judg", "dimension", "space", "teleport",
                "lightning", "thunder", "verdict")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.JUDGEMENT_CUT,
                    ProgrammaticFusionProfile.Response.JUDGEMENT_ECHO, 0.72D);
        }
        if (containsAny(path, "void", "nihil", "abyss", "wither", "soul",
                "black_hole", "supernova")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.VOID_SLASH,
                    ProgrammaticFusionProfile.Response.VOID_TRIDENT, 0.72D);
        }
        if (containsAny(path, "rain", "summon", "storm", "barrage", "wave",
                "phantom", "star", "gale", "wind", "sword_rain")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.WAVE_EDGE,
                    ProgrammaticFusionProfile.Response.WAVE_EDGE, 0.70D);
        }
        if (containsAny(path, "drive", "beam", "projectile", "laser",
                "shot", "boost")) {
            return new ProgrammaticFusionProfile(
                    ProgrammaticFusionProfile.Entry.DRIVE_HORIZONTAL,
                    ProgrammaticFusionProfile.Response.HORIZONTAL_DRIVE, 0.68D);
        }
        return ProgrammaticFusionProfile.SAFE;
    }

    private static void nativeArt(String path,
            ProgrammaticFusionProfile.Entry entry,
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
