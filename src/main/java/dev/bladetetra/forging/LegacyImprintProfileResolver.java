package dev.bladetetra.forging;

import java.util.Objects;
import java.util.function.Function;

/**
 * Side-neutral bridge for optional client geometry resolution.
 * Dedicated servers always use the catalog profile and consider metadata-backed
 * imprints valid. The physical client may install a lazy Wavefront resolver.
 */
public final class LegacyImprintProfileResolver {
    public record Resolution(LegacyCalibrationProfile profile, boolean visualUsable,
            String reason) {
        public Resolution {
            profile = profile == null ? LegacyCalibrationProfile.DEFAULT : profile.normalized();
            reason = reason == null ? "" : reason;
        }
    }

    private static final Function<LegacyImprintKind, Resolution> DEFAULT = kind ->
            new Resolution(kind.rawDefaultProfile(), true, "server metadata");
    private static volatile Function<LegacyImprintKind, Resolution> resolver = DEFAULT;

    public static Resolution resolve(LegacyImprintKind kind) {
        if (kind == null) {
            return new Resolution(LegacyCalibrationProfile.DEFAULT, false, "missing kind");
        }
        try {
            Resolution result = resolver.apply(kind);
            return result == null
                    ? new Resolution(kind.rawDefaultProfile(), false, "resolver returned null")
                    : result;
        } catch (RuntimeException exception) {
            return new Resolution(kind.rawDefaultProfile(), false,
                    exception.getClass().getSimpleName());
        }
    }

    public static void installClientResolver(Function<LegacyImprintKind, Resolution> value) {
        resolver = Objects.requireNonNull(value);
    }

    public static void reset() {
        resolver = DEFAULT;
    }

    private LegacyImprintProfileResolver() {}
}
