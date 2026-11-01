package dev.bladetetra.easteregg;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import static dev.bladetetra.easteregg.SoulLegacyState.Legacy;

/** Explicit ordinary-action families. Shared movement and Slash Arts grant no affinity. */
public final class SoulActionAffinity {
    private static final Set<String> IAIDO = Set.of("iaido_draw", "iaido_follow", "iaido_return", "iaido_finish");
    private static final Set<String> RENGEKI = Set.of("combo_b1", "combo_b2", "combo_b3", "combo_b4", "combo_b5", "combo_b6", "combo_b7");

    public static Legacy soul(ResourceLocation action) {
        if (action == null) return Legacy.NONE;
        if (action.getNamespace().equals("blade_tetra") && IAIDO.contains(action.getPath())) return Legacy.KYOUKA;
        if (action.getNamespace().equals("slashblade") && RENGEKI.contains(action.getPath())) return Legacy.SENBONZAKURA;
        return Legacy.NONE;
    }

    private SoulActionAffinity() {}
}
