package dev.bladetetra.combat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Per-source, per-returned-combo audit; registry presence alone is not FULL support. */
public record ProgrammaticFusionPresentation(Map<String, FusionHandoff.Route> routes) {
    public static final ProgrammaticFusionPresentation NONE =
            new ProgrammaticFusionPresentation(Map.of());

    public ProgrammaticFusionPresentation {
        routes = Map.copyOf(routes);
    }

    public static ProgrammaticFusionPresentation audited(FusionHandoff.Route... routes) {
        return new ProgrammaticFusionPresentation(List.of(routes).stream().collect(
                Collectors.toUnmodifiableMap(route -> route.stages().get(0).combo(), route -> route)));
    }

    public boolean delegateRelease() { return !routes.isEmpty(); }
    public boolean delegateResponse() { return !routes.isEmpty(); }

    public FusionHandoff.Route route(String combo) { return routes.get(combo); }
}
