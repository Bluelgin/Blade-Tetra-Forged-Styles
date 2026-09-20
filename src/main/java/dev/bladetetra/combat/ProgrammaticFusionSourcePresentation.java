package dev.bladetetra.combat;

import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Registry-only bridge into audited source Slash Arts.
 *
 * <p>The bridge never links optional add-on classes. A source ability must first
 * be explicitly allow-listed by a loaded compatibility dictionary; this class
 * then asks SlashBlade's shared registries for that ability's own ComboState.
 * Unknown or broken registrations fail closed so the semantic executor can take
 * over instead.</p>
 */
final class ProgrammaticFusionSourcePresentation {
    static ResourceLocation resolve(ResourceLocation ability,
            SlashArts.ArtsType type, LivingEntity user) {
        if (ability == null || type == null || type == SlashArts.ArtsType.Fail
                || user == null
                || !ProgrammaticFusionProfiles.supportsSourcePresentation(ability)) {
            return null;
        }
        try {
            var artsRegistry = SlashArtsRegistry.REGISTRY.get();
            if (!artsRegistry.containsKey(ability)) {
                return null;
            }
            SlashArts art = artsRegistry.getValue(ability);
            if (art == null) {
                return null;
            }
            ResourceLocation combo = art.doArts(type, user);
            if (combo == null || ComboStateRegistry.NONE.getId().equals(combo)) {
                return null;
            }
            var comboRegistry = ComboStateRegistry.REGISTRY.get();
            return comboRegistry.containsKey(combo) && comboRegistry.getValue(combo) != null
                    ? combo : null;
        } catch (RuntimeException | LinkageError ignored) {
            // Optional add-ons are allowed to drift. Their semantic profile remains
            // the deterministic fallback if a source registry contract changes.
            return null;
        }
    }

    private ProgrammaticFusionSourcePresentation() {
    }
}
