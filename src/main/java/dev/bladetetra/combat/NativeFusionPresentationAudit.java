package dev.bladetetra.combat;

import java.util.ArrayList;
import java.util.List;

/** SlashBlade 1.9.63 lifecycle audit. See FUSION_PRESENTATION_AUDIT.md. */
public final class NativeFusionPresentationAudit {
    private static final FusionHandoff.Route SUPER = route(
            stage("judgement_cut_end", 1923, 1928, 1F, 2));

    public static ProgrammaticFusionPresentation presentation(String art) {
        List<FusionHandoff.Route> routes = new ArrayList<>();
        switch (art) {
            case "judgement_cut" -> {
                routes.add(route(stage("judgement_cut", 1900, 1923, 1F, -1),
                        stage("judgement_cut_slash", 1923, 1928, .4F, 2)));
                routes.add(route(stage("judgement_cut_slash_air", 1923, 1928, .5F, 2)));
                routes.add(route(stage("judgement_cut_slash_just", 1923, 1928, 1F, 3)));
            }
            case "sakura_end" -> {
                routes.add(route(stage("sakura_end_left", 1816, 1859, 6F, -1),
                        stage("sakura_end_right", 204, 218, 1.1F, 2)));
                routes.add(route(stage("sakura_end_left_air", 1300, 1328, 3.2F, -1),
                        stage("sakura_end_right_air", 1200, 1210, 1F, 2)));
            }
            case "void_slash" -> routes.add(route(stage("void_slash", 2200, 2277, 1F, 23)));
            case "circle_slash" -> routes.add(route(stage("circle_slash", 725, 743, 1F, 11)));
            case "drive_horizontal" -> routes.add(route(stage("drive_horizontal", 400, 459, 1F, 5)));
            case "drive_vertical" -> routes.add(route(stage("drive_vertical", 1600, 1659, 1F, 5)));
            case "wave_edge" -> routes.add(route(stage("wave_edge_vertical", 1600, 1659, 1F, 5)));
            case "piercing" -> {
                routes.add(route(stage("piercing", 1, 33, 1F, -1),
                        stage("piercing_2", 33, 55, 1F, 4)));
                routes.add(route(stage("piercing_just", 34, 55, 1F, 4)));
            }
            default -> { return ProgrammaticFusionPresentation.NONE; }
        }
        routes.add(SUPER);
        return ProgrammaticFusionPresentation.audited(routes.toArray(FusionHandoff.Route[]::new));
    }

    /** Audited native semantic release; used even when the original add-on failed. */
    static FusionHandoff.Route semanticRoute(String combo) {
        for (String art : List.of("judgement_cut", "sakura_end", "void_slash", "circle_slash",
                "drive_horizontal", "drive_vertical", "wave_edge", "piercing")) {
            FusionHandoff.Route route = presentation(art).route(combo);
            if (route != null) return route;
        }
        return null;
    }

    public static FusionHandoff.Route superRoute() { return SUPER; }

    public static FusionHandoff.Route route(FusionHandoff.Stage... stages) {
        return new FusionHandoff.Route(List.of(stages), 100);
    }

    private static FusionHandoff.Stage stage(String combo, int start, int end, float speed, int safe) {
        return new FusionHandoff.Stage("slashblade:" + combo, start, end, speed, 0, safe);
    }

    private NativeFusionPresentationAudit() { }
}
