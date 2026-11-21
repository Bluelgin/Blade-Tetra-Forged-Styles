package dev.bladetetra.combat;

import net.minecraft.resources.ResourceLocation;

/** Source-specific conditions confirmed in add-on code. The native timeout chain still owns recovery. */
final class NativeFusionLifecycle {
    static int requiredFrame(ResourceLocation art, ResourceLocation combo) {
        String namespace = art.getNamespace();
        if (!namespace.equals(combo.getNamespace())) return 0;

        return switch (namespace) {
            case "slashblade_addon" -> art.getPath().equals(combo.getPath())
                    ? switch (art.getPath()) {
                        case "spiral_edge", "fire_spiral" -> 7;
                        case "rapid_blistering_swords", "gale_swords", "water_drive", "lighting_swords" -> 3;
                        default -> 0;
                    } : 0;
            case "last_smith" -> switch (combo.getPath()) {
                case "iai_cross_slash" -> 6;
                case "sakura_blistering_swords", "odachi_combo_a1" -> 3;
                default -> 0;
            };
            case "recasting" -> art.equals(combo) && (art.getPath().equals("soul_sever")
                    || art.getPath().equals("soul_sever_lambda")) ? 16 : 0;
            default -> 0;
        };
    }

    private NativeFusionLifecycle() {}
}
