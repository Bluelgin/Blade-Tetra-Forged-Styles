package dev.bladetetra.customstyle;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Audited Resharped 8018222 ordinary timelines; never mutates global ComboStates. */
public final class CustomStyleTimingPolicy {
    public record Timing(int nativeTicks, int safeTicks, int effectiveTicks) {}
    // native minimum (frame-to-tick truncation), last required callback + one tick.
    // Native B rush cancellation windows are not accelerated: their native multi-hit
    // tail is optional after that window. Unadapted external actions keep their gate.
    private static final Map<String, int[]> AUDITED = Map.ofEntries(
            Map.entry("combo_a1", new int[]{3, 1}), Map.entry("combo_a2", new int[]{3, 1}),
            Map.entry("combo_a3", new int[]{6, 7}), Map.entry("combo_a4", new int[]{14, 10}),
            Map.entry("combo_a4_ex", new int[]{14, 15}), Map.entry("combo_a5ex", new int[]{22, 20}),
            Map.entry("combo_c", new int[]{10, 4}), Map.entry("upperslash", new int[]{7, 6}),
            Map.entry("rapid_slash", new int[]{13, 12}), Map.entry("rapid_slash_quick", new int[]{16, 16}),
            Map.entry("combo_b1", new int[]{8, 8}), Map.entry("combo_b2", new int[]{4, 4}),
            Map.entry("combo_b3", new int[]{4, 4}), Map.entry("combo_b4", new int[]{4, 4}),
            Map.entry("combo_b5", new int[]{4, 4}), Map.entry("combo_b6", new int[]{4, 4}),
            Map.entry("combo_b7", new int[]{22, 22}));
    public static Timing timing(ResourceLocation id, CustomBladeLength length) {
        if (!"slashblade".equals(id.getNamespace())) return null;
        int[] audited = AUDITED.get(id.getPath());
        if (audited == null) return null;
        double factor = switch (length) { case SHORT -> .75; case STANDARD -> 1; case LONG -> 1.25; };
        return new Timing(audited[0], audited[1], Math.max(audited[1], (int)Math.round(audited[0] * factor)));
    }
    private CustomStyleTimingPolicy() {}
}
