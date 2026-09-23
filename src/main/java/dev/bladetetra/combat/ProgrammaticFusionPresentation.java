package dev.bladetetra.combat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-source presentation policy.
 *
 * <p>Audited routes keep exact lifecycle metadata. Known third-party Slash Arts
 * without a matching exact audit use a short soft-overlap window: source A is
 * allowed to run for a few ticks so its signature effects can spawn, then source
 * B is allowed to take over the single player ComboState. Unknown abilities
 * remain NONE.</p>
 */
public record ProgrammaticFusionPresentation(
        Map<String, FusionHandoff.Route> routes,
        boolean softOverlap) {
    public static final ProgrammaticFusionPresentation NONE =
            new ProgrammaticFusionPresentation(Map.of(), false);
    public static final ProgrammaticFusionPresentation SOFT_OVERLAP =
            new ProgrammaticFusionPresentation(Map.of(), true);

    public ProgrammaticFusionPresentation {
        routes = Map.copyOf(routes);
    }

    /** Compatibility constructor for exact route-only callers. */
    public ProgrammaticFusionPresentation(Map<String, FusionHandoff.Route> routes) {
        this(routes, false);
    }

    public static ProgrammaticFusionPresentation audited(FusionHandoff.Route... routes) {
        return audited(false, routes);
    }

    public static ProgrammaticFusionPresentation auditedSoftOverlap(FusionHandoff.Route... routes) {
        return audited(true, routes);
    }

    private static ProgrammaticFusionPresentation audited(boolean softOverlap,
            FusionHandoff.Route... routes) {
        return new ProgrammaticFusionPresentation(List.of(routes).stream().collect(
                Collectors.toUnmodifiableMap(route -> route.stages().get(0).combo(), route -> route)),
                softOverlap);
    }

    public static ProgrammaticFusionPresentation softOverlap() {
        return SOFT_OVERLAP;
    }

    public boolean delegateRelease() {
        return softOverlap || !routes.isEmpty();
    }

    public boolean delegateResponse() {
        return softOverlap || !routes.isEmpty();
    }

    public boolean isAudited() {
        return !routes.isEmpty();
    }

    public FusionHandoff.Route route(String combo) {
        return routes.get(combo);
    }
}
