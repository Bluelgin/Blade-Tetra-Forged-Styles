package dev.bladetetra.combat;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Only combinations with audited native release and motion callbacks can enter a sealed profile. */
public final class SealedFusionSources {
    public static final ResourceLocation SOURCE_DISPATCHER = ResourceLocation.parse("blade_tetra:programmatic_fusion");
    public static final ResourceLocation DISPATCHER = ResourceLocation.parse("bt_export_runtime:programmatic_fusion");
    public static final String INTEROP = "sealed_fusion_profiles_v1";
    private static final Set<String> STOCK = Set.of("drive_horizontal", "drive_vertical", "judgement_cut");
    public static final int RELEASE_COST = 45;

    private SealedFusionSources() {}

    public static boolean supports(ResourceLocation source) {
        return source != null && "slashblade".equals(source.getNamespace()) && STOCK.contains(source.getPath());
    }
}
