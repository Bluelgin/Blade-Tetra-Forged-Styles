package dev.bladetetra.combat;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Registry sources that can use the standard charge and motion dispatcher. */
public final class SealedFusionSources {
    public static final ResourceLocation SOURCE_DISPATCHER = ResourceLocation.parse("blade_tetra:programmatic_fusion");
    public static final ResourceLocation DISPATCHER = ResourceLocation.parse("bt_export_runtime:programmatic_fusion");
    public static final String INTEROP = "sealed_fusion_profiles_v1";
    private static final Set<String> SOURCE_ONLY = Set.of("blade_tetra", "tetra", "bt_export_runtime");
    public static final int RELEASE_COST = 45;

    private SealedFusionSources() {}

    public static String incompatibility(ResourceLocation source) {
        if (source == null || SOURCE_ONLY.contains(source.getNamespace())) {
            return "requires the source mod or a recursive fusion dispatcher";
        }
        if (source.getNamespace().equals("recasting") && source.getPath().equals("time_beyond")) {
            return "needs Recasting's pre-release charge lifecycle";
        }
        if (source.getPath().equals("treasure_barrage")) {
            return "needs its provider's delayed replay context";
        }
        return null;
    }

    public static boolean supports(ResourceLocation source) {
        return incompatibility(source) == null;
    }
}
