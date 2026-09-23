package dev.bladetetra.combat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Per-source presentation policy.
 *
 * <p>Audited routes keep exact lifecycle metadata. Known third-party Slash Arts
 * without an exact audit may still execute through dynamic observation: the
 * runtime watches the real ComboState clock and only hands off after the source
 * naturally reaches a neutral state. Unknown abilities remain NONE.</p>
 */
public record ProgrammaticFusionPresentation(
        Map<String, FusionHandoff.Route> routes,
        boolean dynamicObservation) {
    public static final ProgrammaticFusionPresentation NONE =
            new ProgrammaticFusionPresentation(Map.of(), false);
    public static final ProgrammaticFusionPresentation DYNAMIC =
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

    public static ProgrammaticFusionPresentation auditedDynamic(FusionHandoff.Route... routes) {
        return audited(true, routes);
    }

    private static ProgrammaticFusionPresentation audited(boolean dynamicObservation,
            FusionHandoff.Route... routes) {
        return new ProgrammaticFusionPresentation(List.of(routes).stream().collect(
                Collectors.toUnmodifiableMap(route -> route.stages().get(0).combo(), route -> route)),
                dynamicObservation);
    }

    public static ProgrammaticFusionPresentation dynamic() {
        return DYNAMIC;
    }

    public boolean delegateRelease() {
        return dynamicObservation || !routes.isEmpty();
    }

    public boolean delegateResponse() {
        return dynamicObservation || !routes.isEmpty();
    }

    public boolean isAudited() {
        return !routes.isEmpty();
    }

    public FusionHandoff.Route route(String combo) {
        return routes.get(combo);
    }
}
