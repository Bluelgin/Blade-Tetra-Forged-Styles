package dev.bladetetra.customstyle;

import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capture is open to every SlashBlade owner, but only explicitly known
 * ordinary moves can be replayed as a free normal click. Slash Art ComboStates
 * have privileged callbacks and must never be smuggled into normal attacks.
 * Optional addons may register vetted ordinary ComboStates via this API.
 */
public final class ComboCompatibilityResolver {
    private static final Set<String> NATIVE_ORDINARY = Set.of(
            "combo_a1", "combo_a2", "combo_a3", "combo_a4", "combo_a4_ex", "combo_a5ex",
            "combo_b1", "combo_b2", "combo_b3", "combo_b4", "combo_b5",
            "combo_b6", "combo_b7", "combo_c",
            "upperslash", "rapid_slash", "rapid_slash_quick");
    private static final Set<String> BLADETETRA_ORDINARY = Set.of(
            "iaido_draw", "iaido_follow", "iaido_return", "iaido_finish",
            "dangaku_sweep", "dangaku_cleave", "dangaku_charged_sweep");
    private static final Set<ResourceLocation> ADDON_ORDINARY = ConcurrentHashMap.newKeySet();

    /**
     * Third-party addons must vet the move's clickAction, tickAction, damage,
     * prerequisites and no-cost execution before opting in.
     */
    public static void registerOrdinaryAction(ResourceLocation id) {
        if (id != null) ADDON_ORDINARY.add(id);
    }

    public static boolean playable(ResourceLocation id) {
        if (id == null || !ComboStateRegistry.REGISTRY.get().containsKey(id)) return false;
        ComboState state = ComboStateRegistry.REGISTRY.get().getValue(id);
        if (state == null || state.isAerial()) return false;
        return switch (id.getNamespace()) {
            case "slashblade" -> NATIVE_ORDINARY.contains(id.getPath());
            case "blade_tetra" -> BLADETETRA_ORDINARY.contains(id.getPath())
                    || ADDON_ORDINARY.contains(id);
            default -> ADDON_ORDINARY.contains(id);
        };
    }

    public static boolean playable(CustomStyleSpec spec) {
        return spec != null && spec.actions().stream().allMatch(ComboCompatibilityResolver::playable);
    }

    private ComboCompatibilityResolver() {}
}
